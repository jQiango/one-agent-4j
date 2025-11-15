# AI 模型降噪测试指南

本指南帮助你快速测试和评估 AI 模型在异常降噪场景下的效果。

---

## 📋 测试准备

### 1. 环境要求

- ✅ Java 17+
- ✅ Maven 3.6+
- ✅ MySQL 8.0+ (已运行且导入了初始化脚本)
- ✅ AI API Key (OpenAI 兼容的接口)

### 2. 配置检查

确保 `application.properties` 或 `application-test.properties` 中配置正确：

```properties
# 启用 AI 降噪
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

### 3. 环境变量设置（可选）

```bash
# Windows CMD
set OPENAI_API_KEY=your-api-key-here

# Windows PowerShell
$env:OPENAI_API_KEY="your-api-key-here"

# Linux/Mac
export OPENAI_API_KEY=your-api-key-here
```

---

## 🧪 测试类说明

项目提供了两个测试类：

### 测试类 1: `AiDenoiseModelTest.java` (完整测试套件)

**位置**: `src/test/java/com/all/in/one/agent/AiDenoiseModelTest.java`

**包含 8 个测试场景**：

1. ✅ **testCase1_IdenticalException** - 完全相同的异常识别
2. ✅ **testCase2_SimilarException** - 相似异常的相似度判断
3. ✅ **testCase3_DifferentException** - 完全不同的异常识别
4. ✅ **testCase4_FrequentException** - 频繁异常的处理策略
5. ✅ **testCase5_SeverityAssessment** - 严重级别评估能力
6. ✅ **testCase6_CachePerformance** - 缓存性能测试
7. ✅ **testCase7_BusinessContextUnderstanding** - 业务上下文理解
8. ✅ **testCase8_OverallStatistics** - 整体统计信息

**适用场景**：全面评估 AI 模型的各项能力

**运行方式**：
```bash
# 运行所有测试
mvn test -Dtest=AiDenoiseModelTest

# 运行单个测试
mvn test -Dtest=AiDenoiseModelTest#testCase1_IdenticalException
```

---

### 测试类 2: `AiDenoiseQuickTest.java` (快速验证)

**位置**: `src/test/java/com/all/in/one/agent/AiDenoiseQuickTest.java`

**包含 3 个快速测试**：

1. ✅ **quickTest_CustomException** - 自定义异常场景（可修改参数）
2. ✅ **quickTest_BatchSimilarExceptions** - 批量相似异常测试
3. ✅ **quickTest_CompareSeverity** - 严重级别对比测试

**适用场景**：快速验证单个场景，方便调试和迭代

**运行方式**：
```bash
# 运行所有快速测试
mvn test -Dtest=AiDenoiseQuickTest

# 运行单个测试
mvn test -Dtest=AiDenoiseQuickTest#quickTest_CustomException
```

---

## 🚀 快速开始

### 方式 1: 使用 IDEA 运行（推荐）

1. 在 IDEA 中打开项目
2. 找到测试类 `AiDenoiseQuickTest.java`
3. 右键点击 `quickTest_CustomException` 方法
4. 选择 "Run 'quickTest_CustomException()'"
5. 查看控制台输出

### 方式 2: 使用 Maven 命令行

```bash
# 1. 进入项目目录
cd F:\work\ai\one-agent-4j

# 2. 运行快速测试
mvn test -Dtest=AiDenoiseQuickTest#quickTest_CustomException

# 3. 查看输出结果
```

### 方式 3: 自定义测试场景

编辑 `AiDenoiseQuickTest.java` 中的 `quickTest_CustomException` 方法：

```java
@Test
void quickTest_CustomException() {
    // ========== 修改这里的参数 ==========

    String exceptionType = "java.lang.NullPointerException";
    String exceptionMessage = "你的异常消息";
    String errorLocation = "com.example.YourService.yourMethod:100";
    String requestUri = "/api/your/endpoint";
    String environment = "prod";  // dev/test/uat/prod

    // ====================================

    // ... 测试代码会自动执行
}
```

---

## 📊 测试输出示例

### 正常输出示例

```
================================================================================
🚀 AI 降噪快速测试
================================================================================

📋 测试异常信息:
  类型: java.lang.NullPointerException
  消息: Cannot invoke "User.getName()" because "user" is null
  位置: com.example.UserService.getUserInfo:100
  接口: /api/user/info
  环境: prod
  指纹: abc123def456...

⏳ 正在调用 AI 进行分析...

================================================================================
🤖 AI 分析结果
================================================================================
✅ 决策: 需要报警

📊 详细信息:
  • 是否重复: 否
  • 相似度: 0.0
  • 建议严重级别: P2

