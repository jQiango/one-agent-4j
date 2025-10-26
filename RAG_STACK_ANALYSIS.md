# 基于 RAG 的堆栈分析技术方案

## 1. 方案概述

利用 RAG (Retrieval-Augmented Generation) 技术，将项目源码、历史告警、运维文档等作为知识库，当收到异常告警时，结合堆栈信息和相关代码上下文，使用 AI 模型进行深度分析。

### 1.1 核心流程

```
告警信息 → 堆栈解析 → 代码检索 → 上下文增强 → AI 分析 → 分析报告
   ↓           ↓           ↓            ↓           ↓          ↓
原始告警   提取关键信息   RAG 检索    构建 Prompt   LLM 推理   结构化输出
```

---

## 2. 技术架构设计

### 2.1 整体架构

```
┌────────────────────────────────────────────────────────────────┐
│                         告警输入层                               │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐              │
│  │ 异常堆栈    │  │ 错误日志    │  │ 监控指标    │              │
│  └──────┬─────┘  └──────┬─────┘  └──────┬─────┘              │
└─────────┼────────────────┼────────────────┼────────────────────┘
          │                │                │
          └────────────────┴────────────────┘
                           ↓
┌────────────────────────────────────────────────────────────────┐
│                      堆栈解析层                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  StackTraceParser                                        │ │
│  │  - 提取异常类型                                           │ │
│  │  - 解析调用栈                                             │ │
│  │  - 识别关键类和方法                                        │ │
│  │  - 提取错误行号                                           │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────┬──────────────────────────────────────┘
                          ↓
┌────────────────────────────────────────────────────────────────┐
│                      RAG 检索层                                  │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐     │
│  │  源码检索      │  │  历史告警检索  │  │  文档检索      │     │
│  │ CodeRetriever │  │ AlertRetriever│  │ DocRetriever  │     │
│  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘     │
│          │                  │                  │              │
│          └──────────────────┴──────────────────┘              │
│                          Embedding                            │
│                             ↓                                 │
│                    Vector Database (Milvus)                   │
└─────────────────────────┬──────────────────────────────────────┘
                          ↓
┌────────────────────────────────────────────────────────────────┐
│                    上下文构建层                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ContextBuilder                                          │ │
│  │  - 源码上下文（错误行前后代码）                            │ │
│  │  - 依赖关系上下文（调用链）                                │ │
│  │  - 历史上下文（相似问题解决方案）                           │ │
│  │  - 配置上下文（相关配置文件）                              │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────┬──────────────────────────────────────┘
                          ↓
┌────────────────────────────────────────────────────────────────┐
│                      AI 分析层                                   │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  LangChain4J Agent                                       │ │
│  │  - Prompt Engineering                                    │ │
│  │  - Chain of Thought                                      │ │
│  │  - Multi-step Reasoning                                  │ │
│  └──────────────────────────────────────────────────────────┘ │
│                          ↓                                     │
│                    LLM (DeepSeek-V3)                          │
└─────────────────────────┬──────────────────────────────────────┘
                          ↓
┌────────────────────────────────────────────────────────────────┐
│                    输出层                                        │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  AnalysisReport                                          │ │
│  │  - 问题根因                                               │ │
│  │  - 涉及代码位置                                           │ │
│  │  - 影响范围                                               │ │
│  │  - 修复建议                                               │ │
│  │  - 相关历史案例                                           │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

---

## 3. 核心模块实现

### 3.1 堆栈解析模块

#### 功能职责
解析 Java 异常堆栈，提取关键信息用于后续检索。

#### 数据结构

```java
@Data
public class StackTraceInfo {
    // 异常信息
    private String exceptionType;        // 异常类型: NullPointerException
    private String exceptionMessage;     // 异常消息

    // 调用栈信息
    private List<StackFrame> frames;     // 调用栈帧
    private StackFrame errorFrame;       // 出错栈帧

    // 关键信息
    private String errorClass;           // 出错类名
    private String errorMethod;          // 出错方法名
    private Integer errorLine;           // 出错行号
    private String errorFile;            // 出错文件

