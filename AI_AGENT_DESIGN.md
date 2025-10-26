# AI Agent 对话系统设计文档

## 1. 概述

AI Agent 对话系统是智能服务治理平台的人机交互核心，提供自然语言对话能力，让用户通过对话方式查询告警、处理工单、分析问题、获取建议。

### 核心能力

- **多轮对话**：上下文感知的连续对话
- **意图识别**：准确理解用户意图
- **工具调用 (Tool Calling)**：调用系统功能完成任务
- **RAG 增强**：基于知识库的精准回答
- **多模态支持**：文本、图片、代码片段
- **流式响应**：实时返回生成内容

---

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    用户交互层                              │
│  Web Chat UI  │  DingTalk Bot  │  Slack Bot  │  API     │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│              对话网关 (Conversation Gateway)              │
│  - 消息路由                                               │
│  - 会话管理                                               │
│  - 流式传输                                               │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│              AI Agent 引擎 (LangChain4J)                 │
│                                                          │
│  ┌────────────┐   ┌──────────────┐   ┌───────────────┐ │
│  │ 对话管理器  │   │  意图识别器   │   │  工具编排器   │ │
│  │ChatMemory │   │IntentParser │   │ToolOrchestra │ │
│  └────────────┘   └──────────────┘   └───────────────┘ │
│                                                          │
│  ┌────────────┐   ┌──────────────┐   ┌───────────────┐ │
│  │  提示工程   │   │  RAG检索器   │   │  响应生成器   │ │
│  │PromptTempl│   │RagRetriever │   │ResponseGener │ │
│  └────────────┘   └──────────────┘   └───────────────┘ │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   工具集 (Tools)                         │
│  TicketTool  │  AlertTool  │  AnalysisTool  │  QueryTool│
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   数据层                                 │
│  MySQL  │  Redis  │  Milvus  │  Elasticsearch          │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 核心组件设计

### 3.1 对话管理器 (ConversationManager)

```java
@Service
@Slf4j
public class ConversationManager {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMemoryStore chatMemoryStore;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建新会话
     */
    public Conversation createConversation(String userId, String channel) {
        String conversationId = generateConversationId();

        Conversation conversation = Conversation.builder()
            .conversationId(conversationId)
            .userId(userId)
            .channel(channel)
            .status(ConversationStatus.ACTIVE)
            .context(new HashMap<>())
            .build();

        conversationRepository.save(conversation);

        // 初始化聊天记忆
        initChatMemory(conversationId);

        log.info("创建会话: {}, 用户: {}, 渠道: {}", conversationId, userId, channel);

        return conversation;
    }

    /**
     * 获取会话上下文
     */
    public ConversationContext getContext(String conversationId) {
        // 从 Redis 缓存获取
        String cacheKey = "conversation:context:" + conversationId;
        ConversationContext context = (ConversationContext) redisTemplate.opsForValue().get(cacheKey);

        if (context != null) {
            return context;
        }

        // 从数据库加载
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
            .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        context = ConversationContext.builder()
            .conversationId(conversationId)
            .userId(conversation.getUserId())
            .channel(conversation.getChannel())
            .contextData(conversation.getContext())
            .chatMemory(chatMemoryStore.getMessages(conversationId, 10))
            .build();

        // 缓存
        redisTemplate.opsForValue().set(cacheKey, context, Duration.ofHours(1));

        return context;
    }

    /**
     * 添加消息到会话
     */
    public void addMessage(String conversationId, ChatMessage message) {
        // 持久化到存储
        chatMemoryStore.add(conversationId, message);

        // 更新会话最后活跃时间
        updateLastActiveTime(conversationId);

        // 更新上下文（如果消息包含上下文信息）
        if (message instanceof UserMessage userMsg) {
            updateContextIfNeeded(conversationId, userMsg);
        }
    }

    /**
     * 更新会话上下文
     */
    public void updateContext(String conversationId, String key, Object value) {
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
            .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        Map<String, Object> context = conversation.getContext();
        context.put(key, value);
        conversation.setContext(context);

        conversationRepository.save(conversation);

        // 清除缓存
        redisTemplate.delete("conversation:context:" + conversationId);
    }

    /**
     * 结束会话
     */
    public void endConversation(String conversationId) {
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
            .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        conversation.setStatus(ConversationStatus.ENDED);
        conversation.setEndedAt(Instant.now());

        conversationRepository.save(conversation);

        log.info("结束会话: {}", conversationId);
    }

    private String generateConversationId() {
        return "conv_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void initChatMemory(String conversationId) {
        // 初始化空的聊天记忆
        chatMemoryStore.init(conversationId);
    }

    private void updateLastActiveTime(String conversationId) {
        conversationRepository.updateLastActiveTime(conversationId, Instant.now());
    }

    private void updateContextIfNeeded(String conversationId, UserMessage message) {
        // 从消息中提取上下文信息（如选定的工单、服务等）
        // 实现略
    }
}
```

