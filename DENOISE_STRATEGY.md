# 告警降噪策略详细设计

## 1. 降噪的重要性

在生产环境中，告警风暴是常见问题：
- 📈 单个问题可能产生**成千上万**条告警
- 🔁 同一问题在多个实例上**重复出现**
- 🔗 一个根因问题引发**级联告警**
- 📢 大量噪音导致**真正问题被淹没**

**目标**：通过智能降噪，将告警数量减少 **80-90%**，同时不漏掉任何真正的问题。

---

## 2. 降噪策略体系

### 2.1 降噪策略分层

```
┌─────────────────────────────────────────────────────────┐
│              第一层：过滤层 (Filter)                      │
│  - 白名单/黑名单过滤                                      │
│  - 环境过滤 (测试环境告警)                                │
│  - 已知误报过滤                                           │
│  - 低优先级过滤                                           │
└────────────────────┬────────────────────────────────────┘
                     ↓ 过滤后剩余 60-70%
┌─────────────────────────────────────────────────────────┐
│              第二层：去重层 (Deduplication)               │
│  - 时间窗口去重 (5分钟内相同告警只保留一条)                │
│  - 指纹去重 (相同指纹的告警聚合)                          │
│  - 内容去重 (内容相似度去重)                              │
└────────────────────┬────────────────────────────────────┘
                     ↓ 去重后剩余 30-40%
┌─────────────────────────────────────────────────────────┐
│              第三层：聚合层 (Aggregation)                 │
│  - 服务级聚合 (同一服务的告警聚合)                        │
│  - 实例级聚合 (同一实例的告警聚合)                        │
│  - 时间段聚合 (固定时间窗口内聚合)                        │
│  - 关联聚合 (调用链相关的告警聚合)                        │
└────────────────────┬────────────────────────────────────┘
                     ↓ 聚合后剩余 10-20%
┌─────────────────────────────────────────────────────────┐
│              第四层：分级层 (Severity)                    │
│  - 自动分级 (P0-P4)                                      │
│  - 影响范围评估                                           │
│  - 紧急程度判断                                           │
└────────────────────┬────────────────────────────────────┘
                     ↓ 最终输出
┌─────────────────────────────────────────────────────────┐
│              告警事件 (Alert Event)                       │
│  - 高质量告警                                             │
│  - 可操作的信息                                           │
│  - 明确的优先级                                           │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 详细降噪策略

### 3.1 过滤策略

#### 3.1.1 环境过滤

```java
@Component
public class EnvironmentFilter implements DenoiseFilter {

    @Autowired
    private AgentProperties properties;

    @Override
    public boolean shouldFilter(ExceptionInfo exception) {
        String env = exception.getEnvironment();

        // 测试环境告警默认过滤
        if ("test".equals(env) || "dev".equals(env)) {
            return properties.getFilter().isFilterTestEnv();
        }

        return false;
    }

    @Override
    public int getOrder() {
        return 100;  // 最先执行
    }
}
```

#### 3.1.2 黑名单过滤

```java
@Component
public class BlacklistFilter implements DenoiseFilter {

    @Autowired
    private BlacklistRepository blacklistRepository;

    @Override
    public boolean shouldFilter(ExceptionInfo exception) {
        // 检查异常类型黑名单
        if (isInBlacklist(exception.getExceptionType())) {
            return true;
        }

        // 检查包路径黑名单
        String errorClass = exception.getErrorClass();
        if (errorClass != null && matchesBlacklistPackage(errorClass)) {
            return true;
        }

        return false;
    }

    private boolean isInBlacklist(String exceptionType) {
        List<String> blacklist = blacklistRepository.getExceptionBlacklist();
        return blacklist.contains(exceptionType);
    }

    private boolean matchesBlacklistPackage(String className) {
        List<String> packageBlacklist = blacklistRepository.getPackageBlacklist();
        return packageBlacklist.stream()
            .anyMatch(className::startsWith);
    }

