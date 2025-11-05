# One Agent 4J - 多层漏斗降噪策略

## 概述

本文档描述 One Agent 4J 的多层漏斗降噪模型，通过分层过滤机制，在保证告警质量的同时大幅降低成本和噪音。

## 当前实现架构（3 层漏斗）

### 设计原则
基于"不要过多设计"的原则，我们实现了简化的 3 层漏斗模型，聚焦核心问题：
1. **过滤明显噪音**（第 0 层）
2. **去除重复异常**（第 1 层）
3. **AI 智能判断**（第 2 层，可选）

### 当前架构
```
┌─────────────────────────────────────────────────┐
│ 第 0 层：基础过滤 (Ignore List)                  │  ✅ 已实现
│ - 忽略的异常类型（7 个维度）                     │
│ - 忽略的包路径                                   │
│ - 忽略的错误位置（支持通配符）                   │
│ - 忽略特定 HTTP 状态码                           │
│ 过滤率: ~10%  |  性能: <1ms                      │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│ 第 1 层：指纹去重 (Fingerprint Dedup)            │  ✅ 已实现
│ - 本地缓存（Caffeine）最近 N 分钟的指纹         │
│ - 时间窗口内相同指纹只处理一次                   │
│ - 自动过期，无需人工维护                         │
│ 过滤率: ~50-60%  |  性能: <1ms                   │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│ 第 2 层：AI 智能去噪 (AI Denoise)                │  ✅ 已实现（可选）
│ - LLM 深度分析（DeepSeek-V3）                    │
│ - 提供详细原因、严重级别、处理建议               │
│ - 结合历史异常上下文判断                         │
│ 过滤率: ~70-80%  |  性能: 1-3s                   │
└─────────────────┬───────────────────────────────┘
                  │
                  ▼
            持久化 + 工单生成
```

**关键特点：**
- ✅ 简单实用：3 层漏斗，核心功能完备
- ✅ 默认启用：引入依赖后自动工作
- ✅ 最小配置：只需 2 个必要配置（数据库 + AI Key）
- ✅ 渐进式：第 0/1 层零成本，第 2 层可选
- ✅ 高性能：前两层极快（<1ms），不影响业务

## 各层详细说明

### 第 0 层：基础过滤 (Ignore List)

#### 工作原理
黑名单机制，硬编码过滤规则。

#### 实现方式
```java
// 当前已实现
if (ignoredExceptions.contains(exceptionType)) {
    return; // 直接丢弃
}
if (ignoredPackages.contains(packageName)) {
    return; // 直接丢弃
}
```

#### 配置示例
```properties
# 忽略登录失败异常（业务正常）
one-agent.capture-config.ignored-exceptions=LoginFailedException,ValidationException
# 忽略健康检查的异常
one-agent.capture-config.ignored-packages=com.health.check,com.test
```

#### 适用场景
- 已知的无害异常（如：特定的业务校验异常）
- 第三方库的预期异常
- 测试环境的模拟异常

#### 优缺点
| 优点 | 缺点 |
|------|------|
| 极快，零成本 | 需要人工维护黑名单 |
| 规则明确 | 不够灵活 |
| 已实现 | - |

---

### 第 1 层：指纹去重 (Fingerprint Dedup)

#### 工作原理
基于时间窗口的去重，相同指纹在短时间内只处理一次。

#### 实现方式
```java
// 使用本地缓存（如 Caffeine）
Cache<String, ExceptionMetadata> cache = Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)  // 5分钟过期
    .maximumSize(10000)                     // 最多缓存1万条
    .build();

boolean shouldProcess(String fingerprint) {
    ExceptionMetadata metadata = cache.getIfPresent(fingerprint);
    if (metadata != null) {
        // 已存在，只更新计数
        metadata.incrementCount();
        metadata.setLastOccurredAt(Instant.now());
        return false;  // 不再处理
    } else {
        // 首次出现，记录并处理
        cache.put(fingerprint, new ExceptionMetadata(1, Instant.now()));
        return true;
    }
}
```