    // 提取的关键词（用于检索）
    private List<String> keywords;
}

@Data
public class StackFrame {
    private String className;            // 类名
    private String methodName;           // 方法名
    private String fileName;             // 文件名
    private Integer lineNumber;          // 行号
    private boolean isProjectCode;       // 是否项目代码
}
```

#### 解析器实现

```java
@Service
public class StackTraceParser {

    private static final Pattern STACK_FRAME_PATTERN =
        Pattern.compile("at\\s+([\\w.$]+)\\.([\\w<>]+)\\(([\\w.]+):(\\d+)\\)");

    /**
     * 解析堆栈信息
     */
    public StackTraceInfo parse(String stackTrace) {
        StackTraceInfo info = new StackTraceInfo();

        String[] lines = stackTrace.split("\n");

        // 1. 解析异常类型和消息
        if (lines.length > 0) {
            parseExceptionLine(lines[0], info);
        }

        // 2. 解析调用栈
        List<StackFrame> frames = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            StackFrame frame = parseStackFrame(lines[i]);
            if (frame != null) {
                frames.add(frame);

                // 第一个项目代码栈帧作为错误栈帧
                if (frame.isProjectCode() && info.getErrorFrame() == null) {
                    info.setErrorFrame(frame);
                    info.setErrorClass(frame.getClassName());
                    info.setErrorMethod(frame.getMethodName());
                    info.setErrorFile(frame.getFileName());
                    info.setErrorLine(frame.getLineNumber());
                }
            }
        }
        info.setFrames(frames);

        // 3. 提取关键词
        info.setKeywords(extractKeywords(info));