### 3.2 AI Agent 引擎

```java
@Service
@Slf4j
public class AgentService {

    @Autowired
    private ChatLanguageModel chatModel;

    @Autowired
    private ConversationManager conversationManager;

    @Autowired
    private IntentRecognizer intentRecognizer;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private RagRetriever ragRetriever;

    @Autowired
    private PromptTemplateFactory promptTemplateFactory;

    /**
     * 处理用户消息（同步）
     */
    public String chat(String conversationId, String userMessage) {
        log.info("处理消息 - 会话: {}, 消息: {}", conversationId, userMessage);

        // 1. 获取会话上下文
        ConversationContext context = conversationManager.getContext(conversationId);

        // 2. 添加用户消息到会话历史
        UserMessage userMsg = UserMessage.from(userMessage);
        conversationManager.addMessage(conversationId, userMsg);

        // 3. 意图识别
        Intent intent = intentRecognizer.recognize(userMessage, context);
        log.info("识别意图: {}", intent.getType());

        // 4. RAG 检索（如果需要）
        List<String> retrievedDocs = null;
        if (intent.needsRetrieval()) {
            retrievedDocs = ragRetriever.retrieve(userMessage, intent, 5);
        }

        // 5. 构建提示
        String prompt = buildPrompt(userMessage, context, intent, retrievedDocs);

        // 6. 工具调用
        List<ToolSpecification> tools = toolRegistry.getToolsForIntent(intent);

        // 7. 调用 LLM（带工具）
        Response<AiMessage> response;
        if (tools.isEmpty()) {
            response = chatModel.generate(context.getChatMemory(), userMsg);
        } else {
            response = chatModel.generate(context.getChatMemory(), userMsg, tools);
        }

        // 8. 处理工具调用
        AiMessage aiMessage = response.content();
        if (aiMessage.hasToolExecutionRequests()) {
            aiMessage = handleToolCalls(aiMessage, context);
        }

        // 9. 添加 AI 响应到会话历史
        conversationManager.addMessage(conversationId, aiMessage);

        String responseText = aiMessage.text();
        log.info("AI 响应: {}", responseText);

        return responseText;
    }

    /**
     * 流式对话
     */
    public Flux<String> chatStream(String conversationId, String userMessage) {
        log.info("流式处理消息 - 会话: {}, 消息: {}", conversationId, userMessage);

        return Flux.create(sink -> {
            try {
                // 1. 获取会话上下文
                ConversationContext context = conversationManager.getContext(conversationId);

                // 2. 添加用户消息
                UserMessage userMsg = UserMessage.from(userMessage);
                conversationManager.addMessage(conversationId, userMsg);

                // 3. 意图识别和 RAG 检索（同上）
                Intent intent = intentRecognizer.recognize(userMessage, context);
                List<String> retrievedDocs = null;
                if (intent.needsRetrieval()) {
                    retrievedDocs = ragRetriever.retrieve(userMessage, intent, 5);
                }

                // 4. 构建提示
                String prompt = buildPrompt(userMessage, context, intent, retrievedDocs);

                // 5. 流式生成
                StringBuilder fullResponse = new StringBuilder();

                StreamingChatLanguageModel streamingModel = getStreamingModel();
                streamingModel.generate(
                    context.getChatMemory(),
                    userMsg,
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            fullResponse.append(token);
                            sink.next(token);
                        }

                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            // 保存完整响应
                            conversationManager.addMessage(conversationId, response.content());
                            sink.complete();
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("流式生成错误", error);
                            sink.error(error);
                        }
                    }
                );

            } catch (Exception e) {
                log.error("流式对话异常", e);
                sink.error(e);
            }
        });
    }

    /**
     * 处理工具调用
     */
    private AiMessage handleToolCalls(AiMessage aiMessage, ConversationContext context) {
        List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
        List<ToolExecutionResult> toolResults = new ArrayList<>();

        for (ToolExecutionRequest request : toolRequests) {
            log.info("执行工具: {}, 参数: {}", request.name(), request.arguments());

            try {
                // 获取工具
                Tool tool = toolRegistry.getTool(request.name());

                // 执行工具
                String result = tool.execute(request.arguments(), context);

                toolResults.add(ToolExecutionResult.builder()
                    .id(request.id())
                    .toolName(request.name())
                    .result(result)
                    .build());

            } catch (Exception e) {
                log.error("工具执行失败: {}", request.name(), e);
                toolResults.add(ToolExecutionResult.builder()
                    .id(request.id())
                    .toolName(request.name())
                    .result("工具执行失败: " + e.getMessage())
                    .build());
            }
        }

        // 将工具执行结果反馈给 LLM
        ToolExecutionResultMessage toolResultMessage = ToolExecutionResultMessage.from(toolResults);
        conversationManager.addMessage(context.getConversationId(), toolResultMessage);

        // 让 LLM 根据工具结果生成最终响应
        Response<AiMessage> finalResponse = chatModel.generate(
            context.getChatMemory(),
            toolResultMessage
        );

        return finalResponse.content();
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(String userMessage, ConversationContext context,
                               Intent intent, List<String> retrievedDocs) {
        PromptTemplate template = promptTemplateFactory.getTemplate(intent.getType());

        Map<String, Object> variables = new HashMap<>();
        variables.put("user_message", userMessage);
        variables.put("context", context.getContextData());
        variables.put("intent", intent);

        if (retrievedDocs != null && !retrievedDocs.isEmpty()) {
            variables.put("retrieved_docs", String.join("\n\n", retrievedDocs));
        }

        return template.apply(variables).text();
    }

    private StreamingChatLanguageModel getStreamingModel() {
        // 返回支持流式输出的模型
        return OpenAiStreamingChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("deepseek-ai/DeepSeek-V3")
            .build();
    }
}
```

