# AI 降噪完整管道集成测试指南

本指南说明如何测试 **One Agent 4J** 从异常捕获到工单生成的完整 AI 降噪管道流程。

---

## 📋 测试概述

### 测试目标

验证异常处理的完整生命周期:

```
Exception Thrown (异常抛出)
    ↓
ExceptionCollector (异常收集器)
    ↓
Layer 0: Ignore List Filter (基础过滤 - 忽略列表)
    ↓
Layer 1: Fingerprint Dedup (指纹去重 - 缓存)
    ↓
Layer 1.5: Rule Engine (规则引擎 - 频率/时间窗口/环境)
    ↓
Layer 2: AI Denoise (AI 智能降噪 - LLM 决策)
    ↓
Persistence (持久化 - app_alarm_record 表)
    ↓
Ticket Generation (工单生成 - app_alarm_ticket 表)
```

---

## 🧪 测试类说明

### 1. **FullPipelineIntegrationTest** (完整管道集成测试) ⭐⭐⭐

**文件位置**: `src/test/java/com/all/in/one/agent/FullPipelineIntegrationTest.java`

**测试内容**:
- ✅ **场景1**: 首次异常 - 完整流程 (通过所有层 → 持久化 → 生成工单)
- ✅ **场景2**: 重复异常 - AI识别去重 (被 Layer 1 或 Layer 2 拦截)
- ✅ **场景3**: 频繁异常 - 规则引擎 (5个相似异常，规则引擎检测频率)
- ✅ **场景4**: 综合统计 (所有层的过滤率、缓存命中率、成本分析)

**运行方式**:
```bash
# 运行所有场景
mvn test -Dtest=FullPipelineIntegrationTest

# 运行单个场景
mvn test -Dtest=FullPipelineIntegrationTest#testScenario1_FirstException_FullPipeline
mvn test -Dtest=FullPipelineIntegrationTest#testScenario2_DuplicateException_FilteredByAI
mvn test -Dtest=FullPipelineIntegrationTest#testScenario3_FrequentExceptions_RuleEngine
mvn test -Dtest=FullPipelineIntegrationTest#testScenario4_OverallStatistics
```

**优点**:
- 🎯 测试真实的异常流转路径
- 📊 自动统计所有层的性能指标
- 🗑️ 自动清理测试数据
- 💰 提供成本分析 (API调用次数 × 单价)

---

### 2. **AiDenoiseWithDataTest** (AI 降噪数据库测试)

**文件位置**: `src/test/java/com/all/in/one/agent/AiDenoiseWithDataTest.java`

**测试内容**:
- 专注测试 **Layer 2 AI 降噪** 的准确性
- 预先写入历史数据到数据库，让 AI 基于真实上下文做判断

**8个测试场景**:
1. 有历史数据的重复识别
2. 相似异常的相似度判断
3. 不同类型异常识别
4. 频繁异常处理 (10条历史记录)
5. 严重级别升级检测 (测试环境 → 生产环境)
6. 混合历史场景 (多种类型异常)
7. 清空历史后的首次异常
8. 性能统计和成本分析

**运行方式**:
```bash
# 运行所有AI测试
mvn test -Dtest=AiDenoiseWithDataTest

# 运行单个场景
mvn test -Dtest=AiDenoiseWithDataTest#testCase1_DuplicateWithHistory
```

---

### 3. **FunnelDenoiseTest** (漏斗降噪功能测试)

**文件位置**: `src/test/java/com/all/in/one/agent/FunnelDenoiseTest.java`

**测试内容**:
- 单独测试 Layer 0, Layer 1, Layer 1.5 的功能
- 测试严重级别计算逻辑

**运行方式**:
```bash
mvn test -Dtest=FunnelDenoiseTest
```

---

## 🚀 快速开始

### 步骤1: 确保环境就绪

#### 1.1 MySQL 数据库

```bash
# 检查 MySQL 是否运行
mysqladmin ping -u root -p

# 初始化数据库
mysql -u root -p < sql/init.sql
```

#### 1.2 AI API 配置

编辑 `src/main/resources/application.properties`:

```properties
# AI 降噪开关（必须启用）
one-agent.ai-denoise.enabled=true

# AI API 配置（必填）
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.base-url=https://api.siliconflow.cn
langchain4j.open-ai.chat-model.model-name=deepseek-ai/DeepSeek-V3

# 数据库配置（必填）
spring.datasource.url=jdbc:mysql://localhost:3306/one_agent
spring.datasource.username=root
spring.datasource.password=your-password
```

#### 1.3 环境变量（可选）

```bash
# Windows CMD
set OPENAI_API_KEY=your-api-key

# Windows PowerShell
$env:OPENAI_API_KEY="your-api-key"

# Linux/Mac
export OPENAI_API_KEY=your-api-key
```

---

### 步骤2: 运行完整管道测试 (推荐从这里开始！)

```bash
# 方式1: 使用 Maven
mvn test -Dtest=FullPipelineIntegrationTest

# 方式2: 使用 Maven Wrapper (推荐)
./mvnw test -Dtest=FullPipelineIntegrationTest           # Linux/Mac
.\mvnw.cmd test -Dtest=FullPipelineIntegrationTest       # Windows
```

---

## 📊 预期输出示例

### 场景1: 首次异常 - 完整流程

```
====================================================================================================
🚀 开始集成测试 - 完整AI降噪管道
====================================================================================================

📋 测试场景 1: 首次异常 - 完整降噪管道
--------------------------------------------------------------------------------
📤 步骤1: 将异常提交给 ExceptionCollector
   异常类型: java.lang.NullPointerException
   异常消息: Cannot invoke "com.example.User.getName()" because "user" is null
   错误位置: com.example.UserService.getUserInfo:100
⏱️  处理耗时: 1234ms

✅ Layer 0 验证: 基础过滤
   总检查: 1, 已过滤: 0, 过滤率: 0.0%

✅ Layer 1 验证: 指纹去重
   总检查: 1, 去重: 0, 缓存大小: 1

✅ Layer 1.5 验证: 规则引擎
   总检查: 1, 规则过滤: 0, 过滤率: 0.0%

✅ Layer 2 验证: AI 智能降噪
   总检查: 1, AI调用: 1, 缓存命中: 0, 已过滤: 0
   缓存命中率: 0.0%, AI过滤率: 0.0%

✅ 持久化验证: 检查数据库记录
   查询到告警记录数: 1
   记录ID: 12345
   异常指纹: a1b2c3d4e5f6...
   错误位置: com.example.UserService:getUserInfo:100
   AI处理: 是
   AI决策: ALERT
   AI原因: 首次出现的空指针异常，建议检查 user 对象的初始化逻辑

✅ 工单验证: 检查自动生成的工单
   查询到工单数: 1
   工单ID: 101
   工单标题: [P2][NullPointerException] Cannot invoke "User.getName()"
   严重级别: P2
   工单状态: PENDING
   发生次数: 1
   预期解决时间: 2025-11-16 13:30:00

================================================================================
✅ 场景1测试完成: 首次异常成功通过完整管道并生成工单
================================================================================
```

---

### 场景2: 重复异常 - AI识别去重

```
📋 测试场景 2: 重复异常 - AI 识别去重
--------------------------------------------------------------------------------
📊 当前告警记录数: 1
📊 当前工单数: 1

📤 步骤1: 提交重复异常
   异常类型: java.lang.NullPointerException
   异常消息: Cannot invoke "com.example.User.getName()" because "user" is null
⏱️  处理耗时: 15ms (应该很快，可能被缓存拦截)

✅ Layer 1 验证: 指纹去重应该生效
   去重过滤: 1 次

✅ Layer 2 验证: AI 降噪统计
   AI过滤: 0 次
   缓存命中: 0 次
   ✅ 指纹去重在 Layer 1 已拦截，无需调用 AI

✅ 持久化验证: 不应该有新记录
   处理前记录数: 1
   处理后记录数: 1

================================================================================
✅ 场景2测试完成: 重复异常被成功识别和过滤
================================================================================
```

---