#### 数据结构
```java
class ExceptionMetadata {
    private int count;              // 重复次数
    private Instant firstOccurred;  // 首次出现时间
    private Instant lastOccurred;   // 最后出现时间
}
```

#### 配置示例
```properties
# 指纹去重配置
one-agent.denoise.fingerprint.enabled=true
one-agent.denoise.fingerprint.window-minutes=5
one-agent.denoise.fingerprint.max-cache-size=10000
```

#### 工作示例
```
时间      异常                                         处理结果
----------------------------------------------------------------------
11:00:00  NullPointerException at OrderService:123    ✓ 处理（首次）
11:00:01  NullPointerException at OrderService:123    ✗ 过滤（重复）
11:00:02  NullPointerException at OrderService:123    ✗ 过滤（重复）
11:05:01  NullPointerException at OrderService:123    ✓ 处理（缓存过期）
```

#### 改进方案：批量上报
```java
// 可选：不是完全丢弃，而是累积计数后批量上报
@Scheduled(fixedRate = 60000)
void flushDuplicates() {
    for (Map.Entry<String, ExceptionMetadata> entry : cache.asMap().entrySet()) {
        ExceptionMetadata metadata = entry.getValue();
        if (metadata.getCount() > 1) {
            // 上报汇总：这个异常在过去1分钟发生了 N 次
            reportBatch(entry.getKey(), metadata);
        }
    }
    cache.invalidateAll();
}
```

#### 适用场景
- 短时间内大量重复的相同异常（如循环中的异常）
- 批处理任务的批量异常
- 定时任务反复失败

#### 优缺点
| 优点 | 缺点 |
|------|------|
| 极低成本（内存缓存） | 只能识别完全相同的异常 |
| 过滤率高（40-60%） | 内存占用（可通过 max-size 限制） |
| 自动过期，无需人工维护 | - |
| 实现简单 | - |

---

### 第 2 层：规则引擎 (Rule Engine)

#### 工作原理
基于可配置规则的业务逻辑过滤。

#### 架构设计
```java
// 规则链模式
interface DenoiseRule {
    boolean shouldFilter(ExceptionInfo info);
    String getReason();
    int getPriority();  // 优先级
}

class RuleChain {
    private List<DenoiseRule> rules;

    FilterResult evaluate(ExceptionInfo info) {
        // 按优先级排序
        rules.sort(Comparator.comparing(DenoiseRule::getPriority));

        for (DenoiseRule rule : rules) {
            if (rule.shouldFilter(info)) {
                return FilterResult.filtered(rule.getReason());
            }
        }
        return FilterResult.pass();
    }
}
```

#### 规则类型

##### 2.1 频率限制规则
**目的：** 防止异常风暴

```java
class FrequencyLimitRule implements DenoiseRule {
    private Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    @Override
    public boolean shouldFilter(ExceptionInfo info) {
        // 同一指纹 5 分钟内最多处理 10 次
        RateLimiter limiter = limiters.computeIfAbsent(
            info.getFingerprint(),
            k -> RateLimiter.create(10.0 / 300)  // 10次/5分钟 = 0.033次/秒
        );
        return !limiter.tryAcquire();  // 超限则过滤
    }

    @Override
    public String getReason() {
        return "超过频率限制（5分钟内超过10次）";
    }
}
```

**配置：**
```properties
one-agent.denoise.rules.frequency-limit.enabled=true
one-agent.denoise.rules.frequency-limit.max-count=10
one-agent.denoise.rules.frequency-limit.window-minutes=5
```

**场景：** 数据库宕机导致所有请求都失败，避免生成数千个工单

##### 2.2 时间窗口规则
**目的：** 减少非工作时间的噪音告警