### 3.3 意图识别器

```java
@Service
public class IntentRecognizer {

    @Autowired
    private ChatLanguageModel intentClassifier;

    /**
     * 识别用户意图
     */
    public Intent recognize(String userMessage, ConversationContext context) {
        // 使用小型快速模型进行意图分类
        String intentPrompt = buildIntentPrompt(userMessage, context);

        String intentJson = intentClassifier.generate(intentPrompt);

        return parseIntent(intentJson);
    }

    private String buildIntentPrompt(String userMessage, ConversationContext context) {
        return String.format("""
            分析用户消息的意图，返回 JSON 格式。

            用户消息：%s

            会话上下文：%s

            可选意图类型：
            - QUERY_TICKET: 查询工单
            - CREATE_TICKET: 创建工单
            - UPDATE_TICKET: 更新工单
            - QUERY_ALERT: 查询告警
            - ANALYZE_ISSUE: 分析问题
            - ASK_SOLUTION: 询问解决方案
            - GENERAL_CHAT: 普通聊天
            - STATISTICS: 统计分析

            返回格式：
            {
              "type": "意图类型",
              "confidence": 0.95,
              "entities": {
                "ticket_no": "工单编号",
                "service_name": "服务名",
                "time_range": "时间范围"
              },
              "needs_retrieval": true
            }
            """,
            userMessage,
            context.getContextData()
        );
    }

    private Intent parseIntent(String intentJson) {
        // 解析 JSON 为 Intent 对象
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(intentJson, Intent.class);
        } catch (JsonProcessingException e) {
            // 如果解析失败，返回默认意图
            return Intent.builder()
                .type(IntentType.GENERAL_CHAT)
                .confidence(0.5)
                .needsRetrieval(false)
                .build();
        }
    }
}

@Data
@Builder
public class Intent {
    private IntentType type;
    private double confidence;
    private Map<String, Object> entities;
    private boolean needsRetrieval;
}

public enum IntentType {
    QUERY_TICKET("查询工单"),
    CREATE_TICKET("创建工单"),
    UPDATE_TICKET("更新工单"),
    QUERY_ALERT("查询告警"),
    ANALYZE_ISSUE("分析问题"),
    ASK_SOLUTION("询问解决方案"),
    GENERAL_CHAT("普通聊天"),
    STATISTICS("统计分析");

    private final String label;
}
```