💡 判断原因:
  这是一个空指针异常，发生在 UserService 中，在生产环境需要报警

🔧 处理建议:
  建议检查 getUserInfo 方法中的空指针处理，确保 user 对象不为 null

⚡ 性能指标:
  • 响应耗时: 1523ms
  • 性能评价: 良好（AI调用）

================================================================================
📈 累计统计信息
================================================================================
总体指标:
  • 总检查次数: 1
  • 缓存命中: 0 次 (0.0%)
  • AI调用: 1 次
  • 被过滤: 0 次 (0.0%)

缓存信息:
  • 当前缓存大小: 1
  • 缓存驱逐次数: 0

💰 成本估算:
  • API调用次数: 1
  • 预估成本: ¥0.001 (假设 ¥0.001/次)
================================================================================
```

---

## 🎯 评估指标

### 1. 准确性指标

| 指标 | 说明 | 理想值 |
|------|------|--------|
| **重复识别准确率** | 能正确识别重复异常的比例 | > 95% |
| **相似度判断** | 相似异常的相似度分数 | 0.7 - 0.95 |
| **严重级别准确性** | AI 建议的级别与实际相符 | > 90% |

### 2. 性能指标

| 指标 | 说明 | 理想值 |
|------|------|--------|
| **首次调用耗时** | AI 调用的响应时间 | < 3000ms |
| **缓存命中耗时** | 从缓存获取的响应时间 | < 50ms |
| **缓存命中率** | 缓存命中的比例 | > 80% |

### 3. 成本指标

| 指标 | 说明 | 参考值 |
|------|------|--------|
| **API 调用次数** | 实际调用 AI 的次数 | 越少越好 |
| **过滤率** | 被 AI 过滤的异常比例 | 70% - 80% |
| **预估成本** | 基于调用次数的成本估算 | < ¥0.01/小时 |

---

## 🔍 测试场景详解

### 场景 1: 重复异常识别

**测试目的**: 验证 AI 能否识别完全相同的异常

**预期结果**:
- 首次异常: `shouldAlert = true`
- 重复异常: `shouldAlert = false`, `isDuplicate = true`

**验证方法**:
```java
// 运行测试
mvn test -Dtest=AiDenoiseModelTest#testCase1_IdenticalException

// 查看日志中的 "isDuplicate" 字段
```

---

### 场景 2: 相似度判断

**测试目的**: 验证 AI 能否判断相似但不完全相同的异常

**预期结果**:
- `similarityScore > 0.7`: 高度相似
- `isDuplicate = true`: 可能判定为重复
- 给出合理的判断原因

**验证方法**:
```java
mvn test -Dtest=AiDenoiseModelTest#testCase2_SimilarException
```

---

### 场景 3: 严重级别评估

**测试目的**: 验证 AI 对不同异常类型的严重程度判断

**预期结果**:
- `OutOfMemoryError` → `P0`
- `SQLException` → `P0/P1`
- `TimeoutException` → `P1/P2`
- `NullPointerException` → `P2/P3`
- `IllegalArgumentException` → `P3/P4`

**验证方法**:
```java
mvn test -Dtest=AiDenoiseModelTest#testCase5_SeverityAssessment
```

---

### 场景 4: 缓存性能

**测试目的**: 验证缓存机制的有效性

**预期结果**:
- 首次调用: 1000-3000ms
- 缓存命中: < 50ms
- 性能提升: > 20倍

**验证方法**:
```java
mvn test -Dtest=AiDenoiseModelTest#testCase6_CachePerformance
```

---

## 🐛 常见问题

### Q1: 测试报错 "AI 降噪服务未启用"

**原因**: 配置未正确启用

**解决方案**:
```properties
# 检查 application.properties
one-agent.ai-denoise.enabled=true
langchain4j.open-ai.chat-model.api-key=your-api-key
```

---

### Q2: 测试超时或无响应

**原因**:
1. API Key 无效
2. 网络连接问题
3. API 限流

**解决方案**:
```bash
# 1. 验证 API Key
curl -X POST https://api.siliconflow.cn/v1/chat/completions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"deepseek-ai/DeepSeek-V3","messages":[{"role":"user","content":"test"}]}'

# 2. 检查网络
ping api.siliconflow.cn

# 3. 增加超时时间
langchain4j.open-ai.chat-model.timeout=120
```

---

### Q3: AI 返回的严重级别不准确

**原因**: Prompt 可能需要优化

**解决方案**:
1. 查看 `src/main/resources/prompts/denoise-prompt-template.txt`
2. 根据实际情况调整判断标准
3. 提供更多上下文信息

---

### Q4: 缓存命中率太低

**原因**:
1. 异常指纹变化太频繁
2. 缓存TTL设置太短

**解决方案**:
```properties
# 增加缓存时间
one-agent.ai-denoise.cache-ttl-minutes=10