        return info;
    }

    /**
     * 解析异常类型和消息
     */
    private void parseExceptionLine(String line, StackTraceInfo info) {
        // java.lang.NullPointerException: Cannot invoke "User.getName()" because "user" is null
        int colonIndex = line.indexOf(':');
        if (colonIndex > 0) {
            info.setExceptionType(line.substring(0, colonIndex).trim());
            info.setExceptionMessage(line.substring(colonIndex + 1).trim());
        } else {
            info.setExceptionType(line.trim());
        }
    }

    /**
     * 解析单个栈帧
     */
    private StackFrame parseStackFrame(String line) {
        Matcher matcher = STACK_FRAME_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        StackFrame frame = new StackFrame();
        frame.setClassName(matcher.group(1));
        frame.setMethodName(matcher.group(2));
        frame.setFileName(matcher.group(3));
        frame.setLineNumber(Integer.parseInt(matcher.group(4)));

        // 判断是否是项目代码（根据包名）
        frame.setProjectCode(isProjectPackage(frame.getClassName()));

        return frame;
    }

    /**
     * 判断是否是项目包
     */
    private boolean isProjectPackage(String className) {
        // 可配置项目包前缀
        return className.startsWith("com.all.in.one")
            || className.startsWith("com.yourcompany");
    }

    /**
     * 提取关键词用于检索
     */
    private List<String> extractKeywords(StackTraceInfo info) {
        Set<String> keywords = new HashSet<>();

        // 异常类型
        keywords.add(info.getExceptionType());

        // 错误类名和方法名
        if (info.getErrorClass() != null) {
            keywords.add(info.getErrorClass());
            keywords.add(getSimpleClassName(info.getErrorClass()));
        }
        if (info.getErrorMethod() != null) {
            keywords.add(info.getErrorMethod());
        }

        // 从异常消息中提取关键词
        if (info.getExceptionMessage() != null) {
            keywords.addAll(extractFromMessage(info.getExceptionMessage()));
        }

        return new ArrayList<>(keywords);
    }

    private String getSimpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    private List<String> extractFromMessage(String message) {
        // 提取消息中的类名、方法名、变量名等
        List<String> keywords = new ArrayList<>();

        // 匹配引号中的内容
        Pattern pattern = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            keywords.add(matcher.group(1));
        }

        return keywords;
    }
}
```

---

### 3.2 源码索引模块

#### 功能职责
将项目源码索引到向量数据库，支持语义检索。

#### 代码块分割策略

```java
@Service
public class CodeIndexService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    /**
     * 索引项目源码
     */
    public void indexProjectCode(String projectPath) {
        List<CodeBlock> codeBlocks = scanAndSplitCode(projectPath);

        for (CodeBlock block : codeBlocks) {
            // 生成代码块的 Embedding
            Embedding embedding = embeddingModel.embed(block.toIndexText()).content();

            // 存储到向量数据库
            vectorStore.add(block.getId(), embedding, block);
        }
    }

    /**
     * 扫描并分割代码
     */
    private List<CodeBlock> scanAndSplitCode(String projectPath) {
        List<CodeBlock> blocks = new ArrayList<>();

        // 递归扫描 Java 文件
        Files.walk(Paths.get(projectPath))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    String content = Files.readString(path);
                    blocks.addAll(splitCodeFile(path, content));
                } catch (IOException e) {
                    log.error("Failed to read file: {}", path, e);
                }
            });

        return blocks;
    }

    /**
     * 分割代码文件为多个代码块
     */
    private List<CodeBlock> splitCodeFile(Path filePath, String content) {
        List<CodeBlock> blocks = new ArrayList<>();

        // 使用 JavaParser 解析代码
        CompilationUnit cu = StaticJavaParser.parse(content);

        // 提取类信息
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            // 提取类级别的代码块
            CodeBlock classBlock = CodeBlock.builder()
                .id(UUID.randomUUID().toString())
                .filePath(filePath.toString())
                .type(CodeBlockType.CLASS)
                .className(classDecl.getNameAsString())
                .packageName(cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString()).orElse(""))
                .content(classDecl.toString())
                .startLine(classDecl.getBegin().map(pos -> pos.line).orElse(0))
                .endLine(classDecl.getEnd().map(pos -> pos.line).orElse(0))
                .build();
            blocks.add(classBlock);

            // 提取方法级别的代码块
            classDecl.getMethods().forEach(method -> {
                CodeBlock methodBlock = CodeBlock.builder()
                    .id(UUID.randomUUID().toString())
                    .filePath(filePath.toString())
                    .type(CodeBlockType.METHOD)
                    .className(classDecl.getNameAsString())
                    .methodName(method.getNameAsString())
                    .packageName(cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString()).orElse(""))
                    .content(method.toString())
                    .startLine(method.getBegin().map(pos -> pos.line).orElse(0))
                    .endLine(method.getEnd().map(pos -> pos.line).orElse(0))
                    .build();
                blocks.add(methodBlock);
            });
        });

        return blocks;
    }
}

@Data
@Builder
public class CodeBlock {
    private String id;
    private String filePath;           // 文件路径
    private CodeBlockType type;        // 代码块类型
    private String packageName;        // 包名
    private String className;          // 类名
    private String methodName;         // 方法名（如果是方法）
    private String content;            // 代码内容
    private Integer startLine;         // 起始行
    private Integer endLine;           // 结束行
    private Map<String, Object> metadata; // 元数据

    /**
     * 转换为索引文本（用于生成 Embedding）
     */
    public String toIndexText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Package: ").append(packageName).append("\n");
        sb.append("Class: ").append(className).append("\n");
        if (methodName != null) {
            sb.append("Method: ").append(methodName).append("\n");
        }
        sb.append("Content:\n").append(content);
        return sb.toString();
    }
}