### 3.4 工具定义和注册

```java
/**
 * 工具接口
 */
public interface Tool {
    String name();
    String description();
    ToolSpecification specification();
    String execute(String arguments, ConversationContext context);
}

/**
 * 工单查询工具
 */
@Component
public class TicketQueryTool implements Tool {

    @Autowired
    private TicketQueryService ticketQueryService;

    @Override
    public String name() {
        return "query_ticket";
    }

    @Override
    public String description() {
        return "查询工单信息，支持按工单编号、服务名、状态、处理人等条件查询";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .addParameter("ticket_no", JsonSchemaProperty.STRING,
                JsonSchemaProperty.description("工单编号"))
            .addParameter("service_name", JsonSchemaProperty.STRING,
                JsonSchemaProperty.description("服务名称"))
            .addParameter("status", JsonSchemaProperty.STRING,
                JsonSchemaProperty.description("工单状态"))
            .addParameter("assignee", JsonSchemaProperty.STRING,
                JsonSchemaProperty.description("处理人"))
            .build();
    }

    @Override
    public String execute(String arguments, ConversationContext context) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            TicketQueryRequest request = mapper.readValue(arguments, TicketQueryRequest.class);

            List<Ticket> tickets = ticketQueryService.query(request, PageRequest.of(0, 10))
                .getContent();

            if (tickets.isEmpty()) {
                return "未找到符合条件的工单";
            }

            // 格式化结果
            StringBuilder result = new StringBuilder();
            result.append(String.format("找到 %d 个工单：\n\n", tickets.size()));

            for (Ticket ticket : tickets) {
                result.append(formatTicket(ticket)).append("\n\n");
            }

            return result.toString();

        } catch (Exception e) {
            return "查询工单失败: " + e.getMessage();
        }
    }

    private String formatTicket(Ticket ticket) {
        return String.format("""
            工单编号：%s
            服务：%s
            状态：%s
            严重级别：%s
            处理人：%s
            创建时间：%s
            """,
            ticket.getTicketNo(),
            ticket.getServiceName(),
            ticket.getStatus(),
            ticket.getSeverity(),
            ticket.getAssignee(),
            ticket.getCreatedAt()
        );
    }
}

/**
 * 问题分析工具
 */
@Component
public class IssueAnalysisTool implements Tool {

    @Autowired
    private StackAnalysisService stackAnalysisService;

    @Override
    public String name() {
        return "analyze_issue";
    }

    @Override
    public String description() {
        return "分析异常堆栈，提供根因分析和解决方案建议";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .addParameter("stack_trace", JsonSchemaProperty.STRING,
                JsonSchemaProperty.description("异常堆栈信息"))
            .addParameter("service_name", JsonSchemaProperty.STRING,
                JsonSchemaProperty.description("服务名称"))
            .build();
    }

    @Override
    public String execute(String arguments, ConversationContext context) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode args = mapper.readTree(arguments);

            String stackTrace = args.get("stack_trace").asText();
            String serviceName = args.has("service_name") ? args.get("service_name").asText() : null;

            // 调用堆栈分析服务
            StackAnalysisResult analysis = stackAnalysisService.analyze(stackTrace);

            return formatAnalysisResult(analysis);

        } catch (Exception e) {
            return "问题分析失败: " + e.getMessage();
        }
    }

    private String formatAnalysisResult(StackAnalysisResult result) {
        return String.format("""
            ## 根因分析

            %s

            ## 影响范围

            %s

            ## 建议解决方案

            %s

            ## 相关代码

            %s
            """,
            result.getRootCause(),
            result.getImpactScope(),
            result.getSuggestedSolution(),
            result.getRelevantCode()
        );
    }
}

/**
 * 工具注册中心
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    @Autowired
    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            tools.put(tool.name(), tool);
        }
    }

    public Tool getTool(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new ToolNotFoundException("工具不存在: " + name);
        }
        return tool;
    }

    public List<ToolSpecification> getToolsForIntent(Intent intent) {
        return switch (intent.getType()) {
            case QUERY_TICKET, UPDATE_TICKET -> List.of(
                getTool("query_ticket").specification(),
                getTool("update_ticket").specification()
            );
            case ANALYZE_ISSUE -> List.of(
                getTool("analyze_issue").specification(),
                getTool("query_similar_cases").specification()
            );
            case QUERY_ALERT -> List.of(
                getTool("query_alerts").specification()
            );
            case STATISTICS -> List.of(
                getTool("get_statistics").specification()
            );
            default -> Collections.emptyList();
        };
    }

    public List<ToolSpecification> getAllTools() {
        return tools.values().stream()
            .map(Tool::specification)
            .toList();
    }
}
```

