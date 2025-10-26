# 知识库构建方案设计文档

## 1. 概述

知识库是 AI Agent 的核心数据基础,通过 RAG (Retrieval-Augmented Generation) 技术,将历史处理方案、源代码、文档等知识向量化存储,实现精准检索和智能推荐。

### 核心目标

- **知识沉淀**:将每次问题处理形成可复用的知识
- **精准检索**:通过向量相似度快速找到相关知识
- **持续学习**:知识库随系统运行不断丰富
- **多源融合**:整合代码、文档、工单、日志等多种知识来源

---

## 2. 知识库架构

```
┌─────────────────────────────────────────────────────────┐
│                      知识来源层                           │
│  源代码  │  工单方案  │  技术文档  │  运维手册  │  日志   │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   知识采集层                              │
│  CodeScanner │ TicketExtractor │ DocCrawler │ LogParser│
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   知识处理层                              │
│  文本分割  │  清洗过滤  │  元数据提取  │  向量编码      │
│  ChunkSplitter │ TextCleaner │ MetadataExtractor │ Embed│
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   向量存储层                              │
│        Milvus (向量数据库) + Elasticsearch (全文检索)     │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   检索服务层                              │
│  混合检索  │  重排序  │  相关性评分  │  结果融合        │
│  HybridRetriever │ Reranker │ ScoreCalculator │ Merger │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 数据模型设计

### 3.1 知识库表 (knowledge_base)

```sql
CREATE TABLE knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识ID',
    knowledge_id VARCHAR(64) UNIQUE NOT NULL COMMENT '知识唯一标识',

    -- 知识分类
    knowledge_type VARCHAR(32) NOT NULL COMMENT '知识类型：CODE/SOLUTION/DOCUMENT/FAQ',
    category VARCHAR(64) COMMENT '分类标签',
    tags JSON COMMENT '标签列表',

    -- 知识内容
    title VARCHAR(255) NOT NULL COMMENT '标题',
    content TEXT NOT NULL COMMENT '内容',
    summary VARCHAR(500) COMMENT '摘要',

    -- 来源信息
    source_type VARCHAR(32) NOT NULL COMMENT '来源类型：TICKET/CODE/DOC/MANUAL',
    source_id VARCHAR(128) COMMENT '来源ID',
    source_url VARCHAR(512) COMMENT '来源URL',

    -- 元数据
    metadata JSON COMMENT '元数据',

    -- 向量信息
    embedding_model VARCHAR(64) COMMENT '向量模型',
    vector_id VARCHAR(128) COMMENT '向量存储ID',

    -- 质量评分
    quality_score DOUBLE DEFAULT 0.0 COMMENT '质量评分 0-1',
    relevance_score DOUBLE DEFAULT 0.0 COMMENT '相关性评分',
    usage_count INT DEFAULT 0 COMMENT '使用次数',

    -- 状态
    status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/ARCHIVED/DELETED',
    verified BOOLEAN DEFAULT FALSE COMMENT '是否已验证',
    verified_by VARCHAR(64) COMMENT '验证人',
    verified_at TIMESTAMP NULL COMMENT '验证时间',

    -- 审计字段
    created_by VARCHAR(64) DEFAULT 'system' COMMENT '创建人',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_type (knowledge_type, status),
    INDEX idx_source (source_type, source_id),
    INDEX idx_quality (quality_score DESC),
    INDEX idx_usage (usage_count DESC),
    FULLTEXT INDEX ft_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';