public enum CodeBlockType {
    CLASS,      // 类
    METHOD,     // 方法
    FIELD,      // 字段
    INTERFACE   // 接口
}
```

---

### 3.3 RAG 检索模块

#### 功能职责
根据堆栈信息检索相关代码、历史告警和文档。

#### 多路检索策略

```java
@Service
public class RagRetrievalService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AlertRepository alertRepository;

    /**
     * 综合检索
     */
    public RetrievalContext retrieve(StackTraceInfo stackInfo) {
        RetrievalContext context = new RetrievalContext();

        // 1. 检索相关源码
        context.setCodeBlocks(retrieveRelevantCode(stackInfo));

        // 2. 检索历史告警
        context.setHistoricalAlerts(retrieveSimilarAlerts(stackInfo));

        // 3. 检索运维文档
        context.setDocuments(retrieveRelevantDocs(stackInfo));

        return context;
    }

    /**
     * 检索相关源码
     */
    private List<CodeBlock> retrieveRelevantCode(StackTraceInfo stackInfo) {
        List<CodeBlock> results = new ArrayList<>();

        // 策略 1: 精确匹配（根据文件名和行号）
        if (stackInfo.getErrorFile() != null) {
            CodeBlock exactMatch = findExactCode(
                stackInfo.getErrorClass(),
                stackInfo.getErrorMethod(),
                stackInfo.getErrorLine()
            );
            if (exactMatch != null) {
                results.add(exactMatch);
            }
        }

        // 策略 2: 语义检索（根据异常信息）
        String query = buildCodeQuery(stackInfo);
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        List<EmbeddingMatch<CodeBlock>> semanticMatches =
            vectorStore.findRelevant(queryEmbedding, 5, 0.7);

        semanticMatches.stream()
            .map(EmbeddingMatch::embedded)
            .forEach(results::add);

        // 策略 3: 调用链检索（检索调用栈中的其他方法）
        for (StackFrame frame : stackInfo.getFrames()) {
            if (frame.isProjectCode()) {
                CodeBlock callerCode = findMethodCode(
                    frame.getClassName(),
                    frame.getMethodName()
                );
                if (callerCode != null && !results.contains(callerCode)) {
                    results.add(callerCode);
                }
            }
        }

        return results.stream()
            .distinct()
            .limit(10)
            .collect(Collectors.toList());
    }

    /**
     * 精确查找代码
     */
    private CodeBlock findExactCode(String className, String methodName, Integer line) {
        // 从向量数据库中查询（也可以直接从文件系统读取）
        return vectorStore.search(
            Filters.eq("className", className)
                .and(Filters.eq("methodName", methodName))
        ).stream()
            .filter(block -> line >= block.getStartLine() && line <= block.getEndLine())
            .findFirst()
            .orElse(null);
    }

    /**
     * 构建代码检索查询
     */
    private String buildCodeQuery(StackTraceInfo stackInfo) {
        return String.format(
            "Exception: %s, Error in class %s method %s, message: %s",
            stackInfo.getExceptionType(),
            stackInfo.getErrorClass(),
            stackInfo.getErrorMethod(),
            stackInfo.getExceptionMessage()
        );
    }

    /**
     * 检索相似历史告警
     */
    private List<HistoricalAlert> retrieveSimilarAlerts(StackTraceInfo stackInfo) {
        // 构建查询
        String query = String.format(
            "%s %s",
            stackInfo.getExceptionType(),
            stackInfo.getExceptionMessage()
        );

        // 向量检索
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<EmbeddingMatch<HistoricalAlert>> matches =
            vectorStore.findRelevant(queryEmbedding, 5, 0.6);

        return matches.stream()
            .map(EmbeddingMatch::embedded)
            .collect(Collectors.toList());
    }

    /**
     * 检索相关文档
     */
    private List<Document> retrieveRelevantDocs(StackTraceInfo stackInfo) {
        // 根据关键词检索运维文档、FAQ 等
        List<String> keywords = stackInfo.getKeywords();

        // 可以从 Elasticsearch 或向量数据库检索
        return documentService.search(keywords, 3);
    }
}

@Data
public class RetrievalContext {
    private List<CodeBlock> codeBlocks;           // 相关代码
    private List<HistoricalAlert> historicalAlerts; // 历史告警
    private List<Document> documents;              // 相关文档
}
```

---

### 3.4 上下文构建模块

#### 功能职责
将检索到的信息组织成结构化的上下文，用于 AI 分析。

```java
@Service
public class ContextBuilder {