---

## 4. RAG 检索器

```java
@Service
public class RagRetriever {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private KnowledgeRepository knowledgeRepository;

    /**
     * 检索相关文档
     */
    public List<String> retrieve(String query, Intent intent, int topK) {
        // 1. 生成查询向量
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. 向量检索
        List<EmbeddingMatch<TextSegment>> matches = vectorStoreService.findRelevant(
            queryEmbedding,
            topK,
            getCollectionName(intent)
        );

        // 3. 提取文档内容
        List<String> documents = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            if (match.score() > 0.7) {  // 相似度阈值
                documents.add(match.embedded().text());
            }
        }

        // 4. 混合检索：关键词检索补充
        if (documents.size() < topK) {
            List<String> keywordResults = keywordSearch(query, intent, topK - documents.size());
            documents.addAll(keywordResults);
        }

        return documents;
    }

    /**
     * 根据意图选择检索集合
     */
    private String getCollectionName(Intent intent) {
        return switch (intent.getType()) {
            case ASK_SOLUTION -> "solutions";
            case ANALYZE_ISSUE -> "code_index";
            case QUERY_TICKET -> "tickets";
            default -> "knowledge_base";
        };
    }

    /**
     * 关键词检索（补充）
     */
    private List<String> keywordSearch(String query, Intent intent, int limit) {
        // 使用 Elasticsearch 进行关键词检索
        // 实现略
        return Collections.emptyList();
    }
}
```

---

## 5. 提示词模板工厂