```

### 3.2 代码索引表 (code_index)

```sql
CREATE TABLE code_index (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id VARCHAR(64) NOT NULL COMMENT '关联知识ID',

    -- 代码位置
    project_name VARCHAR(128) NOT NULL COMMENT '项目名',
    module_name VARCHAR(128) COMMENT '模块名',
    package_name VARCHAR(255) NOT NULL COMMENT '包名',
    class_name VARCHAR(128) NOT NULL COMMENT '类名',
    method_name VARCHAR(128) COMMENT '方法名',
    file_path VARCHAR(512) NOT NULL COMMENT '文件路径',
    start_line INT COMMENT '起始行',
    end_line INT COMMENT '结束行',

    -- 代码内容
    code_snippet TEXT NOT NULL COMMENT '代码片段',
    javadoc TEXT COMMENT 'JavaDoc',

    -- 代码元数据
    code_type VARCHAR(32) NOT NULL COMMENT '代码类型：CLASS/METHOD/FIELD',
    modifiers JSON COMMENT '修饰符',
    annotations JSON COMMENT '注解',
    dependencies JSON COMMENT '依赖',

    -- Git 信息
    git_repo VARCHAR(255) COMMENT 'Git仓库',
    git_branch VARCHAR(64) COMMENT '分支',
    git_commit VARCHAR(64) COMMENT '提交ID',
    last_author VARCHAR(64) COMMENT '最后修改人',
    last_modified_at TIMESTAMP COMMENT '最后修改时间',

    -- 向量信息
    vector_id VARCHAR(128) COMMENT '向量ID',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_knowledge (knowledge_id),
    INDEX idx_project (project_name),
    INDEX idx_class (class_name, method_name),
    INDEX idx_file (file_path),
    FULLTEXT INDEX ft_code (code_snippet)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码索引';
```

### 3.3 解决方案表 (solution_library)

```sql
CREATE TABLE solution_library (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id VARCHAR(64) NOT NULL COMMENT '关联知识ID',

    -- 问题描述
    problem_type VARCHAR(64) NOT NULL COMMENT '问题类型',
    problem_description TEXT NOT NULL COMMENT '问题描述',
    exception_fingerprint VARCHAR(64) COMMENT '异常指纹',

    -- 解决方案
    solution TEXT NOT NULL COMMENT '解决方案',
    solution_type VARCHAR(32) NOT NULL COMMENT '方案类型',
    steps JSON COMMENT '操作步骤',
    code_fix TEXT COMMENT '代码修复',
    config_change JSON COMMENT '配置变更',

    -- 效果评估
    success_rate DOUBLE DEFAULT 0.0 COMMENT '成功率',
    avg_resolve_time INT COMMENT '平均解决时间（分钟）',
    applied_count INT DEFAULT 0 COMMENT '应用次数',
    feedback_positive INT DEFAULT 0 COMMENT '正面反馈数',
    feedback_negative INT DEFAULT 0 COMMENT '负面反馈数',

    -- 来源工单
    source_ticket_id BIGINT COMMENT '来源工单ID',
    source_ticket_no VARCHAR(64) COMMENT '来源工单编号',
    resolver VARCHAR(64) COMMENT '解决人',
    resolved_at TIMESTAMP COMMENT '解决时间',

    -- 适用范围
    applicable_services JSON COMMENT '适用服务',
    applicable_versions JSON COMMENT '适用版本',

    -- 向量信息
    vector_id VARCHAR(128) COMMENT '向量ID',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_knowledge (knowledge_id),
    INDEX idx_fingerprint (exception_fingerprint),
    INDEX idx_problem_type (problem_type),
    INDEX idx_success_rate (success_rate DESC),
    INDEX idx_applied_count (applied_count DESC),
    FULLTEXT INDEX ft_problem (problem_description, solution)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='解决方案库';
```

---

## 4. 知识采集

### 4.1 源代码扫描器

```java
@Service
@Slf4j
public class CodeScannerService {

    @Autowired
    private CodeIndexRepository codeIndexRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStoreService vectorStoreService;

    /**
     * 扫描项目代码
     */
    public void scanProject(String projectPath, String projectName) {
        log.info("开始扫描项目: {}", projectPath);

        // 1. 解析 Java 文件
        List<CompilationUnit> compilationUnits = parseJavaFiles(projectPath);

        int indexed = 0;
        for (CompilationUnit cu : compilationUnits) {
            // 2. 提取类信息
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                indexClass(cls, projectName, projectPath, cu);
                indexed++;
            });

            // 3. 提取方法信息
            cu.findAll(MethodDeclaration.class).forEach(method -> {
                indexMethod(method, projectName, projectPath, cu);
                indexed++;
            });
        }

        log.info("项目 {} 扫描完成，共索引 {} 个代码块", projectName, indexed);
    }

    /**
     * 解析 Java 文件
     */
    private List<CompilationUnit> parseJavaFiles(String projectPath) {
        List<CompilationUnit> units = new ArrayList<>();

        try {
            SourceRoot sourceRoot = new SourceRoot(Paths.get(projectPath));
            sourceRoot.tryToParse();

            sourceRoot.getCompilationUnits().forEach(cu -> {
                if (cu.getResult().isPresent()) {
                    units.add(cu.getResult().get());
                }
            });

        } catch (IOException e) {
            log.error("解析项目失败: {}", projectPath, e);
        }

        return units;
    }

    /**
     * 索引类
     */
    private void indexClass(ClassOrInterfaceDeclaration cls, String projectName,
                           String projectPath, CompilationUnit cu) {
        try {
            // 1. 提取类信息
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            String className = cls.getNameAsString();
            String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;

            // 2. 构建代码片段
            String codeSnippet = cls.toString();

            // 3. 提取 JavaDoc
            String javadoc = cls.getJavadoc()
                .map(jd -> jd.getDescription().toText())
                .orElse("");

            // 4. 构建索引文本（用于向量化）
            String indexText = buildClassIndexText(fullClassName, javadoc, codeSnippet);

            // 5. 生成向量
            Embedding embedding = embeddingModel.embed(indexText).content();

            // 6. 保存到向量数据库
            String vectorId = vectorStoreService.add(
                embedding,
                Map.of(
                    "type", "class",
                    "project", projectName,
                    "package", packageName,
                    "class", className,
                    "content", codeSnippet
                )
            );

            // 7. 保存到数据库
            CodeIndex codeIndex = CodeIndex.builder()
                .knowledgeId(generateKnowledgeId("CODE", fullClassName))
                .projectName(projectName)
                .packageName(packageName)
                .className(className)
                .filePath(cu.getStorage().map(s -> s.getPath().toString()).orElse(""))
                .startLine(cls.getBegin().map(p -> p.line).orElse(0))
                .endLine(cls.getEnd().map(p -> p.line).orElse(0))
                .codeSnippet(codeSnippet)
                .javadoc(javadoc)
                .codeType("CLASS")
                .modifiers(extractModifiers(cls))
                .annotations(extractAnnotations(cls))
                .vectorId(vectorId)
                .build();

            codeIndexRepository.save(codeIndex);

        } catch (Exception e) {
            log.error("索引类失败: {}", cls.getNameAsString(), e);
        }
    }

    /**
     * 索引方法
     */
    private void indexMethod(MethodDeclaration method, String projectName,
                            String projectPath, CompilationUnit cu) {
        try {
            // 1. 提取方法信息
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            String className = method.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(cls -> cls.getNameAsString())
                .orElse("Unknown");

            String methodName = method.getNameAsString();

            // 2. 构建代码片段
            String codeSnippet = method.toString();

            // 3. 提取 JavaDoc
            String javadoc = method.getJavadoc()
                .map(jd -> jd.getDescription().toText())
                .orElse("");

            // 4. 构建索引文本
            String indexText = buildMethodIndexText(className, methodName, javadoc, codeSnippet);

            // 5. 生成向量
            Embedding embedding = embeddingModel.embed(indexText).content();

            // 6. 保存到向量数据库
            String vectorId = vectorStoreService.add(
                embedding,
                Map.of(
                    "type", "method",
                    "project", projectName,
                    "package", packageName,
                    "class", className,
                    "method", methodName,
                    "content", codeSnippet
                )
            );

            // 7. 保存到数据库
            CodeIndex codeIndex = CodeIndex.builder()
                .knowledgeId(generateKnowledgeId("CODE", className + "." + methodName))
                .projectName(projectName)
                .packageName(packageName)
                .className(className)
                .methodName(methodName)
                .filePath(cu.getStorage().map(s -> s.getPath().toString()).orElse(""))
                .startLine(method.getBegin().map(p -> p.line).orElse(0))
                .endLine(method.getEnd().map(p -> p.line).orElse(0))
                .codeSnippet(codeSnippet)
                .javadoc(javadoc)
                .codeType("METHOD")
                .modifiers(extractModifiers(method))
                .annotations(extractAnnotations(method))
                .vectorId(vectorId)
                .build();

            codeIndexRepository.save(codeIndex);

        } catch (Exception e) {
            log.error("索引方法失败: {}", method.getNameAsString(), e);
        }
    }

    /**
     * 构建类索引文本
     */
    private String buildClassIndexText(String className, String javadoc, String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("类名: ").append(className).append("\n");
        if (!javadoc.isEmpty()) {
            sb.append("文档: ").append(javadoc).append("\n");
        }
        sb.append("代码:\n").append(code);
        return sb.toString();
    }

    /**
     * 构建方法索引文本
     */
    private String buildMethodIndexText(String className, String methodName,
                                       String javadoc, String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("类名: ").append(className).append("\n");
        sb.append("方法名: ").append(methodName).append("\n");
        if (!javadoc.isEmpty()) {
            sb.append("文档: ").append(javadoc).append("\n");
        }
        sb.append("代码:\n").append(code);
        return sb.toString();
    }

    private String generateKnowledgeId(String type, String identifier) {
        return type + "_" + DigestUtils.md5Hex(identifier);
    }

    private JSON extractModifiers(NodeWithModifiers<?> node) {
        List<String> modifiers = node.getModifiers().stream()
            .map(m -> m.getKeyword().asString())
            .toList();
        return JSON.toJSON(modifiers);
    }

    private JSON extractAnnotations(NodeWithAnnotations<?> node) {
        List<String> annotations = node.getAnnotations().stream()
            .map(a -> a.getNameAsString())
            .toList();
        return JSON.toJSON(annotations);
    }
}
```

### 4.2 工单方案提取器

```java
@Service
@Slf4j
public class SolutionExtractorService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private SolutionLibraryRepository solutionLibraryRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStoreService vectorStoreService;

    /**
     * 从工单提取解决方案
     */
    @Async
    public void extractFromTicket(Long ticketId) {
        log.info("开始从工单 {} 提取解决方案", ticketId);

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("工单不存在: " + ticketId));

        // 只处理已解决的工单
        if (!ticket.getStatus().equals(TicketStatus.RESOLVED)
            && !ticket.getStatus().equals(TicketStatus.CLOSED)) {
            log.warn("工单 {} 未解决，跳过", ticketId);
            return;
        }

        // 检查是否有解决方案
        if (ticket.getSolution() == null || ticket.getSolution().isEmpty()) {
            log.warn("工单 {} 没有解决方案，跳过", ticketId);
            return;
        }

        try {
            // 1. 构建问题描述
            String problemDescription = buildProblemDescription(ticket);

            // 2. 构建索引文本
            String indexText = buildSolutionIndexText(ticket, problemDescription);

            // 3. 生成向量
            Embedding embedding = embeddingModel.embed(indexText).content();

            // 4. 保存到向量数据库
            String vectorId = vectorStoreService.add(
                embedding,
                Map.of(
                    "type", "solution",
                    "ticket_no", ticket.getTicketNo(),
                    "problem_type", ticket.getProblemType(),
                    "fingerprint", ticket.getExceptionFingerprint(),
                    "problem", problemDescription,
                    "solution", ticket.getSolution()
                )
            );

            // 5. 保存到解决方案库
            SolutionLibrary solution = SolutionLibrary.builder()
                .knowledgeId(generateKnowledgeId("SOLUTION", ticket.getExceptionFingerprint()))
                .problemType(ticket.getProblemType())
                .problemDescription(problemDescription)
                .exceptionFingerprint(ticket.getExceptionFingerprint())
                .solution(ticket.getSolution())
                .solutionType(ticket.getSolutionType())
                .codeFix(extractCodeFix(ticket))
                .configChange(ticket.getCodeChanges())
                .successRate(1.0)  // 初始成功率
                .appliedCount(1)   // 初始应用次数
                .feedbackPositive(0)
                .feedbackNegative(0)
                .sourceTicketId(ticket.getId())
                .sourceTicketNo(ticket.getTicketNo())
                .resolver(ticket.getAssignee())
                .resolvedAt(ticket.getResolvedAt())
                .vectorId(vectorId)
                .build();

            solutionLibraryRepository.save(solution);

            // 6. 更新知识库表
            createKnowledgeEntry(solution, ticket);

            log.info("工单 {} 解决方案提取完成", ticket.getTicketNo());

        } catch (Exception e) {
            log.error("提取工单 {} 解决方案失败", ticketId, e);
        }
    }

    /**
     * 构建问题描述
     */
    private String buildProblemDescription(Ticket ticket) {
        return String.format("""
            服务: %s
            异常类型: %s
            错误位置: %s
            错误信息: %s
            """,
            ticket.getServiceName(),
            ticket.getProblemType(),
            ticket.getErrorLocation(),
            ticket.getAlertContent().substring(0, Math.min(500, ticket.getAlertContent().length()))
        );
    }

    /**
     * 构建解决方案索引文本
     */
    private String buildSolutionIndexText(Ticket ticket, String problemDescription) {
        return String.format("""
            问题类型: %s
            问题描述:
            %s

            解决方案:
            %s

            根因:
            %s
            """,
            ticket.getProblemType(),
            problemDescription,
            ticket.getSolution(),
            ticket.getRootCause() != null ? ticket.getRootCause() : ""
        );
    }

    private String extractCodeFix(Ticket ticket) {
        // 从解决方案中提取代码片段
        // 简单实现：查找 ```java ... ``` 代码块
        String solution = ticket.getSolution();
        Pattern pattern = Pattern.compile("```(?:java)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(solution);

        StringBuilder codeFix = new StringBuilder();
        while (matcher.find()) {
            codeFix.append(matcher.group(1)).append("\n\n");
        }

        return codeFix.toString();
    }

    private void createKnowledgeEntry(SolutionLibrary solution, Ticket ticket) {
        Knowledge knowledge = Knowledge.builder()
            .knowledgeId(solution.getKnowledgeId())
            .knowledgeType("SOLUTION")
            .category(ticket.getProblemCategory())
            .tags(List.of(ticket.getProblemType(), ticket.getServiceName()))
            .title(String.format("[%s] %s 解决方案", ticket.getProblemType(), ticket.getServiceName()))
            .content(ticket.getSolution())
            .summary(ticket.getSolution().substring(0, Math.min(200, ticket.getSolution().length())))
            .sourceType("TICKET")
            .sourceId(ticket.getTicketNo())
            .metadata(Map.of(
                "problem_type", ticket.getProblemType(),
                "service_name", ticket.getServiceName(),
                "severity", ticket.getSeverity()
            ))
            .vectorId(solution.getVectorId())
            .qualityScore(0.8)  // 初始质量分
            .status("ACTIVE")
            .verified(false)
            .createdBy(ticket.getAssignee())
            .build();

        knowledgeRepository.save(knowledge);
    }

    private String generateKnowledgeId(String type, String identifier) {
        return type + "_" + DigestUtils.md5Hex(identifier);
    }
}
```

---

## 5. 文本分割和向量化

### 5.1 文本分割器

```java
@Service
public class TextSplitterService {