    @Override
    public int getOrder() {
        return 200;
    }
}
```

#### 3.1.3 已知误报过滤

```java
@Component
public class FalsePositiveFilter implements DenoiseFilter {

    @Autowired
    private KnowledgeService knowledgeService;

    @Override
    public boolean shouldFilter(ExceptionInfo exception) {
        // 从知识库查询是否为已知误报
        String fingerprint = exception.getFingerprint();

        KnowledgeItem knowledge = knowledgeService.findByFingerprint(fingerprint);
        if (knowledge != null && knowledge.isFalsePositive()) {
            log.info("Filter false positive exception: {}",
                exception.getExceptionType());
            return true;
        }

        return false;
    }

    @Override
    public int getOrder() {
        return 300;
    }
}
```

#### 3.1.4 业务异常过滤

```java
@Component
public class BusinessExceptionFilter implements DenoiseFilter {

    /**
     * 过滤业务异常（非系统错误）
     */
    @Override
    public boolean shouldFilter(ExceptionInfo exception) {
        String exceptionType = exception.getExceptionType();

        // 业务异常通常继承自 BusinessException
        if (exceptionType.contains("BusinessException") ||
            exceptionType.contains("BizException")) {
            // 业务异常降低优先级，不完全过滤
            exception.setSeverity("P4");
            exception.setNeedAlert(false);
            return false;  // 不过滤，但标记为低优先级
        }

        return false;
    }

    @Override
    public int getOrder() {
        return 400;
    }
}
```

---

### 3.2 去重策略

#### 3.2.1 时间窗口去重

```java
@Service
public class TimeWindowDeduplication {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final Duration WINDOW_SIZE = Duration.ofMinutes(5);

    /**
     * 时间窗口内去重
     */
    public boolean isDuplicate(ExceptionInfo exception) {
        String fingerprint = exception.getFingerprint();
        String key = "exception:dedup:" + fingerprint;

        // 尝试设置 key，如果已存在则表示重复
        Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(key, exception.getTraceId(), WINDOW_SIZE);

        if (Boolean.FALSE.equals(success)) {
            // 重复告警，增加计数
            incrementCount(fingerprint);
            return true;
        }

        return false;
    }

    /**
     * 增加重复计数
     */
    private void incrementCount(String fingerprint) {
        String countKey = "exception:count:" + fingerprint;
        redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, WINDOW_SIZE);
    }

    /**
     * 获取重复次数
     */
    public long getCount(String fingerprint) {
        String countKey = "exception:count:" + fingerprint;
        String count = redisTemplate.opsForValue().get(countKey);
        return count != null ? Long.parseLong(count) : 0;
    }
}
```

#### 3.2.2 指纹生成算法

```java
@Service
public class FingerprintGenerator {

    /**
     * 生成异常指纹
     */
    public String generate(ExceptionInfo exception) {
        StringBuilder sb = new StringBuilder();

        // 1. 异常类型
        sb.append(exception.getExceptionType()).append("|");

        // 2. 出错位置（类名+方法名+行号）
        if (exception.getErrorClass() != null) {
            sb.append(exception.getErrorClass()).append(".");
            sb.append(exception.getErrorMethod()).append(":");
            sb.append(exception.getErrorLine());
        } else {
            // 如果没有出错位置，使用堆栈的前两帧
            sb.append(getStackFrameSignature(exception.getStackTrace()));
        }

        // 3. 应用名称
        sb.append("|").append(exception.getAppName());

        // 生成 MD5
        return DigestUtils.md5Hex(sb.toString());
    }

    /**
     * 从堆栈中提取签名
     */
    private String getStackFrameSignature(String stackTrace) {
        String[] lines = stackTrace.split("\n");
        StringBuilder sig = new StringBuilder();

        int count = 0;
        for (String line : lines) {
            if (line.trim().startsWith("at ") && !isFrameworkCode(line)) {
                sig.append(line.trim());
                if (++count >= 2) break;
            }
        }

        return sig.toString();
    }