```java
@Component
public class PromptTemplateFactory {

    private final Map<IntentType, PromptTemplate> templates = new EnumMap<>(IntentType.class);

    @PostConstruct
    public void init() {
        // 查询工单模板
        templates.put(IntentType.QUERY_TICKET, PromptTemplate.from("""
            你是一个服务治理助手，帮助用户查询和管理工单。

            用户消息：{{user_message}}

            会话上下文：{{context}}

            可用工具：query_ticket, update_ticket

            请根据用户需求调用相应的工具，并以清晰友好的方式返回结果。
            """
        ));

        // 问题分析模板
        templates.put(IntentType.ANALYZE_ISSUE, PromptTemplate.from("""
            你是一个资深的 Java 工程师，擅长分析异常和解决问题。

            用户消息：{{user_message}}

            相关代码上下文：
            {{retrieved_docs}}

            请分析以下内容：
            1. 根本原因是什么？
            2. 为什么会发生？
            3. 如何修复？
            4. 如何预防？

            请给出详细、专业的分析。
            """
        ));

        // 解决方案咨询模板
        templates.put(IntentType.ASK_SOLUTION, PromptTemplate.from("""
            你是一个服务治理助手，帮助用户解决问题。

            用户消息：{{user_message}}

            相似历史案例：
            {{retrieved_docs}}

            请参考历史案例，给出针对性的解决建议。
            如果历史案例中有成功的解决方案，优先推荐。
            """
        ));

        // 普通聊天模板
        templates.put(IntentType.GENERAL_CHAT, PromptTemplate.from("""
            你是一个友好的服务治理助手。

            用户消息：{{user_message}}

            会话上下文：{{context}}

            请友好、专业地回答用户的问题。
            """
        ));

        // 统计分析模板
        templates.put(IntentType.STATISTICS, PromptTemplate.from("""
            你是一个数据分析助手，帮助用户理解统计数据。

            用户消息：{{user_message}}

            可用工具：get_statistics

            请调用统计工具获取数据，并以清晰的方式展示和解释结果。
            如果发现异常趋势，请主动指出。
            """
        ));
    }

    public PromptTemplate getTemplate(IntentType intentType) {
        return templates.getOrDefault(intentType, getDefaultTemplate());
    }

    private PromptTemplate getDefaultTemplate() {
        return PromptTemplate.from("""
            你是一个服务治理助手。

            用户消息：{{user_message}}

            请根据用户需求提供帮助。
            """
        );
    }
}
```

---

## 6. 聊天记忆存储

```java
@Service
public class ChatMemoryStore {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ConversationMessageRepository messageRepository;

    private static final int MAX_MEMORY_MESSAGES = 20;

    /**
     * 初始化会话记忆
     */
    public void init(String conversationId) {
        String key = getMemoryKey(conversationId);
        redisTemplate.delete(key);
    }

    /**
     * 添加消息
     */
    public void add(String conversationId, ChatMessage message) {
        // 1. 存储到 Redis（短期记忆）
        String key = getMemoryKey(conversationId);
        redisTemplate.opsForList().rightPush(key, message);
        redisTemplate.expire(key, Duration.ofHours(24));

        // 限制记忆长度
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > MAX_MEMORY_MESSAGES) {
            redisTemplate.opsForList().trim(key, size - MAX_MEMORY_MESSAGES, -1);
        }

        // 2. 持久化到数据库（长期记忆）
        ConversationMessage persistMessage = ConversationMessage.builder()
            .conversationId(conversationId)
            .role(message.type().toString())
            .content(extractContent(message))
            .build();

        messageRepository.save(persistMessage);
    }

    /**
     * 获取最近 N 条消息
     */
    public List<ChatMessage> getMessages(String conversationId, int limit) {
        String key = getMemoryKey(conversationId);

        // 从 Redis 获取
        List<Object> objects = redisTemplate.opsForList().range(key, -limit, -1);

        if (objects != null && !objects.isEmpty()) {
            return objects.stream()
                .map(obj -> (ChatMessage) obj)
                .toList();
        }

        // Redis 中没有，从数据库加载
        List<ConversationMessage> messages = messageRepository
            .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, limit))
            .getContent();

        Collections.reverse(messages);

        return messages.stream()
            .map(this::toChatMessage)
            .toList();
    }

    /**
     * 清除会话记忆
     */
    public void clear(String conversationId) {
        String key = getMemoryKey(conversationId);
        redisTemplate.delete(key);
    }

    private String getMemoryKey(String conversationId) {
        return "conversation:memory:" + conversationId;
    }

    private String extractContent(ChatMessage message) {
        if (message instanceof UserMessage userMsg) {
            return userMsg.singleText();
        } else if (message instanceof AiMessage aiMsg) {
            return aiMsg.text();
        } else if (message instanceof SystemMessage sysMsg) {
            return sysMsg.text();
        }
        return message.toString();
    }

    private ChatMessage toChatMessage(ConversationMessage message) {
        return switch (message.getRole()) {
            case "USER" -> UserMessage.from(message.getContent());
            case "AI" -> AiMessage.from(message.getContent());
            case "SYSTEM" -> SystemMessage.from(message.getContent());
            default -> throw new IllegalArgumentException("Unknown role: " + message.getRole());
        };
    }
}
```