# 增加缓存容量
one-agent.ai-denoise.max-cache-size=20000
```

---

## 📈 性能优化建议

### 1. 调整缓存策略

```properties
# 增加缓存时间（适用于稳定环境）
one-agent.ai-denoise.cache-ttl-minutes=10

# 增加缓存容量
one-agent.ai-denoise.max-cache-size=20000
```

### 2. 优化历史查询

```properties
# 减少历史记录查询数量
one-agent.ai-denoise.max-history-records=10

# 缩短回看时间窗口
one-agent.ai-denoise.lookback-minutes=1
```

### 3. 使用更快的模型

```properties
# 使用速度更快的模型（如果可用）
langchain4j.open-ai.chat-model.model-name=gpt-3.5-turbo
```

---

## 🎓 评估报告模板

测试完成后，可以使用以下模板生成评估报告：

```markdown
# AI 模型降噪效果评估报告

## 测试环境
- 测试时间: 2025-01-15
- 模型: DeepSeek-V3
- 测试场景数: 8

## 准确性评估
- ✅ 重复识别准确率: 95%
- ✅ 相似度判断合理性: 良好
- ✅ 严重级别准确性: 90%

## 性能评估
- ⚡ 平均响应时间: 1523ms
- ⚡ 缓存命中率: 85%
- ⚡ 过滤率: 75%

## 成本评估
- 💰 测试期间 API 调用: 50 次
- 💰 预估日常成本: ¥0.50/天
- 💰 月度成本: ¥15/月

## 结论
AI 模型在异常降噪场景下表现良好，建议投入生产使用。

## 优化建议
1. 调整缓存TTL至10分钟
2. 优化Prompt以提高严重级别判断准确性
3. 增加历史异常上下文
```

---

## 📞 技术支持

如有问题，请查看：
- 项目文档: `CLAUDE.md`
- 降噪策略: `DENOISE_STRATEGY.md`
- 测试指南: `TESTING_GUIDE.md`

---

## 🔧 改进版测试：数据库场景测试

### 📝 问题分析

之前的测试类 `AiDenoiseModelTest` 和 `AiDenoiseQuickTest` 存在一个关键问题：

❌ **没有历史数据** - AI 降噪依赖历史告警记录做判断
❌ **无法测试重复识别** - 数据库为空时无法验证重复检测
❌ **相似度判断不完整** - 缺少对比基准
❌ **缓存测试不真实** - 只有第二次调用才体现缓存

### ✅ 改进版测试：`AiDenoiseWithDataTest.java`

**新增特性：**
- 🗄️ **自动写入测试数据** - 模拟真实历史告警记录
- 📊 **数据库查询** - AI 依赖查询历史记录做判断
- 🧹 **自动清理** - 测试后自动清理数据
- 🎯 **场景丰富** - 8 个真实场景

**测试场景：**
1. ✅ **重复识别** - 先写历史，再测试相同异常
2. ✅ **相似度判断** - 同位置不同方法调用的对比
3. ✅ **类型区分** - 空指针 vs SQL 异常
4. ✅ **频繁异常** - 多次重复的处理策略
5. ✅ **严重级别变化** - 测试环境 vs 生产环境
6. ✅ **混合场景** - 多种类型的历史记录
7. ✅ **首次异常** - 清空历史后的测试
8. ✅ **性能统计** - 缓存命中率和成本分析

### 🚀 如何使用改进版测试

```bash
# 1. 准备环境
mysql -u root -p < sql/init.sql

# 2. 运行完整测试套件
mvn test -Dtest=AiDenoiseWithDataTest

# 3. 运行单个场景
mvn test -Dtest=AiDenoiseWithDataTest#testCase1_DuplicateWithHistory
mvn test -Dtest=AiDenoiseWithDataTest#testCase2_SimilarWithHistory
mvn test=Dtest=AiDenoiseWithDataTest#testCase5_SeverityEscalation

# 4. 查看结果
```

### 📊 改进版测试输出示例

```
================================================================================
🧪 测试用例 1: 有历史数据的重复异常识别
================================================================================

📝 步骤1: 写入历史数据（3条）
  ✓ 已插入历史记录 #1: id=123, fingerprint=a1b2c3d4
  ✓ 已插入历史记录 #2: id=124, fingerprint=a1b2c3d4
  ✓ 已插入历史记录 #3: id=125, fingerprint=a1b2c3d4