### 场景4: 综合统计

```
📊 综合统计信息
================================================================================

🔹 Layer 0 - 基础过滤 (Ignore List)
   总检查: 11
   已过滤: 0
   过滤率: 0.0%

🔹 Layer 1 - 指纹去重 (Fingerprint)
   总检查: 11
   去重过滤: 6
   过滤率: 54.5%
   缓存大小: 6
   缓存驱逐: 0

🔹 Layer 1.5 - 规则引擎 (Rule Engine)
   总检查: 5
   规则过滤: 2
   过滤率: 40.0%

🔹 Layer 2 - AI 智能降噪
   总检查: 3
   AI 实际调用: 2
   缓存命中: 1
   AI 过滤: 0
   缓存命中率: 33.3%
   AI 过滤率: 0.0%
   缓存大小: 2

   💰 成本分析:
      API调用成本: ¥0.002
      缓存节省成本: ¥0.001
      总节省率: 33.3%

🔹 持久化统计
   告警记录总数: 3
   工单总数: 2

================================================================================
```

**关键指标解读**:
- **Layer 1 过滤率 54.5%**: 指纹去重拦截了一半以上的重复异常
- **Layer 2 缓存命中率 33.3%**: AI 决策被缓存，节省了 1/3 的 LLM 调用
- **API 调用成本**: 本次测试仅花费 ¥0.002，成本可控

---

## 🎯 测试策略建议

### 阶段1: 快速验证 (2分钟)

```bash
# 只运行场景1，验证基础功能
mvn test -Dtest=FullPipelineIntegrationTest#testScenario1_FirstException_FullPipeline
```

**预期**:
- ✅ 异常被捕获
- ✅ 通过所有层
- ✅ 写入数据库
- ✅ 生成工单

---

### 阶段2: 去重测试 (5分钟)

```bash
# 运行场景1+场景2
mvn test -Dtest=FullPipelineIntegrationTest#testScenario1_FirstException_FullPipeline
mvn test -Dtest=FullPipelineIntegrationTest#testScenario2_DuplicateException_FilteredByAI
```

**预期**:
- ✅ 首次异常生成记录
- ✅ 重复异常被 Layer 1 拦截
- ✅ 缓存生效，响应时间 < 50ms

---

### 阶段3: 频繁异常测试 (8分钟)

```bash
# 运行场景3
mvn test -Dtest=FullPipelineIntegrationTest#testScenario3_FrequentExceptions_RuleEngine
```

**预期**:
- ✅ 规则引擎检测到频率异常
- ✅ AI 建议合并告警
- ✅ 部分异常被规则引擎拦截

---

### 阶段4: 完整评估 (15分钟)

```bash
# 运行所有测试
mvn test -Dtest=FullPipelineIntegrationTest

# 或者运行所有测试类
mvn test
```

**预期**:
- ✅ 所有场景通过
- ✅ 统计数据完整
- ✅ 成本在预期范围内

---

## 📈 关键评估指标

| 层级 | 指标 | 理想值 | 说明 |
|------|------|--------|------|
| **Layer 0** | 过滤率 | 5-10% | 忽略健康检查、心跳等无关异常 |
| **Layer 1** | 过滤率 | 50-60% | 指纹去重，拦截短时间内的重复异常 |
| **Layer 1** | 响应时间 | < 1ms | 基于内存缓存，极快 |
| **Layer 1.5** | 过滤率 | 10-20% | 规则引擎，拦截频繁异常和非工作时间低优先级 |
| **Layer 2** | AI 过滤率 | 20-30% | AI 判断为不需要报警的异常 |
| **Layer 2** | 缓存命中率 | > 80% | 相同异常的AI决策被缓存 |
| **Layer 2** | 首次调用耗时 | 1-3s | 调用 LLM 的时间 |
| **Layer 2** | 缓存命中耗时 | < 50ms | 从 Caffeine 缓存获取 |
| **整体** | 最终过滤率 | 70-80% | 经过4层过滤后，只有20-30%需要人工处理 |
| **成本** | 单个异常成本 | < ¥0.001 | 包含 AI 调用成本 |