---

## 7. API 设计

### 7.1 对话 API

```java
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private ConversationManager conversationManager;

    /**
     * 创建会话
     */
    @PostMapping("/conversations")
    public ConversationVO createConversation(@RequestBody CreateConversationRequest request) {
        Conversation conversation = conversationManager.createConversation(
            request.getUserId(),
            request.getChannel()
        );

        return toVO(conversation);
    }

    /**
     * 发送消息（同步）
     */
    @PostMapping("/conversations/{conversationId}/messages")
    public ChatResponse chat(@PathVariable String conversationId,
                             @RequestBody ChatRequest request) {
        String response = agentService.chat(conversationId, request.getMessage());

        return ChatResponse.builder()
            .conversationId(conversationId)
            .message(response)
            .timestamp(Instant.now())
            .build();
    }

    /**
     * 发送消息（流式）
     */
    @GetMapping(value = "/conversations/{conversationId}/messages/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@PathVariable String conversationId,
                                                     @RequestParam String message) {
        return agentService.chatStream(conversationId, message)
            .map(token -> ServerSentEvent.<String>builder()
                .data(token)
                .build());
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessageVO> getMessages(@PathVariable String conversationId,
                                           @RequestParam(defaultValue = "20") int limit) {
        return conversationManager.getContext(conversationId)
            .getChatMemory()
            .stream()
            .map(this::toMessageVO)
            .toList();
    }

    /**
     * 结束会话
     */
    @PostMapping("/conversations/{conversationId}/end")
    public void endConversation(@PathVariable String conversationId) {
        conversationManager.endConversation(conversationId);
    }
}
```

### 7.2 WebSocket 支持

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private AgentWebSocketHandler agentWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent")
            .setAllowedOrigins("*");
    }
}

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private AgentService agentService;

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String conversationId = extractConversationId(session);
        sessions.put(conversationId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String conversationId = extractConversationId(session);
        String userMessage = message.getPayload();

        // 流式返回
        agentService.chatStream(conversationId, userMessage)
            .subscribe(
                token -> {
                    try {
                        session.sendMessage(new TextMessage(token));
                    } catch (IOException e) {
                        // 处理异常
                    }
                },
                error -> {
                    // 错误处理
                },
                () -> {
                    // 完成
                    try {
                        session.sendMessage(new TextMessage("[DONE]"));
                    } catch (IOException e) {
                        // 处理异常
                    }
                }
            );
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String conversationId = extractConversationId(session);
        sessions.remove(conversationId);
    }

    private String extractConversationId(WebSocketSession session) {
        // 从 session 中提取 conversationId
        return session.getUri().getQuery().split("=")[1];
    }
}
```

---

## 8. 集成钉钉机器人

```java
@Service
public class DingTalkBotService {

    @Autowired
    private AgentService agentService;

    @Autowired
    private ConversationManager conversationManager;

    /**
     * 处理钉钉回调
     */
    public DingTalkResponse handleCallback(DingTalkRequest request) {
        String userId = request.getSenderId();
        String message = request.getText().getContent();

        // 获取或创建会话
        String conversationId = getOrCreateConversation(userId, "dingtalk");

        // 处理消息
        String response = agentService.chat(conversationId, message);

        // 返回钉钉格式响应
        return DingTalkResponse.builder()
            .msgtype("text")
            .text(Map.of("content", response))
            .build();
    }

    private String getOrCreateConversation(String userId, String channel) {
        // 从缓存或数据库查找最近的会话
        String conversationId = findRecentConversation(userId, channel);

        if (conversationId == null) {
            Conversation conversation = conversationManager.createConversation(userId, channel);
            conversationId = conversation.getConversationId();
        }

        return conversationId;
    }