    /**
     * 构建分析上下文
     */
    public AnalysisContext build(StackTraceInfo stackInfo, RetrievalContext retrieval) {
        AnalysisContext context = new AnalysisContext();

        // 1. 错误代码上下文
        context.setErrorCodeContext(buildErrorCodeContext(stackInfo, retrieval));

        // 2. 调用链上下文
        context.setCallChainContext(buildCallChainContext(stackInfo, retrieval));

        // 3. 历史案例上下文
        context.setHistoricalContext(buildHistoricalContext(retrieval));

        // 4. 依赖配置上下文
        context.setConfigContext(buildConfigContext(stackInfo));

        return context;
    }

    /**
     * 构建错误代码上下文
     */
    private String buildErrorCodeContext(StackTraceInfo stackInfo, RetrievalContext retrieval) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 错误位置\n");
        sb.append(String.format("文件: %s\n", stackInfo.getErrorFile()));
        sb.append(String.format("类: %s\n", stackInfo.getErrorClass()));
        sb.append(String.format("方法: %s\n", stackInfo.getErrorMethod()));
        sb.append(String.format("行号: %d\n\n", stackInfo.getErrorLine()));

        // 添加出错位置的代码
        CodeBlock errorCode = retrieval.getCodeBlocks().stream()
            .filter(block -> block.getClassName().equals(stackInfo.getErrorClass())
                && block.getMethodName().equals(stackInfo.getErrorMethod()))
            .findFirst()
            .orElse(null);

        if (errorCode != null) {
            sb.append("## 相关代码\n");
            sb.append("```java\n");
            sb.append(highlightErrorLine(errorCode.getContent(),
                stackInfo.getErrorLine() - errorCode.getStartLine()));
            sb.append("\n```\n");
        }

        return sb.toString();
    }

    /**
     * 高亮错误行
     */
    private String highlightErrorLine(String code, int errorLineOffset) {
        String[] lines = code.split("\n");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (i == errorLineOffset) {
                sb.append(">>> ").append(lines[i]).append(" // ⚠️ ERROR HERE\n");
            } else {
                sb.append("    ").append(lines[i]).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 构建调用链上下文
     */
    private String buildCallChainContext(StackTraceInfo stackInfo, RetrievalContext retrieval) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 调用链\n");
        for (int i = 0; i < Math.min(stackInfo.getFrames().size(), 10); i++) {
            StackFrame frame = stackInfo.getFrames().get(i);
            sb.append(String.format("%d. %s.%s(%s:%d)%s\n",
                i + 1,
                frame.getClassName(),
                frame.getMethodName(),
                frame.getFileName(),
                frame.getLineNumber(),
                frame.isProjectCode() ? " [项目代码]" : ""
            ));
        }

        return sb.toString();
    }

    /**
     * 构建历史案例上下文
     */
    private String buildHistoricalContext(RetrievalContext retrieval) {
        if (retrieval.getHistoricalAlerts().isEmpty()) {
            return "## 历史案例\n无相似历史告警\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 历史相似案例\n");

        for (int i = 0; i < retrieval.getHistoricalAlerts().size(); i++) {
            HistoricalAlert alert = retrieval.getHistoricalAlerts().get(i);
            sb.append(String.format("\n### 案例 %d (相似度: %.2f)\n",
                i + 1, alert.getSimilarity()));
            sb.append(String.format("问题: %s\n", alert.getProblem()));
            sb.append(String.format("根因: %s\n", alert.getRootCause()));
            sb.append(String.format("解决方案: %s\n", alert.getSolution()));
        }

        return sb.toString();
    }

    /**
     * 构建配置上下文
     */
    private String buildConfigContext(StackTraceInfo stackInfo) {
        // 检索相关配置文件（如果异常涉及配置）
        // 例如：数据库连接异常 -> 检索 application.yml 中的数据库配置
        return "## 相关配置\n（待实现）\n";
    }
}

@Data
public class AnalysisContext {
    private String errorCodeContext;      // 错误代码上下文
    private String callChainContext;      // 调用链上下文
    private String historicalContext;     // 历史案例上下文
    private String configContext;         // 配置上下文
}
```

---

### 3.5 AI 分析模块

#### 功能职责
使用 LLM 结合上下文进行深度分析。

```java
@Service
public class StackAnalysisService {