    /**
     * 递归字符分割（推荐）
     */
    public List<String> recursiveSplit(String text, int chunkSize, int chunkOverlap) {
        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(
            chunkSize,
            chunkOverlap,
            List.of("\n\n", "\n", " ", "")  // 分割符优先级
        );

        return splitter.split(text);
    }

    /**
     * 代码专用分割器
     */
    public List<String> splitCode(String code, String language) {
        // 按方法、类等逻辑单元分割
        // 使用 JavaParser 或类似工具
        // 实现略
        return List.of(code);
    }

    /**
     * Markdown 分割器
     */
    public List<String> splitMarkdown(String markdown) {
        // 按标题层级分割
        List<String> chunks = new ArrayList<>();
        String[] sections = markdown.split("(?=^#{1,3} )", -1);
        chunks.addAll(Arrays.asList(sections));
        return chunks;
    }
}
```

### 5.2 向量存储服务

```java
@Service
@Slf4j
public class VectorStoreService {

    @Autowired
    private MilvusClient milvusClient;

    private static final String COLLECTION_NAME = "knowledge_vectors";
    private static final int VECTOR_DIM = 1024;  // BGE-Large 向量维度

    /**
     * 初始化向量集合
     */
    @PostConstruct
    public void initCollection() {
        if (!milvusClient.hasCollection(COLLECTION_NAME)) {
            createCollection();
        }
    }