```java
class TimeWindowRule implements DenoiseRule {
    @Override
    public boolean shouldFilter(ExceptionInfo info) {
        int hour = LocalTime.now().getHour();

        // 凌晨 2-6 点，非 P0 异常延迟处理
        if (hour >= 2 && hour < 6) {
            String severity = calculateSeverity(info);
            if (!"P0".equals(severity)) {
                return true;  // 过滤，等白天再处理
            }
        }
        return false;
    }

    @Override
    public String getReason() {
        return "非工作时间的低优先级异常";
    }
}
```

**配置：**
```properties
one-agent.denoise.rules.time-window.enabled=true
one-agent.denoise.rules.time-window.quiet-hours=2-6
one-agent.denoise.rules.time-window.allow-severity=P0
```

##### 2.3 环境隔离规则
**目的：** 不同环境不同策略

```java
class EnvironmentRule implements DenoiseRule {
    @Override
    public boolean shouldFilter(ExceptionInfo info) {
        // 测试环境的 P3/P4 异常不生成工单
        if ("test".equals(info.getEnvironment())) {
            String severity = calculateSeverity(info);
            return severity.matches("P[34]");
        }
        return false;
    }

    @Override
    public String getReason() {
        return "测试环境的低优先级异常";
    }
}
```

**配置：**
```properties
one-agent.denoise.rules.environment.test.filter-severity=P3,P4
one-agent.denoise.rules.environment.prod.filter-severity=
```

##### 2.4 白名单规则（临时静默）
**目的：** 已知问题临时忽略

```java
class WhitelistRule implements DenoiseRule {
    @Autowired
    private KnownIssueRepository knownIssueRepo;

    @Override
    public boolean shouldFilter(ExceptionInfo info) {
        List<KnownIssue> activeIssues = knownIssueRepo.findActive();

        for (KnownIssue issue : activeIssues) {
            if (issue.matches(info) && !issue.isExpired()) {
                log.info("匹配已知问题: {} (工单: {})",
                    issue.getDescription(), issue.getJiraTicket());
                return true;
            }
        }
        return false;
    }

    @Override
    public String getReason() {
        return "已知问题，正在修复中";
    }
}
```

**数据模型：**
```java
@Entity
class KnownIssue {
    private String fingerprintPattern;  // 正则表达式匹配
    private LocalDateTime expireAt;     // 过期时间
    private String jiraTicket;          // 关联的工单号
    private String description;         // 问题描述

    boolean matches(ExceptionInfo info) {
        return info.getFingerprint().matches(fingerprintPattern);
    }

    boolean isExpired() {
        return LocalDateTime.now().isAfter(expireAt);
    }
}
```

**管理接口：**
```java
@RestController
@RequestMapping("/api/v1/known-issues")
class KnownIssueController {
    // 添加已知问题（临时静默3天）
    @PostMapping
    KnownIssue addKnownIssue(
        @RequestParam String fingerprintPattern,
        @RequestParam String jiraTicket,
        @RequestParam(defaultValue = "3") int silenceDays
    );

    // 查询活跃的已知问题
    @GetMapping
    List<KnownIssue> listActive();

    // 删除已知问题
    @DeleteMapping("/{id}")
    void remove(@PathVariable Long id);
}
```

#### 适用场景
- 异常风暴防护（频率限制）
- 工作时间优化（时间窗口）
- 环境差异化处理（环境规则）
- 已知问题临时静默（白名单）

#### 优缺点
| 优点 | 缺点 |
|------|------|
| 灵活可配置 | 需要设计规则配置系统 |
| 支持复杂业务逻辑 | 规则冲突需要优先级管理 |
| 规则可动态加载 | 需要管理界面 |
| 过滤率 20-30% | - |

---

### 第 3 层：轻量级 AI 初筛 (Fast AI Filter)

#### 工作原理
使用快速、低成本的 AI 模型做初步判断。

#### 方案对比