    @Autowired
    private ChatLanguageModel chatModel;

    @Autowired
    private StackTraceParser stackParser;

    @Autowired
    private RagRetrievalService retrievalService;

    @Autowired
    private ContextBuilder contextBuilder;

    /**
     * 分析堆栈异常
     */
    public StackAnalysisResult analyze(String stackTrace) {
        // 1. 解析堆栈
        StackTraceInfo stackInfo = stackParser.parse(stackTrace);

        // 2. RAG 检索
        RetrievalContext retrieval = retrievalService.retrieve(stackInfo);

        // 3. 构建上下文
        AnalysisContext context = contextBuilder.build(stackInfo, retrieval);

        // 4. 构建 Prompt
        String prompt = buildAnalysisPrompt(stackInfo, context);

        // 5. AI 分析
        String analysis = chatModel.generate(prompt);

        // 6. 解析结果
        return parseAnalysisResult(analysis, stackInfo, retrieval);
    }

    /**
     * 构建分析 Prompt
     */
    private String buildAnalysisPrompt(StackTraceInfo stackInfo, AnalysisContext context) {
        return String.format("""
            你是一个资深的 Java 后端工程师和问题排查专家。请分析以下异常堆栈信息。

            # 异常堆栈
            异常类型: %s
            异常消息: %s

            %s

            %s

            %s

            # 分析任务
            请提供详细的分析报告，包括：

            1. **问题根因分析**
               - 为什么会发生这个异常？
               - 是代码逻辑问题、配置问题还是环境问题？
               - 从代码层面分析具体的错误原因

            2. **涉及的代码位置**
               - 指出问题代码的具体位置
               - 分析代码逻辑是否存在缺陷

            3. **影响范围**
               - 这个异常会影响哪些功能？
               - 是否会导致数据不一致？
               - 对用户的影响程度

            4. **修复建议**
               - 提供具体的修复方案
               - 包括代码修改建议
               - 是否需要配置调整或数据修复

            5. **预防措施**
               - 如何避免类似问题再次发生？
               - 需要添加哪些防御性代码？
               - 是否需要改进监控或告警？

            6. **参考历史案例**
               - 如果有相似的历史案例，说明参考价值

            请以 JSON 格式返回结果，格式如下：
            ```json
            {
              "rootCause": "根因分析",
              "codeLocation": "涉及代码位置",
              "impact": "影响范围",
              "fixSuggestions": ["修复建议1", "修复建议2"],
              "preventionMeasures": ["预防措施1", "预防措施2"],
              "referenceCase": "相关历史案例引用",
              "confidence": 0.85
            }
            ```
            """,
            stackInfo.getExceptionType(),
            stackInfo.getExceptionMessage(),
            context.getErrorCodeContext(),
            context.getCallChainContext(),
            context.getHistoricalContext()
        );
    }

    /**
     * 解析分析结果
     */
    private StackAnalysisResult parseAnalysisResult(
            String analysis,
            StackTraceInfo stackInfo,
            RetrievalContext retrieval) {

        // 使用 FastJSON 解析 JSON 结果
        JSONObject json = JSON.parseObject(extractJson(analysis));

        return StackAnalysisResult.builder()
            .exceptionType(stackInfo.getExceptionType())
            .exceptionMessage(stackInfo.getExceptionMessage())
            .errorLocation(String.format("%s.%s:%d",
                stackInfo.getErrorClass(),
                stackInfo.getErrorMethod(),
                stackInfo.getErrorLine()))
            .rootCause(json.getString("rootCause"))
            .codeLocation(json.getString("codeLocation"))
            .impact(json.getString("impact"))
            .fixSuggestions(json.getJSONArray("fixSuggestions")
                .toJavaList(String.class))
            .preventionMeasures(json.getJSONArray("preventionMeasures")
                .toJavaList(String.class))
            .referenceCase(json.getString("referenceCase"))
            .confidence(json.getDouble("confidence"))
            .relatedCode(retrieval.getCodeBlocks())
            .historicalAlerts(retrieval.getHistoricalAlerts())
            .analyzedAt(LocalDateTime.now())
            .build();
    }