    /**
     * 创建向量集合
     */
    private void createCollection() {
        FieldType idField = FieldType.newBuilder()
            .withName("id")
            .withDataType(DataType.VarChar)
            .withMaxLength(128)
            .withPrimaryKey(true)
            .build();

        FieldType vectorField = FieldType.newBuilder()
            .withName("embedding")
            .withDataType(DataType.FloatVector)
            .withDimension(VECTOR_DIM)
            .build();

        FieldType metadataField = FieldType.newBuilder()
            .withName("metadata")
            .withDataType(DataType.JSON)
            .build();

        CreateCollectionParam param = CreateCollectionParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .addFieldType(idField)
            .addFieldType(vectorField)
            .addFieldType(metadataField)
            .build();

        R<RpcStatus> response = milvusClient.createCollection(param);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("创建向量集合失败: " + response.getMessage());
        }

        // 创建索引
        createIndex();

        log.info("向量集合 {} 创建成功", COLLECTION_NAME);
    }

    /**
     * 创建向量索引
     */
    private void createIndex() {
        CreateIndexParam param = CreateIndexParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withFieldName("embedding")
            .withIndexType(IndexType.IVF_FLAT)
            .withMetricType(MetricType.IP)  // Inner Product (余弦相似度)
            .withExtraParam("{\"nlist\":1024}")
            .build();

        R<RpcStatus> response = milvusClient.createIndex(param);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("创建索引失败: " + response.getMessage());
        }

        log.info("向量索引创建成功");
    }

    /**
     * 添加向量
     */
    public String add(Embedding embedding, Map<String, Object> metadata) {
        String id = UUID.randomUUID().toString();

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("id", List.of(id)));
        fields.add(new InsertParam.Field("embedding", List.of(embedding.vector())));
        fields.add(new InsertParam.Field("metadata", List.of(new JSONObject(metadata))));

        InsertParam param = InsertParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withFields(fields)
            .build();

        R<MutationResult> response = milvusClient.insert(param);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("添加向量失败: " + response.getMessage());
        }

        return id;
    }

    /**
     * 批量添加向量
     */
    public List<String> addBatch(List<Embedding> embeddings, List<Map<String, Object>> metadataList) {
        List<String> ids = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();
        List<JSONObject> metadata = new ArrayList<>();

        for (int i = 0; i < embeddings.size(); i++) {
            String id = UUID.randomUUID().toString();
            ids.add(id);
            vectors.add(Arrays.stream(embeddings.get(i).vector())
                .boxed()
                .map(Float::valueOf)
                .toList());
            metadata.add(new JSONObject(metadataList.get(i)));
        }

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("id", ids));
        fields.add(new InsertParam.Field("embedding", vectors));
        fields.add(new InsertParam.Field("metadata", metadata));

        InsertParam param = InsertParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withFields(fields)
            .build();

        R<MutationResult> response = milvusClient.insert(param);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("批量添加向量失败: " + response.getMessage());
        }

        return ids;
    }

    /**
     * 搜索相似向量
     */
    public List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int topK, String filter) {
        List<Float> queryVector = Arrays.stream(queryEmbedding.vector())
            .boxed()
            .map(Float::valueOf)
            .toList();

        SearchParam.Builder paramBuilder = SearchParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withVectorFieldName("embedding")
            .withVectors(List.of(queryVector))
            .withTopK(topK)
            .withMetricType(MetricType.IP);

        if (filter != null && !filter.isEmpty()) {
            paramBuilder.withExpr(filter);
        }

        SearchParam param = paramBuilder.build();

        R<SearchResults> response = milvusClient.search(param);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("搜索失败: " + response.getMessage());
        }

        // 解析结果
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());

        for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
            QueryResultsWrapper.RowRecord record = wrapper.getRowRecords(0).get(i);
            Map<String, Object> metadata = (Map<String, Object>) record.get("metadata");
            double score = (double) record.get("distance");

            TextSegment segment = TextSegment.from(
                (String) metadata.getOrDefault("content", "")
            );

            matches.add(new EmbeddingMatch<>(score, (String) record.get("id"), queryEmbedding, segment));
        }

        return matches;
    }

    /**
     * 删除向量
     */
    public void delete(String id) {
        DeleteParam param = DeleteParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withExpr("id == \"" + id + "\"")
            .build();

        R<MutationResult> response = milvusClient.delete(param);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("删除向量失败: " + response.getMessage());
        }
    }
}
```

---

## 6. 混合检索服务

```java
@Service
@Slf4j
public class HybridRetrievalService {

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private RerankerService rerankerService;