---

## 🔧 调试技巧

### 1. 查看详细日志

编辑 `application.properties`:
```properties
# 开启 DEBUG 日志
logging.level.com.all.in.one.agent=DEBUG
logging.level.com.all.in.one.agent.ai=DEBUG
```

### 2. 禁用某一层进行对比

```properties
# 禁用 AI 降噪，对比效果
one-agent.ai-denoise.enabled=false

# 禁用规则引擎
one-agent.rule-engine.frequency-limit.enabled=false

# 禁用指纹去重
one-agent.dedup.enabled=false
```

### 3. 调整缓存参数

```properties
# 增加缓存时间（提高命中率）
one-agent.ai-denoise.cache-ttl-minutes=10

# 增加缓存容量
one-agent.ai-denoise.max-cache-size=20000
```

### 4. 查看数据库记录

```sql
-- 查看最近的告警记录
SELECT id, exception_type, exception_message, ai_processed, ai_similarity_score, created_at
FROM app_alarm_record
ORDER BY created_at DESC
LIMIT 10;

-- 查看生成的工单
SELECT id, title, severity, status, occurrence_count, created_at
FROM app_alarm_ticket
ORDER BY created_at DESC
LIMIT 10;

-- 统计各层过滤效果（需要添加统计字段）
SELECT
    COUNT(*) as total,
    SUM(CASE WHEN ai_processed = 1 THEN 1 ELSE 0 END) as ai_processed_count,
    SUM(CASE WHEN ai_should_alert = 0 THEN 1 ELSE 0 END) as ai_filtered_count
FROM app_alarm_record
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR);
```

---

## 🐛 常见问题

### Q1: 测试提示 "ExceptionCollector 未启用"

**原因**: Spring Bean 未注入

**解决方案**:
```properties
# 检查 application.properties
one-agent.enabled=true
one-agent.capture-config.enable-filter=true
one-agent.capture-config.enable-controller-advice=true
```

---

### Q2: AI 降噪不生效

**原因**: AI 服务未配置或未启用

**解决方案**:
```properties
# 确保以下配置正确
one-agent.ai-denoise.enabled=true
langchain4j.open-ai.chat-model.api-key=your-valid-key
langchain4j.open-ai.chat-model.base-url=https://api.siliconflow.cn
```

---

### Q3: 数据库连接失败

**解决方案**:
```bash
# 1. 检查 MySQL 运行状态
mysqladmin ping -u root -p

# 2. 检查数据库是否存在
mysql -u root -p -e "SHOW DATABASES LIKE 'one_agent';"

# 3. 重新初始化
mysql -u root -p < sql/init.sql
```

---

### Q4: 测试通过但没看到统计数据

**原因**: 日志级别太高

**解决方案**:
```properties
# 降低日志级别
logging.level.com.all.in.one.agent=DEBUG
```

---

### Q5: 重复异常没有被过滤

**可能原因**:
1. 指纹生成逻辑问题
2. 缓存时间太短
3. 异常信息略有不同

**排查步骤**:
```java
// 在测试中打印指纹
String fingerprint = FingerprintGenerator.generate(exceptionType, errorLocation);
log.info("异常指纹: {}", fingerprint);
```

---

## 📚 相关文档

- 📖 **项目架构**: `CLAUDE.md`
- 📖 **降噪策略**: `DENOISE_STRATEGY.md`
- 📖 **AI 模型测试**: `AI_MODEL_TEST_GUIDE.md`
- 📖 **数据库说明**: `sql/README.md`
- 📖 **字段映射**: `sql/FIELD_MAPPING.md`

---

## 🎉 测试成功标志

运行完整测试后，如果看到以下输出，说明系统工作正常：

```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

**关键验证点**:
- ✅ 所有4个场景测试通过
- ✅ Layer 1 过滤率 > 50%
- ✅ AI 缓存命中率 > 30%
- ✅ 数据库有告警记录和工单
- ✅ 成本在预期范围内

---

**祝测试顺利！** 🚀

有任何问题欢迎查看日志或相关文档。