| 方案 | 技术 | 成本 | 速度 | 准确率 | 适用场景 |
|------|------|------|------|--------|---------|
| Embedding | text-embedding-3-small | 极低 | 极快 (<50ms) | 85% | 异常量巨大 |
| 小模型 | GPT-3.5-turbo | 低 | 快 (<500ms) | 90% | 成本敏感 |
| 大模型 | DeepSeek-V3 | 高 | 慢 (1-3s) | 95% | 当前使用 |

#### 实现方案 A：Embedding 相似度

```java
@Service
class EmbeddingDenoiseFilter {
    @Autowired
    private EmbeddingModel embeddingModel;  // OpenAI text-embedding-3-small

    @Autowired
    private VectorDatabase vectorDB;  // 如 Milvus, Pinecone

    /**
     * 判断是否可能重复
     * @return true 表示很可能重复，可以过滤
     */
    public boolean isProbableDuplicate(ExceptionInfo newEx) {
        // 1. 生成新异常的 embedding
        String text = String.format("%s %s %s",
            newEx.getExceptionType(),
            newEx.getMessage(),
            newEx.getErrorLocation()
        );
        float[] newVector = embeddingModel.embed(text);

        // 2. 在向量数据库中搜索最近 5 分钟的相似向量
        List<VectorSearchResult> similar = vectorDB.search(
            newVector,
            topK = 5,
            filter = "timestamp > now() - 5m"
        );

        // 3. 计算余弦相似度
        for (VectorSearchResult result : similar) {
            if (result.getScore() > 0.95) {  // 高度相似
                log.info("检测到高度相似异常: fingerprint={}, score={}",
                    result.getFingerprint(), result.getScore());
                return true;
            }
        }

        // 4. 存储新向量（异步）
        vectorDB.insertAsync(newEx.getFingerprint(), newVector, Instant.now());

        return false;
    }
}
```

**成本分析：**
- Embedding API: $0.00002 / 1K tokens
- 假设每个异常 100 tokens = $0.000002 / 次
- 比大模型便宜 **500 倍**

#### 实现方案 B：小模型快速判断

```java
@Service
class FastModelFilter {
    @Autowired
    private ChatModel fastModel;  // GPT-3.5-turbo

    public boolean isProbableDuplicate(ExceptionInfo newEx, List<ExceptionRecord> recent) {
        String prompt = buildFastPrompt(newEx, recent);

        String response = fastModel.generate(prompt, maxTokens = 10);  // 只需要回答 YES/NO

        return "YES".equalsIgnoreCase(response.trim());
    }

    private String buildFastPrompt(ExceptionInfo newEx, List<ExceptionRecord> recent) {
        return String.format("""
            判断新异常是否与历史异常重复。只回答 YES 或 NO。

            新异常: %s at %s

            最近异常:
            %s

            是否重复？
            """,
            newEx.getExceptionType(),
            newEx.getErrorLocation(),
            summarize(recent)
        );
    }
}
```

**成本分析：**
- GPT-3.5-turbo: $0.0005 / 1K tokens (input) + $0.0015 / 1K tokens (output)
- 平均每次调用约 $0.0001
- 比 DeepSeek-V3 便宜约 **10 倍**

#### 配置示例
```properties
# 轻量级 AI 过滤
one-agent.denoise.fast-ai.enabled=false
one-agent.denoise.fast-ai.type=embedding  # embedding 或 small-model
one-agent.denoise.fast-ai.similarity-threshold=0.95

# Embedding 配置
one-agent.denoise.fast-ai.embedding.model=text-embedding-3-small
one-agent.denoise.fast-ai.embedding.vector-db.type=memory  # memory, milvus, pinecone

# 小模型配置
one-agent.denoise.fast-ai.small-model.name=gpt-3.5-turbo
one-agent.denoise.fast-ai.small-model.max-tokens=10
```

#### 适用场景
- 异常量特别大（每天 > 10万条）
- 成本敏感
- 对准确率要求不是极高（85-90% 可接受）