    /**
     * 混合检索（向量 + 关键词）
     */
    public List<RetrievalResult> hybridSearch(String query, int topK, RetrievalConfig config) {
        // 1. 向量检索
        List<RetrievalResult> vectorResults = vectorSearch(query, topK * 2);

        // 2. 关键词检索
        List<RetrievalResult> keywordResults = keywordSearch(query, topK * 2);

        // 3. 结果融合（RRF - Reciprocal Rank Fusion）
        List<RetrievalResult> fusedResults = fuseResults(vectorResults, keywordResults, topK);

        // 4. 重排序（可选）
        if (config.isEnableReranker()) {
            fusedResults = rerankerService.rerank(query, fusedResults, topK);
        }

        return fusedResults;
    }

    /**
     * 向量检索
     */
    private List<RetrievalResult> vectorSearch(String query, int topK) {
        // 生成查询向量
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 搜索
        List<EmbeddingMatch<TextSegment>> matches = vectorStoreService.search(
            queryEmbedding,
            topK,
            null
        );

        // 转换结果
        return matches.stream()
            .map(match -> RetrievalResult.builder()
                .id(match.embeddingId())
                .content(match.embedded().text())
                .score(match.score())
                .source("vector")
                .build())
            .toList();
    }

    /**
     * 关键词检索
     */
    private List<RetrievalResult> keywordSearch(String query, int topK) {
        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index("knowledge_base")
                .query(q -> q
                    .multiMatch(m -> m
                        .query(query)
                        .fields("title^2", "content")  // title 权重更高
                    )
                )
                .size(topK)
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            return response.hits().hits().stream()
                .map(hit -> RetrievalResult.builder()
                    .id(hit.id())
                    .content((String) hit.source().get("content"))
                    .score(hit.score())
                    .source("keyword")
                    .build())
                .toList();

        } catch (Exception e) {
            log.error("关键词检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 结果融合（RRF）
     */
    private List<RetrievalResult> fuseResults(List<RetrievalResult> vectorResults,
                                             List<RetrievalResult> keywordResults,
                                             int topK) {
        Map<String, RetrievalResult> resultMap = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        // RRF 公式: RRF(d) = Σ 1 / (k + rank(d))
        // k 通常取 60
        int k = 60;

        // 计算向量检索的 RRF 分数
        for (int i = 0; i < vectorResults.size(); i++) {
            RetrievalResult result = vectorResults.get(i);
            double rrfScore = 1.0 / (k + i + 1);
            rrfScores.merge(result.getId(), rrfScore, Double::sum);
            resultMap.putIfAbsent(result.getId(), result);
        }

        // 计算关键词检索的 RRF 分数
        for (int i = 0; i < keywordResults.size(); i++) {
            RetrievalResult result = keywordResults.get(i);
            double rrfScore = 1.0 / (k + i + 1);
            rrfScores.merge(result.getId(), rrfScore, Double::sum);
            resultMap.putIfAbsent(result.getId(), result);
        }

        // 按 RRF 分数排序
        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> {
                RetrievalResult result = resultMap.get(entry.getKey());
                result.setScore(entry.getValue());
                result.setSource("hybrid");
                return result;
            })
            .toList();
    }
}

@Data
@Builder
public class RetrievalResult {
    private String id;
    private String content;
    private double score;
    private String source;  // vector/keyword/hybrid
    private Map<String, Object> metadata;
}

@Data
@Builder
public class RetrievalConfig {
    private boolean enableReranker;
    private boolean enableHybrid;
    private double vectorWeight;
    private double keywordWeight;
}
```

---

## 7. 重排序服务

```java
@Service
public class RerankerService {