    private String findRecentConversation(String userId, String channel) {
        // 查找最近 1 小时内的会话
        // 实现略
        return null;
    }
}

@RestController
@RequestMapping("/api/dingtalk")
public class DingTalkBotController {

    @Autowired
    private DingTalkBotService botService;

    @PostMapping("/callback")
    public DingTalkResponse callback(@RequestBody DingTalkRequest request) {
        return botService.handleCallback(request);
    }
}
```

---

## 9. 配置示例

```yaml
# application.yml
one-agent:
  agent:
    # LLM 配置
    llm:
      provider: openai
      model: deepseek-ai/DeepSeek-V3
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.siliconflow.cn
      temperature: 0.7
      max-tokens: 2000

    # 意图识别配置
    intent:
      enabled: true
      model: deepseek-ai/DeepSeek-R1-Distill-Qwen-7B  # 小模型
      confidence-threshold: 0.7

    # RAG 配置
    rag:
      enabled: true
      top-k: 5
      similarity-threshold: 0.7
      embedding-model: BAAI/bge-large-zh-v1.5

    # 聊天记忆配置
    memory:
      max-messages: 20
      ttl: 24h

    # 工具配置
    tools:
      enabled:
        - query_ticket
        - update_ticket
        - analyze_issue
        - query_alerts
        - get_statistics
        - query_similar_cases

    # 流式响应配置
    streaming:
      enabled: true
      chunk-size: 50
```

---

## 10. 测试示例

### 对话示例 1：查询工单

```
User: 帮我查一下服务 user-service 最近的工单

AI: 好的，我来帮你查询 user-service 的最近工单。
    [调用工具: query_ticket]

    找到 3 个工单：

    1. 工单编号：TICK-20250124-001
       状态：处理中
       严重级别：P1
       问题：NullPointerException
       处理人：张三

    2. 工单编号：TICK-20250123-089
       状态：已解决
       严重级别：P2
       问题：TimeoutException
       处理人：李四

    3. 工单编号：TICK-20250123-076
       状态：已关闭
       严重级别：P3
       问题：SQLException
       处理人：王五

    需要我帮你查看某个工单的详细信息吗？
```

### 对话示例 2：分析问题

```
User: 这个异常是什么原因导致的？
[附带堆栈信息]

AI: 让我来分析这个异常。
    [调用工具: analyze_issue]

    ## 根因分析

    这是一个 NullPointerException，发生在 UserService.getUserInfo() 方法的第 45 行。
    根据堆栈和代码上下文，原因是：

    userRepository.findById() 返回了 null，但代码没有做空值检查就直接调用了 .getUsername()。

    ## 建议解决方案

    1. 添加空值检查：
       ```java
       User user = userRepository.findById(userId);
       if (user == null) {
           throw new UserNotFoundException("用户不存在: " + userId);
       }
       return user.getUsername();
       ```

    2. 使用 Optional：
       ```java
       return userRepository.findById(userId)
           .map(User::getUsername)
           .orElseThrow(() -> new UserNotFoundException("用户不存在: " + userId));
       ```

    ## 预防措施

    建议在项目中启用 SpotBugs 或 SonarQube 静态代码检查，可以自动发现这类潜在的空指针问题。
```

---

## 11. 总结

### 核心特性

1. **多轮对话**：基于 LangChain4J 的上下文感知对话
2. **工具调用**：Function Calling 实现系统功能集成
3. **RAG 增强**：知识库检索提供精准答案
4. **流式响应**：实时返回，提升用户体验
5. **意图识别**：快速理解用户需求
6. **多渠道支持**：Web、钉钉、Slack 等
7. **会话管理**：完整的会话生命周期管理

### 预期效果

- **响应速度 < 2s**：意图识别 + RAG 检索 + LLM 生成
- **问题解决率 > 80%**：基于 RAG 和工具调用
- **用户满意度 > 90%**：友好的对话体验
- **系统集成度 100%**：所有功能可通过对话完成