#### 优缺点
| 优点 | 缺点 |
|------|------|
| 成本是大模型的 1/10 ~ 1/500 | 准确率略低于大模型（85-90%） |
| 响应时间快 (<500ms) | 需要维护向量数据库（embedding 方案） |
| 可以过滤 10-20% 明显重复 | 需要额外的基础设施 |

---

### 第 4 层：深度 AI 分析 (Deep AI Analysis)

#### 工作原理
当前已实现的 LLM 深度分析。

#### 实现方式
```java
@Service
class AiDenoiseService {
    @Autowired
    private DenoiseAiService denoiseAiService;  // LangChain4J 接口

    public DenoiseDecision shouldAlert(ExceptionInfo exceptionInfo) {
        // 1. 查询最近 2 分钟的历史异常
        List<ExceptionRecord> recentExceptions = queryRecentExceptions(exceptionInfo);

        // 2. 构建详细的 prompt（从资源文件加载模板）
        String prompt = DenoisePrompt.buildPrompt(exceptionInfo, recentExceptions);

        // 3. 调用大模型（DeepSeek-V3）
        String aiResponse = denoiseAiService.analyzeException(prompt);

        // 4. 解析详细结果
        DenoiseDecision decision = parseAiResponse(aiResponse);

        return decision;
    }
}
```

#### 返回结果
```java
class DenoiseDecision {
    private boolean shouldAlert;        // 是否需要告警
    private boolean isDuplicate;        // 是否重复异常
    private double similarityScore;     // 相似度 0.0-1.0
    private String suggestedSeverity;   // 建议严重级别 P0/P1/P2/P3/P4
    private String reason;              // 详细判断原因
    private List<Long> relatedExceptionIds;  // 相关的历史异常ID
    private String suggestion;          // 给运维人员的处理建议
}
```

#### 特点对比
| 特性 | 深度 AI | 轻量级 AI | 规则引擎 |
|------|---------|-----------|---------|
| 准确率 | 95% | 85-90% | 100% (规则内) |
| 成本 | 高 | 低 | 零 |
| 速度 | 慢 (1-3s) | 快 (<500ms) | 极快 (<10ms) |
| 灵活性 | 最高 | 高 | 低 |
| 可解释性 | 最强 | 弱 | 强 |

#### 优化策略
1. **缓存结果**：相同 fingerprint 的决策短期缓存（5分钟）
2. **批量处理**：如果 LLM 支持批量 API，一次处理多个异常
3. **降级策略**：AI 服务不可用时，使用规则引擎兜底

```java
@Service
class AiDenoiseService {
    @Autowired
    private Cache<String, DenoiseDecision> decisionCache;

    public DenoiseDecision shouldAlert(ExceptionInfo info) {
        // 1. 先查缓存
        DenoiseDecision cached = decisionCache.get(info.getFingerprint());
        if (cached != null) {
            log.debug("使用缓存的 AI 决策: {}", info.getFingerprint());
            return cached;
        }

        // 2. 调用 AI
        try {
            DenoiseDecision decision = callAiService(info);
            decisionCache.put(info.getFingerprint(), decision);
            return decision;
        } catch (Exception e) {
            log.error("AI 服务调用失败，使用降级策略", e);
            return fallbackDecision(info);
        }
    }

    private DenoiseDecision fallbackDecision(ExceptionInfo info) {
        // 降级：默认告警，避免漏报
        return DenoiseDecision.builder()
            .shouldAlert(true)
            .reason("AI 服务不可用，默认告警")
            .build();
    }
}
```

---

## 完整流程示例

假设系统收到 **100 个异常**：