    @Autowired
    private ChatLanguageModel rerankerModel;

    /**
     * 使用 LLM 重排序
     */
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (candidates.isEmpty()) {
            return candidates;
        }

        // 构建重排序提示
        String prompt = buildRerankPrompt(query, candidates);

        // 调用 LLM 评分
        String response = rerankerModel.generate(prompt);

        // 解析评分结果
        Map<String, Double> scores = parseRerankScores(response);

        // 按新分数排序
        return candidates.stream()
            .map(result -> {
                double newScore = scores.getOrDefault(result.getId(), result.getScore());
                result.setScore(newScore);
                return result;
            })
            .sorted(Comparator.comparingDouble(RetrievalResult::getScore).reversed())
            .limit(topK)
            .toList();
    }

    private String buildRerankPrompt(String query, List<RetrievalResult> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对以下文档与查询的相关性进行评分（0-1）:\n\n");
        sb.append("查询: ").append(query).append("\n\n");
        sb.append("文档列表:\n");

        for (int i = 0; i < candidates.size(); i++) {
            RetrievalResult result = candidates.get(i);
            sb.append(String.format("[%d] ID: %s\n内容: %s\n\n",
                i, result.getId(), result.getContent().substring(0, Math.min(200, result.getContent().length()))));
        }

        sb.append("请返回JSON格式的评分: {\"doc_id\": score, ...}");

        return sb.toString();
    }