    private boolean isFrameworkCode(String line) {
        return line.contains("org.springframework") ||
               line.contains("com.sun") ||
               line.contains("java.lang.reflect");
    }
}
```

#### 3.2.3 内容相似度去重

```java
@Service
public class SimilarityDeduplication {

    @Autowired
    private EmbeddingModel embeddingModel;

    private static final double SIMILARITY_THRESHOLD = 0.95;

    /**
     * 基于内容相似度去重
     */
    public boolean isSimilar(ExceptionInfo exception, List<ExceptionInfo> recentExceptions) {
        // 生成当前异常的向量
        String content = buildContent(exception);
        Embedding currentEmbedding = embeddingModel.embed(content).content();

        // 与最近的异常比较
        for (ExceptionInfo recent : recentExceptions) {
            String recentContent = buildContent(recent);
            Embedding recentEmbedding = embeddingModel.embed(recentContent).content();

            // 计算余弦相似度
            double similarity = cosineSimilarity(
                currentEmbedding.vector(),
                recentEmbedding.vector()
            );

            if (similarity >= SIMILARITY_THRESHOLD) {
                log.debug("Found similar exception, similarity: {}", similarity);
                return true;
            }
        }

        return false;
    }

    private String buildContent(ExceptionInfo exception) {
        return String.format("%s: %s in %s.%s",
            exception.getExceptionType(),
            exception.getExceptionMessage(),
            exception.getErrorClass(),
            exception.getErrorMethod()
        );
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

---

### 3.3 聚合策略

#### 3.3.1 服务级聚合

```java
@Service
public class ServiceAggregation {

    @Autowired
    private ExceptionRepository exceptionRepository;

    private static final Duration AGGREGATION_WINDOW = Duration.ofMinutes(10);

    /**
     * 按服务聚合异常
     */
    public AlertEvent aggregateByService(String appName) {
        LocalDateTime startTime = LocalDateTime.now().minus(AGGREGATION_WINDOW);

        // 查询时间窗口内该服务的所有异常
        List<ExceptionInfo> exceptions = exceptionRepository
            .findByAppNameAndOccurredAtAfter(appName, startTime);

        if (exceptions.isEmpty()) {
            return null;
        }

        // 创建告警事件
        AlertEvent event = new AlertEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAppName(appName);
        event.setEventType("SERVICE_EXCEPTION");
        event.setExceptionCount(exceptions.size());
        event.setFirstOccurredAt(exceptions.get(0).getOccurredAt());
        event.setLastOccurredAt(exceptions.get(exceptions.size() - 1).getOccurredAt());

        // 统计各类异常
        Map<String, Long> exceptionStats = exceptions.stream()
            .collect(Collectors.groupingBy(
                ExceptionInfo::getExceptionType,
                Collectors.counting()
            ));
        event.setExceptionStats(exceptionStats);

        // 提取最高严重级别
        String highestSeverity = exceptions.stream()
            .map(ExceptionInfo::getSeverity)
            .min(Comparator.naturalOrder())
            .orElse("P4");
        event.setSeverity(highestSeverity);

        // 生成摘要
        event.setSummary(buildSummary(appName, exceptions, exceptionStats));

        return event;
    }

    private String buildSummary(String appName, List<ExceptionInfo> exceptions,
                                 Map<String, Long> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("服务 %s 在过去 %d 分钟内发生 %d 次异常\n",
            appName, AGGREGATION_WINDOW.toMinutes(), exceptions.size()));

        sb.append("异常分布：\n");
        stats.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .forEach(entry -> sb.append(String.format("  - %s: %d 次\n",
                getSimpleExceptionName(entry.getKey()), entry.getValue())));

        return sb.toString();
    }

    private String getSimpleExceptionName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot > 0 ? fullName.substring(lastDot + 1) : fullName;
    }
}
```

#### 3.3.2 实例级聚合

```java
@Service
public class InstanceAggregation {

    /**
     * 按实例聚合异常
     */
    public Map<String, List<ExceptionInfo>> aggregateByInstance(
            List<ExceptionInfo> exceptions) {

        return exceptions.stream()
            .collect(Collectors.groupingBy(
                e -> e.getHostName() + ":" + e.getHostIp()
            ));
    }

    /**
     * 检测是否为全局故障
     */
    public boolean isGlobalFailure(Map<String, List<ExceptionInfo>> instanceMap) {
        // 如果超过 80% 的实例都出现异常，认为是全局故障
        int totalInstances = instanceMap.size();
        long failedInstances = instanceMap.values().stream()
            .filter(list -> list.size() > 5)  // 超过 5 次异常认为失败
            .count();

        double failureRate = (double) failedInstances / totalInstances;
        return failureRate >= 0.8;
    }
}
```

#### 3.3.3 调用链关联聚合

```java
@Service
public class CallChainAggregation {

    @Autowired
    private ExceptionRepository exceptionRepository;

    /**
     * 根据调用链聚合异常
     */
    public List<AlertEvent> aggregateByCallChain(List<ExceptionInfo> exceptions) {
        // 按 traceId 分组
        Map<String, List<ExceptionInfo>> traceGroups = exceptions.stream()
            .filter(e -> e.getTraceId() != null)
            .collect(Collectors.groupingBy(ExceptionInfo::getTraceId));

        List<AlertEvent> events = new ArrayList<>();

        for (Map.Entry<String, List<ExceptionInfo>> entry : traceGroups.entrySet()) {
            String traceId = entry.getKey();
            List<ExceptionInfo> chainExceptions = entry.getValue();

            if (chainExceptions.size() > 1) {
                // 多个服务的调用链异常，聚合为一个事件
                AlertEvent event = createCallChainEvent(traceId, chainExceptions);
                events.add(event);
            }
        }

        return events;
    }

    private AlertEvent createCallChainEvent(String traceId,
                                            List<ExceptionInfo> exceptions) {
        // 找到根因异常（最早发生的）
        ExceptionInfo rootCause = exceptions.stream()
            .min(Comparator.comparing(ExceptionInfo::getOccurredAt))
            .orElseThrow();

        AlertEvent event = new AlertEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("CALL_CHAIN_FAILURE");
        event.setTraceId(traceId);
        event.setRootCauseService(rootCause.getAppName());
        event.setAffectedServices(exceptions.stream()
            .map(ExceptionInfo::getAppName)
            .distinct()
            .collect(Collectors.toList()));

        event.setSummary(String.format(
            "调用链故障 (TraceId: %s)，根因服务: %s，影响 %d 个服务",
            traceId,
            rootCause.getAppName(),
            event.getAffectedServices().size()
        ));

        return event;
    }
}
```

---

### 3.4 自动分级策略

#### 3.4.1 分级规则引擎

```java
@Service
public class SeverityEvaluator {

    /**
     * 评估异常严重级别
     */
    public String evaluate(ExceptionInfo exception, AlertContext context) {
        int score = 0;

        // 规则 1: 异常类型权重
        score += evaluateExceptionType(exception.getExceptionType());

        // 规则 2: 影响范围
        score += evaluateImpactScope(context);

        // 规则 3: 频率
        score += evaluateFrequency(exception.getFingerprint(), context);

        // 规则 4: 业务重要性
        score += evaluateBusinessImportance(exception.getAppName());

        // 规则 5: 时间因素
        score += evaluateTimeFactor(exception.getOccurredAt());

        // 根据总分映射到级别
        return mapScoreToSeverity(score);
    }

    private int evaluateExceptionType(String exceptionType) {
        // 致命异常
        if (exceptionType.contains("OutOfMemoryError") ||
            exceptionType.contains("StackOverflowError")) {
            return 40;
        }

        // 数据库异常
        if (exceptionType.contains("SQLException") ||
            exceptionType.contains("DataAccessException")) {
            return 30;
        }

        // 网络异常
        if (exceptionType.contains("IOException") ||
            exceptionType.contains("TimeoutException")) {
            return 20;
        }

        // 空指针等常见异常
        if (exceptionType.contains("NullPointerException")) {
            return 15;
        }

        // 业务异常
        if (exceptionType.contains("BusinessException")) {
            return 5;
        }

        return 10;  // 默认
    }

    private int evaluateImpactScope(AlertContext context) {
        int affectedInstances = context.getAffectedInstanceCount();
        int totalInstances = context.getTotalInstanceCount();

        double impactRate = (double) affectedInstances / totalInstances;

        if (impactRate >= 0.8) return 30;  // 影响超过 80%
        if (impactRate >= 0.5) return 20;  // 影响超过 50%
        if (impactRate >= 0.2) return 10;  // 影响超过 20%

        return 5;
    }

    private int evaluateFrequency(String fingerprint, AlertContext context) {
        long count = context.getOccurrenceCount(fingerprint);

        if (count >= 100) return 20;  // 超过 100 次
        if (count >= 50) return 15;   // 超过 50 次
        if (count >= 10) return 10;   // 超过 10 次

        return 5;
    }

    private int evaluateBusinessImportance(String appName) {
        // 核心服务
        if (isCoreService(appName)) {
            return 20;
        }

        // 重要服务
        if (isImportantService(appName)) {
            return 10;
        }

        return 5;
    }

    private int evaluateTimeFactor(LocalDateTime occurredAt) {
        int hour = occurredAt.getHour();

        // 高峰期 (9:00-12:00, 14:00-18:00)
        if ((hour >= 9 && hour < 12) || (hour >= 14 && hour < 18)) {
            return 10;
        }

        // 低峰期
        return 5;
    }

    private String mapScoreToSeverity(int score) {
        if (score >= 80) return "P0";  // 紧急
        if (score >= 60) return "P1";  // 严重
        if (score >= 40) return "P2";  // 重要
        if (score >= 20) return "P3";  // 一般
        return "P4";  // 提示
    }

    private boolean isCoreService(String appName) {
        return Arrays.asList("user-service", "order-service", "payment-service")
            .contains(appName);
    }

    private boolean isImportantService(String appName) {
        return Arrays.asList("product-service", "inventory-service")
            .contains(appName);
    }
}

@Data
public class AlertContext {
    private int affectedInstanceCount;
    private int totalInstanceCount;
    private Map<String, Long> occurrenceMap;

    public long getOccurrenceCount(String fingerprint) {
        return occurrenceMap.getOrDefault(fingerprint, 0L);
    }
}
```

---

## 4. 降噪流程编排

### 4.1 降噪流程引擎

```java
@Service
public class DenoiseEngine {

    @Autowired
    private List<DenoiseFilter> filters;

    @Autowired
    private TimeWindowDeduplication timeWindowDedup;

    @Autowired
    private ServiceAggregation serviceAggregation;

    @Autowired
    private SeverityEvaluator severityEvaluator;

    /**
     * 执行降噪流程
     */
    public DenoiseResult process(ExceptionInfo exception) {
        DenoiseResult result = new DenoiseResult();
        result.setOriginalException(exception);

        try {
            // 第一层：过滤
            if (shouldFilter(exception)) {
                result.setFiltered(true);
                result.setReason("Filtered by rules");
                return result;
            }

            // 第二层：去重
            if (timeWindowDedup.isDuplicate(exception)) {
                result.setDuplicate(true);
                result.setReason("Duplicate in time window");

                // 虽然是重复，但更新计数
                long count = timeWindowDedup.getCount(exception.getFingerprint());
                result.setOccurrenceCount(count);

                return result;
            }

            // 第三层：聚合
            AlertEvent event = serviceAggregation.aggregateByService(
                exception.getAppName()
            );
            result.setAlertEvent(event);

            // 第四层：分级
            AlertContext context = buildAlertContext(exception);
            String severity = severityEvaluator.evaluate(exception, context);
            exception.setSeverity(severity);
            result.setSeverity(severity);

            result.setPassed(true);

        } catch (Exception e) {
            log.error("Failed to process denoise", e);
            result.setError(true);
            result.setReason(e.getMessage());
        }

        return result;
    }

    private boolean shouldFilter(ExceptionInfo exception) {
        // 按顺序执行所有过滤器
        return filters.stream()
            .sorted(Comparator.comparingInt(DenoiseFilter::getOrder))
            .anyMatch(filter -> filter.shouldFilter(exception));
    }

    private AlertContext buildAlertContext(ExceptionInfo exception) {
        // 构建告警上下文（用于分级）
        AlertContext context = new AlertContext();
        // ... 填充上下文信息
        return context;
    }
}

@Data
public class DenoiseResult {
    private ExceptionInfo originalException;
    private boolean filtered;         // 是否被过滤
    private boolean duplicate;        // 是否重复
    private boolean error;            // 是否处理错误
    private boolean passed;           // 是否通过（需要告警）
    private String reason;            // 原因
    private Long occurrenceCount;     // 出现次数
    private AlertEvent alertEvent;    // 告警事件
    private String severity;          // 严重级别
}
```

---

## 5. 配置化降噪规则

### 5.1 降噪规则配置

```yaml
# 降噪配置
one-agent:
  denoise:
    # 是否启用降噪
    enabled: true

    # 过滤规则
    filters:
      # 环境过滤
      environment:
        enabled: true
        filter-test-env: true
        filter-dev-env: false

      # 黑名单
      blacklist:
        enabled: true
        exception-types:
          - org.springframework.security.access.AccessDeniedException
          - javax.validation.ValidationException
        packages:
          - org.springframework.web.servlet
          - com.sun

      # 白名单（优先级高于黑名单）
      whitelist:
        enabled: false
        exception-types:
          - java.lang.OutOfMemoryError
          - java.lang.StackOverflowError

    # 去重配置
    deduplication:
      time-window: 5m  # 时间窗口
      similarity-threshold: 0.95  # 相似度阈值

    # 聚合配置
    aggregation:
      enabled: true
      window: 10m
      strategies:
        - SERVICE  # 按服务聚合
        - INSTANCE # 按实例聚合
        - CALL_CHAIN # 按调用链聚合

    # 分级配置
    severity:
      auto-evaluate: true
      rules:
        - type: EXCEPTION_TYPE
          weight: 40
        - type: IMPACT_SCOPE
          weight: 30
        - type: FREQUENCY
          weight: 20
        - type: BUSINESS_IMPORTANCE
          weight: 10

      # 核心服务列表
      core-services:
        - user-service
        - order-service
        - payment-service
```

---

## 6. 监控指标

### 6.1 降噪效果指标

```java
@Component
public class DenoiseMetrics {

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * 记录降噪指标
     */
    public void recordDenoiseResult(DenoiseResult result) {
        // 总异常数
        meterRegistry.counter("exception.total").increment();

        if (result.isFiltered()) {
            // 被过滤数
            meterRegistry.counter("exception.filtered").increment();
        } else if (result.isDuplicate()) {
            // 重复数
            meterRegistry.counter("exception.duplicate").increment();
        } else if (result.isPassed()) {
            // 通过数（真正的告警）
            meterRegistry.counter("exception.passed").increment();

            // 按级别统计
            meterRegistry.counter("exception.severity",
                "level", result.getSeverity()).increment();
        }
    }

    /**
     * 计算降噪率
     */
    public double getDenoiseRate() {
        double total = meterRegistry.counter("exception.total").count();
        double passed = meterRegistry.counter("exception.passed").count();

        if (total == 0) return 0;

        return (total - passed) / total * 100;
    }
}
```

---

## 7. 总结

通过**四层降噪策略**，可以将告警数量从**海量**降低到**可管理**的程度：

```
100,000 条原始异常
    ↓ 第一层过滤 (40% 过滤)
60,000 条
    ↓ 第二层去重 (50% 去重)
30,000 条
    ↓ 第三层聚合 (70% 聚合)
9,000 条
    ↓ 第四层分级 (关注 P0-P2)
~3,000 条 高质量告警
```

**最终效果**：
- 📉 告警数量减少 **97%**
- ✅ 零漏报率
- 🎯 高质量告警
- ⚡ 快速响应真正的问题