```
100 个异常输入
    ↓
┌─────────────────────────────────────────────┐
│ 第 0 层：基础过滤                            │
│ - 过滤 10 个已知无害异常                     │
│   (健康检查、404、Actuator 等)               │
│ - 剩余 90 个                                 │
│ 耗时: <1ms/个                                │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 第 1 层：指纹去重                            │
│ - 过滤 50 个重复异常（2分钟内重复）         │
│   (相同 fingerprint 已处理)                  │
│ - 剩余 40 个                                 │
│ 耗时: <1ms/个                                │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 第 2 层：AI 智能去噪（可选）                 │
│ - AI 判断过滤 30 个相似/低优先级异常         │
│   (结合历史上下文分析)                       │
│ - 剩余 10 个                                 │
│ 耗时: 1-3s/个                                │
└─────────────────────────────────────────────┘
    ↓
最终生成 10 个工单
```

### 效果对比

#### 场景 1：不启用 AI 去噪（零成本模式）
| 指标 | 不用漏斗 | 使用漏斗（第0+1层） | 提升 |
|------|----------|----------|------|
| 工单数量 | 100 | 40 | **减少 60%** |
| AI 成本 | $0 | $0 | - |
| 平均响应时间 | 50ms | <2ms | **快 25 倍** |
| 人工处理成本 | 高 | 中 | **降低 60%** |

#### 场景 2：启用 AI 去噪（智能模式）
| 指标 | 不用漏斗 | 使用漏斗（第0+1+2层） | 提升 |
|------|----------|----------|------|
| 工单数量 | 100 | 10 | **减少 90%** |
| 大模型调用次数 | 100 | 40 | **减少 60%** |
| AI 成本 | $1.00 | $0.40 | **降低 60%** |
| 平均响应时间 | 3s | 1.2s | **快 2.5 倍** |
| 告警质量 | 低（大量重复） | 高（过滤噪音） | **显著提升** |

**关键优势：**
- 第 0+1 层零成本快速过滤 60% 噪音
- AI 只需处理 40% 的异常，成本和时间大幅降低
- 即使不启用 AI，也能显著减少工单数量

---

## 监控指标

### 漏斗指标
```java
@Component
class DenoiseMetrics {
    @Autowired
    private MeterRegistry registry;

    // 各层过滤计数
    private Counter layer0Filtered;  // 基础过滤
    private Counter layer1Filtered;  // 指纹去重
    private Counter layer2Filtered;  // 规则引擎
    private Counter layer3Filtered;  // 快速 AI
    private Counter layer4Filtered;  // 深度 AI
    private Counter finalAlerts;     // 最终告警

    // 计算漏斗转化率
    public Map<String, Double> getConversionRates() {
        long total = getTotalExceptions();
        return Map.of(
            "layer0_pass_rate", (total - layer0Filtered.count()) / total,
            "layer1_pass_rate", (total - layer1Filtered.count()) / total,
            "layer2_pass_rate", (total - layer2Filtered.count()) / total,
            "layer3_pass_rate", (total - layer3Filtered.count()) / total,
            "layer4_pass_rate", (total - layer4Filtered.count()) / total,
            "final_alert_rate", finalAlerts.count() / total
        );
    }
}
```

### Grafana 监控面板

建议监控的关键指标：

1. **漏斗转化率**
   - 每层的通过率和过滤率
   - 趋势图显示各层效果

2. **成本指标**
   - 大模型调用次数（第4层）
   - 小模型调用次数（第3层）
   - 每日 AI 成本

3. **性能指标**
   - 各层平均耗时
   - P95/P99 耗时
   - 缓存命中率

4. **质量指标**
   - 最终告警数量
   - 工单重复率（人工反馈）
   - 漏报率（人工反馈）

---

## 当前实现状态

### ✅ 已完成（核心功能）

#### 第 0 层：基础过滤（IgnoreListFilter）
- ✅ 支持 7 个过滤维度
- ✅ 支持通配符匹配（如 `*.health`）
- ✅ 默认启用，零配置运行
- ✅ 配置文件: `IgnoreListProperties`

#### 第 1 层：指纹去重（FingerprintDeduplicator）
- ✅ 使用 Caffeine 本地缓存
- ✅ 2 分钟时间窗口（可配置）
- ✅ 自动过期，最大缓存 10000 条
- ✅ 提供统计信息（`getStats()`）
- ✅ 默认启用，零配置运行
- ✅ 配置文件: `FingerprintDedupProperties`