📝 步骤2: 创建新异常（与历史完全相同）
  ✓ 新异常: Cannot invoke "User.getName()" because "user" is null

🤖 步骤3: 调用 AI 进行判断
⏳ 正在调用 AI 进行分析...

================================================================================
🤖 AI 分析结果
================================================================================
❌ 决策: 过滤（不报警）

📊 详细信息:
  • 是否重复: 是
  • 相似度: 0.95
  • 建议严重级别: P3

💡 判断原因:
  该异常与历史记录#123高度相似（相似度95%），判定为重复告警，建议不重复处理

🔧 处理建议:
  该异常在3分钟内已发生3次，建议汇总处理，避免告警风暴

⚡ 性能指标:
  • 响应耗时: 1245ms
  • 性能评价: 良好（AI调用）

================================================================================

✅ 验证结果:
  • 是否识别为重复: 是 (预期: 是)
  • 是否建议报警: 否 (预期: 否)
  • 相似度分数: 0.95 (预期: > 0.9)
```

### 📈 测试场景覆盖

| 测试方法 | 测试内容 | 验证点 | 适用场景 |
|---------|---------|--------|---------|
| `testCase1_DuplicateWithHistory` | 完全重复异常识别 | `isDuplicate=true`, `相似度>0.9` | 验证基础去重能力 |
| `testCase2_SimilarWithHistory` | 相似异常判断 | `相似度0.6-0.9` | 验证相似度算法 |
| `testCase3_DifferentTypeWithHistory` | 不同类型异常 | `isDuplicate=false` | 验证类型识别 |
| `testCase4_FrequentExceptionWithHistory` | 频繁异常处理 | 包含处理建议 | 验证异常风暴处理 |
| `testCase5_SeverityEscalation` | 严重级别变化 | 环境升级识别 | 验证优先级判断 |
| `testCase6_MixedHistoryScenario` | 混合历史场景 | 模式识别 | 验证复杂场景 |
| `testCase7_FirstExceptionAfterCleanup` | 首次异常测试 | `isDuplicate=false` | 验证边界条件 |
| `testCase8_PerformanceAndStatistics` | 性能统计 | 缓存命中率、成本 | 验证性能指标 |

---

## 🎯 推荐的测试策略

### 阶段1：快速验证（无数据库）
```bash
# 快速验证 AI 服务正常工作
mvn test -Dtest=AiDenoiseQuickTest#quickTest_CustomException
```

### 阶段2：基础功能测试（数据库）
```bash
# 验证重复识别和相似度
mvn test -Dtest=AiDenoiseWithDataTest#testCase1_DuplicateWithHistory
mvn test -Dtest=AiDenoiseWithDataTest#testCase2_SimilarWithHistory
```

### 阶段3：高级场景测试
```bash
# 验证复杂场景
mvn test -Dtest=AiDenoiseWithDataTest#testCase5_SeverityEscalation
mvn test -DenoiseWithDataTest#testCase6_MixedHistoryScenario
```

### 阶段4：完整评估
```bash
# 运行所有测试
mvn test -Dtest=AiDenoiseWithDataTest

# 查看统计信息
mvn test -Dtest=AiDenoiseWithDataTest#testCase8_PerformanceAndStatistics
```

---

## 🔧 测试数据管理

### 自动清理
- 每个测试方法都会记录写入的测试数据ID
- `@AfterAll` 方法会自动清理所有测试数据
- 不影响生产数据库

### 手动清理（如需要）
```java
// 在测试类中
private void cleanupTestData() {
    if (appAlarmRecordMapper != null && !testRecordIds.isEmpty()) {
        for (Long id : testRecordIds) {
            appAlarmRecordMapper.deleteById(id);
        }
        testRecordIds.clear();
    }
}
```

### 检查测试数据
```sql
SELECT id, exception_type, exception_message, created_at
FROM app_alarm_record
WHERE app_name = 'one-agent-4j-test'
ORDER BY created_at DESC;
```

---

## 📊 关键评估指标（改进版）

| 指标 | 说明 | 理想值 | 测试方法 |
|------|------|--------|----------|
| **重复识别准确率** | 正确识别重复异常的比例 | > 95% | `testCase1` |
| **相似度合理性** | 相似异常的分数范围 | 0.7-0.95 | `testCase2` |
| **类型区分准确性** | 不同类型异常的区分 | > 90% | `testCase3` |
| **频繁异常处理** | 合并告警建议 | 有建议 | `testCase4` |
| **缓存命中率** | 数据库查询节省 | > 80% | `testCase8` |

---

**祝测试顺利！** 🎉