    private Map<String, Double> parseRerankScores(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(response, new TypeReference<Map<String, Double>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
```

---

## 8. 知识库管理 API

```java
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private CodeScannerService codeScannerService;

    /**
     * 扫描项目代码
     */
    @PostMapping("/scan-code")
    public void scanCode(@RequestBody ScanCodeRequest request) {
        codeScannerService.scanProject(request.getProjectPath(), request.getProjectName());
    }

    /**
     * 搜索知识
     */
    @GetMapping("/search")
    public List<KnowledgeVO> search(@RequestParam String query,
                                    @RequestParam(defaultValue = "10") int topK) {
        return knowledgeService.search(query, topK);
    }

    /**
     * 获取知识详情
     */
    @GetMapping("/{id}")
    public KnowledgeDetailVO get(@PathVariable Long id) {
        return knowledgeService.getDetail(id);
    }

    /**
     * 验证知识
     */
    @PostMapping("/{id}/verify")
    public void verify(@PathVariable Long id, @RequestParam String verifier) {
        knowledgeService.verify(id, verifier);
    }

    /**
     * 反馈
     */
    @PostMapping("/{id}/feedback")
    public void feedback(@PathVariable Long id, @RequestBody FeedbackRequest request) {
        knowledgeService.addFeedback(id, request);
    }
}
```

---

## 9. 配置示例

```yaml
# application.yml
one-agent:
  knowledge:
    # 向量模型配置
    embedding:
      model: BAAI/bge-large-zh-v1.5
      dimension: 1024
      api-key: ${EMBEDDING_API_KEY}
      base-url: https://api.siliconflow.cn

    # Milvus 配置
    milvus:
      host: localhost
      port: 19530
      database: one_agent
      collection: knowledge_vectors

    # Elasticsearch 配置
    elasticsearch:
      uris: http://localhost:9200
      username: elastic
      password: ${ES_PASSWORD}

    # 检索配置
    retrieval:
      top-k: 5
      similarity-threshold: 0.7
      enable-hybrid: true
      enable-reranker: true
      vector-weight: 0.6
      keyword-weight: 0.4

    # 文本分割配置
    text-splitter:
      chunk-size: 500
      chunk-overlap: 50

    # 自动扫描配置
    auto-scan:
      enabled: true
      cron: "0 0 2 * * ?"  # 每天凌晨2点
      projects:
        - name: one-agent-4j
          path: /path/to/one-agent-4j
```

---

## 10. 总结

### 核心特性

1. **多源知识采集**：代码、工单、文档、日志等多种来源
2. **智能向量化**：BGE-Large 中文向量模型，精准语义表示
3. **混合检索**：向量检索 + 关键词检索 + RRF 融合
4. **重排序优化**：LLM 重排序提升准确性
5. **自动更新**：工单解决后自动提取方案入库
6. **质量评估**:使用次数、反馈、验证状态等多维评分

### 预期效果

- **检索准确率 > 85%**：混合检索 + 重排序
- **响应时间 < 100ms**：向量数据库高性能检索
- **知识覆盖率 > 90%**：自动采集 + 持续积累
- **方案复用率 > 70%**：历史方案智能推荐