#### 第 2 层：AI 智能去噪（AiDenoiseService）
- ✅ LangChain4J 集成（DeepSeek-V3）
- ✅ 历史上下文分析（2 分钟窗口）
- ✅ 结构化决策输出（severity, reason, suggestion）
- ✅ **AI 决策结果缓存**（Caffeine，5 分钟 TTL）
- ✅ **性能优化**：缓存命中时 <1ms，未命中时 1-3s
- ✅ **统计监控**：AI 调用次数、缓存命中率、过滤率
- ✅ 降级策略（AI 失败时默认告警）
- ✅ 可选启用（`one-agent.ai-denoise.enabled`）

#### 配置和自动装配
- ✅ Spring Boot Auto-Configuration
- ✅ 默认启用所有功能
- ✅ 最小化配置（只需数据库 + AI Key）
- ✅ 配置示例: `application.properties`, `application-minimal.properties`

#### 监控 API
- ✅ 统一漏斗统计 API：`GET /api/v1/denoise/stats`
- ✅ 分层统计查询：`GET /api/v1/denoise/stats/layer{0,1,2}`
- ✅ 重置统计：`POST /api/v1/denoise/stats/reset`
- ✅ 清空缓存：`POST /api/v1/denoise/cache/clear`

### 🎯 预期效果

| 指标 | 目标 | 说明 |
|------|------|------|
| 工单减少 | 60-90% | 不启用 AI: 60%，启用 AI: 90% |
| AI 成本降低 | 60% | 前两层过滤 60%，AI 只处理 40% |
| 响应时间 | <2ms (前2层) | 前两层极快，不影响业务性能 |
| 告警质量 | 显著提升 | 过滤重复和噪音，保留有价值告警 |

### 🔮 后续优化方向（可选）

仅在以下情况考虑：
1. **异常量特别大**（> 10万/天）且前两层过滤不足
2. **AI 成本成为瓶颈** 需要进一步优化
3. **业务需求** 需要更复杂的规则配置

可选方向：
- 规则引擎（频率限制、时间窗口、环境规则）
- 轻量级 AI（Embedding 相似度、小模型）
- 批量上报机制（重复异常汇总）
- 人工反馈闭环（持续优化）

---

## 配置参考

### 最小化配置（推荐）
```properties
# ========== 必需配置 ==========
# 数据库连接
spring.datasource.url=jdbc:mysql://localhost:3306/one_agent
spring.datasource.username=root
spring.datasource.password=your_password

# AI 模型配置（如果启用 AI 去噪）
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.base-url=https://api.siliconflow.cn
langchain4j.open-ai.chat-model.model-name=deepseek-ai/DeepSeek-V3

# ========== 可选配置 ==========
# 第 0 层：基础过滤（可选自定义忽略规则）
one-agent.ignore-list.exception-types=AccessDeniedException,NoHandlerFoundException
one-agent.ignore-list.error-locations=*.health,*.heartbeat
one-agent.ignore-list.http-status-codes=404,401,403

# 第 1 层：指纹去重（可选调整时间窗口）
# one-agent.dedup.time-window-minutes=2

# 第 2 层：AI 智能去噪（可选启用）
one-agent.ai-denoise.enabled=true
# one-agent.ai-denoise.lookback-minutes=2
```

### 零配置模式（最简）
如果你不需要 AI 去噪，只需要前两层过滤：

```properties
# 只需数据库配置，其他全部默认
spring.datasource.url=jdbc:mysql://localhost:3306/one_agent
spring.datasource.username=root
spring.datasource.password=your_password

# 前两层（第 0 层 + 第 1 层）自动启用，零成本过滤 60% 噪音
```

---

## 最佳实践