    /**
     * 提取 JSON 内容
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}

@Data
@Builder
public class StackAnalysisResult {
    // 异常信息
    private String exceptionType;
    private String exceptionMessage;
    private String errorLocation;

    // 分析结果
    private String rootCause;            // 根因
    private String codeLocation;         // 代码位置
    private String impact;               // 影响范围
    private List<String> fixSuggestions; // 修复建议
    private List<String> preventionMeasures; // 预防措施
    private String referenceCase;        // 参考案例
    private Double confidence;           // 置信度

    // 附加信息
    private List<CodeBlock> relatedCode;       // 相关代码
    private List<HistoricalAlert> historicalAlerts; // 历史告警
    private LocalDateTime analyzedAt;          // 分析时间
}
```

---

## 4. 使用示例

### 4.1 API 接口

```java
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Autowired
    private StackAnalysisService analysisService;

    /**
     * 分析堆栈异常
     */
    @PostMapping("/stack")
    public ResponseEntity<StackAnalysisResult> analyzeStack(
            @RequestBody StackAnalysisRequest request) {

        StackAnalysisResult result = analysisService.analyze(request.getStackTrace());

        return ResponseEntity.ok(result);
    }
}

@Data
public class StackAnalysisRequest {
    private String stackTrace;      // 堆栈信息
    private String serviceName;     // 服务名称
    private String environment;     // 环境
    private Map<String, String> metadata; // 元数据
}
```

### 4.2 调用示例

```bash
curl -X POST http://localhost:8080/api/analysis/stack \
  -H "Content-Type: application/json" \
  -d '{
    "stackTrace": "java.lang.NullPointerException: Cannot invoke \"User.getName()\" because \"user\" is null\n  at com.example.service.UserService.getUserInfo(UserService.java:45)\n  at com.example.controller.UserController.getUser(UserController.java:30)",
    "serviceName": "user-service",
    "environment": "production"
  }'
```

---

## 5. 优化建议

### 5.1 性能优化

1. **缓存策略**
   - 缓存常见异常的分析结果
   - 缓存代码块的 Embedding
   - 使用 Redis 缓存检索结果

2. **异步处理**
   - 告警分析改为异步任务
   - 使用消息队列解耦
   - 批量处理相似告警

3. **索引优化**
   - 增量索引源码变更
   - 定期清理过期索引
   - 优化向量检索参数

### 5.2 准确性优化

1. **提示词优化**
   - 使用 Few-shot Learning
   - 提供更多示例
   - 持续优化 Prompt

2. **检索优化**
   - 多路检索融合
   - 调整相似度阈值
   - 增加重排序机制

3. **反馈循环**
   - 收集用户反馈
   - 人工标注样本
   - 持续优化模型

---

## 6. 部署架构

```
┌─────────────────────────────────────────────┐
│            Application Layer                 │
│  ┌────────────────────────────────────────┐ │
│  │   Spring Boot Application              │ │
│  │   - AlertController                    │ │
│  │   - AnalysisController                 │ │
│  │   - StackAnalysisService               │ │
│  └────────────────────────────────────────┘ │
└──────────────────┬──────────────────────────┘
                   │
    ┌──────────────┼──────────────┐
    │              │              │
┌───▼────┐   ┌─────▼────┐   ┌────▼─────┐
│ MySQL  │   │  Redis   │   │ Milvus   │
│(元数据)│   │  (缓存)  │   │(向量检索)│
└────────┘   └──────────┘   └──────────┘
```

---

## 7. 总结

这个基于 RAG 的堆栈分析方案：

✅ **精确定位**: 通过堆栈解析精确定位错误代码
✅ **上下文增强**: 结合源码、历史案例和文档提供全面上下文
✅ **智能分析**: 利用 LLM 深度分析问题根因和解决方案
✅ **知识沉淀**: 通过向量数据库积累和检索历史经验
✅ **可扩展**: 模块化设计，易于扩展和优化

这个方案可以显著提升告警处理效率，减少人工分析时间，特别适合处理复杂的生产环境异常。