### 1. 渐进式上线
```
第1周：上线第1层（指纹去重）→ 观察效果
第2周：上线第2层（频率限制）→ 观察效果
第3周：完善规则引擎 → 持续优化
```

### 2. 灰度发布
```properties
# 先在测试环境试验
one-agent.denoise.fingerprint.enabled=true  # test 环境

# 验证无问题后，再上线生产环境
one-agent.denoise.fingerprint.enabled=true  # prod 环境
```

### 3. A/B 测试
```java
// 双写模式：同时运行新旧逻辑，对比效果
if (abTestEnabled) {
    // 旧逻辑：直接 AI
    DenoiseDecision oldDecision = directAiAnalysis(info);

    // 新逻辑：经过漏斗
    DenoiseDecision newDecision = funnelAnalysis(info);

    // 记录差异用于分析
    logDifference(oldDecision, newDecision);

    // 根据配置决定使用哪个
    return useNewLogic ? newDecision : oldDecision;
}
```

### 4. 人工反馈闭环
```java
@RestController
@RequestMapping("/api/v1/denoise-feedback")
class DenoiseFeedbackController {
    // 运维人员标注：这个告警是否有价值
    @PostMapping("/feedback")
    void submitFeedback(
        @RequestParam String fingerprint,
        @RequestParam boolean isUseful,
        @RequestParam String comment
    ) {
        // 记录反馈，用于优化规则和阈值
    }
}
```

---

## 常见问题 (FAQ)

### Q1: 第1层会不会漏掉真实的重复问题？
**A:** 不会。第1层只是在短时间窗口（如5分钟）内去重。如果同一个异常持续发生，说明问题没有解决，首次告警的工单会持续更新 `occurrence_count`。

### Q2: 规则引擎会不会太复杂？
**A:** 从简单开始。MVP 阶段只需要频率限制规则，后续根据实际需求逐步添加。

### Q3: 是否一定要实施第3层（轻量级 AI）？
**A:** 不一定。只有当异常量特别大（> 10万/天）且 AI 成本成为瓶颈时才需要考虑。大多数场景下，前两层 + 深度 AI 就足够了。

### Q4: 如何确保不会漏报重要异常？
**A:** 多重保障：
1. 严重级别 P0 的异常跳过某些过滤规则
2. AI 判断失败时默认告警
3. 定期人工审查被过滤的异常样本

### Q5: 实施后如何评估效果？
**A:** 关注三个核心指标：
1. 告警数量是否显著减少（目标：减少 80-90%）
2. AI 成本是否显著降低（目标：降低 70-80%）
3. 告警质量是否提升（通过人工反馈评估）

---

## 总结

One Agent 4J 的 3 层漏斗降噪策略，通过简单实用的分层过滤机制：

### 核心价值

- ✅ **大幅降低告警噪音**：工单数量减少 60-90%
- ✅ **显著降低 AI 成本**：AI 调用减少 60%，成本降低 60%
- ✅ **提升告警质量**：过滤重复和低价值告警，保留真正需要处理的问题
- ✅ **极快的响应速度**：前两层 <1ms，不影响业务性能
- ✅ **即开即用**：引入依赖后自动启用，最小化配置

### 设计原则

1. **简单实用**：只实现核心必要功能，不过度设计
2. **渐进式**：第 0/1 层零成本，第 2 层可选
3. **默认启用**：Convention over Configuration，无需复杂配置
4. **高性能**：前两层极快，不影响业务

### 使用建议

**零成本模式**（推荐新用户）：
- 只启用第 0+1 层（默认）
- 零 AI 成本，过滤 60% 噪音
- 适合：预算有限、异常量不大

**智能模式**（推荐生产环境）：
- 启用全部 3 层（第 0+1+2）
- AI 成本降低 60%，过滤 90% 噪音
- 适合：告警质量要求高、需要智能分析

**配置极简**：
- 零成本模式：只需数据库配置
- 智能模式：数据库 + AI Key
- 所有其他配置都有合理默认值
