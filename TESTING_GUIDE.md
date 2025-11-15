# One Agent 4J 测试指南

本指南提供完整的测试方案,帮助验证 4 层漏斗降噪系统是否正常工作。

---

## 📋 测试前准备

### 1. 数据库初始化

#### 方式 1: 全新安装

```bash
# 连接 MySQL
mysql -u root -p

# 创建数据库和表
source sql/init.sql
```

#### 方式 2: 已有数据库 (迁移)

如果数据库已存在,需要添加 AI 相关字段:

```bash
# 连接 MySQL
mysql -u root -p

# 执行迁移脚本
source sql/migration_add_ai_fields.sql
```

**迁移脚本说明**:
- 添加 `updated_at` 字段 (自动更新时间戳)
- 添加 `ai_processed` 字段 (AI是否已处理)
- 添加 `ai_decision` 字段 (AI决策结果)
- 添加 `ai_reason` 字段 (AI决策原因)

**验证迁移成功**:

```sql
USE one_agent;
DESC exception_record;
```

应该能看到新增的 4 个字段。

### 2. 配置文件检查

确保 `application.properties` 包含以下配置:

```properties
# 数据库配置 (必需)
spring.datasource.url=jdbc:mysql://localhost:3306/one_agent
spring.datasource.username=root
spring.datasource.password=your_password

# AI API 配置 (Layer 2 需要)
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.base-url=https://api.siliconflow.cn
langchain4j.open-ai.chat-model.model-name=deepseek-ai/DeepSeek-V3

# 应用配置
spring.application.name=one-agent-4j
spring.profiles.active=dev

# 漏斗配置 (可选,都有默认值)
one-agent.ignore-list.enabled=true
one-agent.dedup.enabled=true
one-agent.rule-engine.enabled=true
one-agent.ai-denoise.enabled=false  # 初次测试建议关闭 AI,避免 API 调用
```

---

## 🚀 方法 1: 快速启动测试 (推荐)

### 步骤 1: 启动应用

```bash
# 使用 Maven 启动 (推荐)
mvn spring-boot:run

# 或使用 IDE 启动
# 右键运行 Application.java
```

### 步骤 2: 测试异常捕获

项目已提供测试接口:

```bash
# 测试 NullPointerException
curl http://localhost:8080/test/null-pointer

# 测试 ArrayIndexOutOfBoundsException
curl http://localhost:8080/test/array-index

# 测试 ArithmeticException
curl http://localhost:8080/test/arithmetic

# 测试 RuntimeException
curl http://localhost:8080/test/runtime
```

**预期结果**:
- 返回 500 错误
- 控制台打印异常日志
- 日志中显示 4 层漏斗的处理过程

### 步骤 3: 测试指纹去重 (Layer 1)

**目标**: 验证相同异常在 2 分钟内只记录一次

```bash
# 连续 5 次访问同一接口
for i in {1..5}; do
  echo "请求 $i:"
  curl http://localhost:8080/test/null-pointer
  echo ""
  sleep 1
done
```

**预期结果**:
- 第 1 次: 通过所有层,持久化到数据库
- 第 2-5 次: 被 Layer 1 过滤,日志显示 "Filtered by Layer 1 (duplicate)"

**验证数据库**:

```sql
-- 应该只有 1 条记录
SELECT COUNT(*) FROM exception_record
WHERE exception_type = 'java.lang.NullPointerException';
```

### 步骤 4: 测试频率限制规则 (Layer 1.5)

**目标**: 验证异常风暴保护

```bash
# 快速触发 15 次异常 (超过默认 10 次限制)
for i in {1..15}; do
  curl http://localhost:8080/test/arithmetic &
done
wait
```

**预期结果**:
- 前 10 次: 正常处理
- 第 11-15 次: 被 FrequencyLimitRule 过滤
- 日志: "Filtered by rule engine - rule=FrequencyLimitRule"

### 步骤 5: 查看监控统计

```bash
# 查看完整漏斗统计
curl http://localhost:8080/api/v1/denoise/stats | jq

# 查看各层统计
curl http://localhost:8080/api/v1/denoise/stats/layer0 | jq   # 基础过滤
curl http://localhost:8080/api/v1/denoise/stats/layer1 | jq   # 指纹去重
curl http://localhost:8080/api/v1/denoise/stats/layer15 | jq  # 规则引擎
curl http://localhost:8080/api/v1/denoise/stats/layer2 | jq   # AI 去噪
```

**预期返回示例**:

```json
{
  "layer0": {
    "enabled": true,
    "totalChecked": 25,
    "totalFiltered": 2,
    "filterRate": 0.08,
    "avgCheckTime": 0.5
  },
  "layer1": {
    "enabled": true,
    "totalChecked": 23,
    "totalFiltered": 10,
    "filterRate": 0.435,
    "cacheSize": 4,
    "avgCheckTime": 0.8
  },
  "layer15": {
    "enabled": true,
    "totalChecked": 13,
    "totalFiltered": 5,
    "filterRate": 0.385,
    "ruleStats": {
      "frequencyLimit": {
        "totalFiltered": 5
      }
    }
  },
  "layer2": {
    "enabled": false,
    "totalChecked": 0
  }
}
```

### 步骤 6: 验证数据库持久化

```sql
-- 查看捕获的异常
SELECT
    exception_type,
    error_location,
    COUNT(*) as count,
    MAX(occurred_at) as last_occurred
FROM exception_record
GROUP BY exception_type, error_location
ORDER BY count DESC;

-- 查看生成的工单
SELECT
    ticket_id,
    title,
    severity,
    status,
    occurrence_count,
    created_at
FROM ticket
ORDER BY created_at DESC
LIMIT 10;

-- 验证严重程度分布
SELECT severity, COUNT(*) as count
FROM ticket
GROUP BY severity;
```

**预期结果**:
- `exception_record` 表: 每种独特异常只有 1 条记录(去重生效)
- `ticket` 表: 每种异常有对应工单
- 严重程度分布合理: NullPointerException → P3, ArithmeticException → P4

### 步骤 7: 测试时间窗口规则 (可选)

**配置**: 启用时间窗口规则

```properties
# 静默时段: 凌晨 2-6 点只允许 P0 异常
one-agent.rule-engine.time-window.enabled=true
one-agent.rule-engine.time-window.quiet-hours=2-6
one-agent.rule-engine.time-window.allowed-severities=P0
```

**测试** (需要在凌晨 2-6 点执行):

```bash
# 低优先级异常应该被过滤
curl http://localhost:8080/test/null-pointer  # P3 级别,应该被过滤
```

**或者**: 临时修改代码中的时间判断逻辑来测试。

---

## 🧪 方法 2: 单元测试

### 运行单元测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=FunnelDenoiseTest

# 运行特定测试方法
mvn test -Dtest=FunnelDenoiseTest#testLayer1_FingerprintDedup
```

### 测试覆盖

已创建的测试类:

1. **ApplicationTests** - 验证 Spring 上下文加载
2. **RecursiveExceptionTest** - 验证递归异常防护
3. **FunnelDenoiseTest** - 验证 4 层漏斗功能:
   - `testLayer0_IgnoreListFilter()` - Layer 0 基础过滤
   - `testLayer1_FingerprintDedup()` - Layer 1 指纹去重
   - `testLayer15_RuleEngine()` - Layer 1.5 规则引擎
   - `testSeverityCalculation()` - 严重程度计算

### 预期输出

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.all.in.one.agent.FunnelDenoiseTest
[INFO] === 测试 Layer 0: 基础过滤 ===
[INFO] ✅ 正常异常未被过滤
[INFO] Layer 0 统计: totalChecked=2, totalFiltered=0, filterRate=0.0
[INFO] === 测试 Layer 1: 指纹去重 ===
[INFO] ✅ 首次异常未被过滤
[INFO] ✅ 重复异常被正确识别
[INFO] Layer 1 统计: totalChecked=2, totalFiltered=1, cacheSize=1, filterRate=0.5
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 🔍 方法 3: 集成测试 - 完整场景

### 场景 1: 正常异常处理流程

**步骤**:

```bash
# 1. 清空数据库
mysql -u root -p one_agent -e "DELETE FROM exception_record; DELETE FROM ticket;"

# 2. 触发新异常
curl http://localhost:8080/test/null-pointer

# 3. 检查数据库
mysql -u root -p one_agent -e "SELECT * FROM exception_record;"
mysql -u root -p one_agent -e "SELECT * FROM ticket;"
```

**验证点**:
- ✅ exception_record 有 1 条记录
- ✅ ticket 有 1 条工单
- ✅ ticket.severity = P3 或 P2
- ✅ ticket.status = PENDING

### 场景 2: 异常去重和聚合

**步骤**:

```bash
# 1. 触发 5 次相同异常
for i in {1..5}; do curl http://localhost:8080/test/null-pointer; sleep 1; done

# 2. 检查数据库
mysql -u root -p one_agent -e "
SELECT fingerprint, COUNT(*) as count
FROM exception_record
GROUP BY fingerprint;
"

mysql -u root -p one_agent -e "
SELECT ticket_id, occurrence_count
FROM ticket
WHERE exception_type = 'java.lang.NullPointerException';
"
```

**验证点**:
- ✅ exception_record 只有 1 条记录(去重生效)
- ✅ ticket.occurrence_count = 1 (因为后续被 Layer 1 过滤,没更新)

### 场景 3: 异常风暴保护

**步骤**:

```bash
# 1. 快速触发 20 次异常
for i in {1..20}; do curl http://localhost:8080/test/arithmetic & done; wait

# 2. 查看统计
curl http://localhost:8080/api/v1/denoise/stats/layer15 | jq
```

**验证点**:
- ✅ 前 10 次通过
- ✅ 第 11-20 次被 FrequencyLimitRule 过滤
- ✅ layer15.ruleStats.frequencyLimit.totalFiltered = 10

### 场景 4: 多种异常混合

**步骤**:

```bash
# 触发 4 种不同异常
curl http://localhost:8080/test/null-pointer
curl http://localhost:8080/test/array-index
curl http://localhost:8080/test/arithmetic
curl http://localhost:8080/test/runtime

# 查看数据库
mysql -u root -p one_agent -e "
SELECT exception_type, COUNT(*) as count, MAX(severity) as severity
FROM ticket
GROUP BY exception_type;
"
```

**验证点**:
- ✅ 4 种异常分别生成 4 条工单
- ✅ 严重程度不同: NullPointer(P3), ArrayIndex(P3), Arithmetic(P4), Runtime(P4)

---

## 🤖 AI 去噪测试 (Layer 2)

### 启用 AI 去噪

```properties
# 启用 Layer 2
one-agent.ai-denoise.enabled=true
one-agent.ai-denoise.lookback-minutes=2
one-agent.ai-denoise.max-history-records=20
```

### 测试 AI 决策

```bash
# 1. 触发第一个异常
curl http://localhost:8080/test/null-pointer

# 2. 立即触发第二个相似异常
curl http://localhost:8080/test/null-pointer

# 3. 查看 AI 统计
curl http://localhost:8080/api/v1/denoise/stats/layer2 | jq
```

**预期**:
- 第 1 次: AI 调用,返回 shouldAlert=true
- 第 2 次: 缓存命中(如果在 5 分钟内),不调用 AI
- layer2.aiCallCount = 1
- layer2.cacheHitCount = 1

**查看日志**:

```
[INFO] AI 去噪: 构建提示词, 历史记录数=0
[INFO] AI 去噪: 调用 LLM, 耗时=1234ms
[INFO] AI 去噪: 决策结果 shouldAlert=true, isDuplicate=false, severity=P3
[INFO] AI 去噪: 缓存命中 fingerprint=abc123...
```

---

## 📊 性能测试

### 测试降噪性能

```bash
# 使用 Apache Bench 进行压测
ab -n 1000 -c 10 http://localhost:8080/test/null-pointer

# 查看统计,计算过滤率
curl http://localhost:8080/api/v1/denoise/stats | jq
```

**预期性能**:
- Layer 0 (基础过滤): < 1ms
- Layer 1 (指纹去重): < 1ms
- Layer 1.5 (规则引擎): < 5ms
- Layer 2 (AI 去噪):
  - 缓存命中: < 1ms
  - AI 调用: 1-3s

**预期过滤率**:
- 1000 次请求 → Layer 0 过滤 ~5% → 950 次
- 950 次 → Layer 1 过滤 ~60% → 380 次
- 380 次 → Layer 1.5 过滤 ~30% → 266 次
- 266 次 → Layer 2 过滤 ~20% → 213 次实际持久化

**总过滤率**: ~79% (1000 → 213)

---

## 🐛 故障排查

### 问题 1: 应用启动失败

**检查**:
- 数据库连接是否正确
- MySQL 是否启动
- 端口 8080 是否被占用

```bash
# 检查端口
netstat -ano | findstr 8080  # Windows
lsof -i :8080                # Linux/Mac

# 检查数据库连接
mysql -u root -p -e "SELECT 1;"
```

### 问题 2: 异常未被捕获

**检查**:
- `one-agent.enabled=true`
- 日志级别: `logging.level.com.all.in.one.agent=INFO`
- 异常是否在忽略列表中

```bash
# 查看忽略列表配置
curl http://localhost:8080/api/v1/denoise/stats/layer0 | jq
```

### 问题 3: 去重不生效

**检查**:
- `one-agent.dedup.enabled=true`
- 时间窗口配置: `one-agent.dedup.time-window-minutes=2`
- 两次请求间隔是否超过 2 分钟

```bash
# 清空缓存重新测试
curl -X POST http://localhost:8080/api/v1/denoise/cache/clear
```

### 问题 4: AI 不调用

**检查**:
- `one-agent.ai-denoise.enabled=true`
- API Key 是否正确: `echo $OPENAI_API_KEY`
- Base URL 是否可访问

```bash
# 测试 API 连接
curl -X POST https://api.siliconflow.cn/v1/chat/completions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"deepseek-ai/DeepSeek-V3","messages":[{"role":"user","content":"Hi"}]}'
```

---

## ✅ 测试检查清单

完成以下检查确保系统正常:

- [ ] 应用成功启动
- [ ] 数据库连接正常
- [ ] 4 种测试接口都能触发异常
- [ ] Layer 0: 基础过滤正常工作
- [ ] Layer 1: 指纹去重生效(相同异常 2 分钟内只记录 1 次)
- [ ] Layer 1.5: 频率限制规则生效(超过 10 次被过滤)
- [ ] Layer 2: AI 去噪正常(如果启用)
- [ ] 异常正确持久化到 exception_record 表
- [ ] 工单正确生成到 ticket 表
- [ ] 严重程度计算正确(P0-P4)
- [ ] 监控 API 返回正确统计数据
- [ ] 日志输出清晰完整

---

## 📝 测试报告模板

测试完成后,建议记录以下信息:

```
### 测试环境
- 操作系统: Windows 11
- Java 版本: 17
- MySQL 版本: 8.0
- Spring Boot 版本: 3.4.8

### 测试结果
- Layer 0 过滤率: 8%
- Layer 1 过滤率: 56%
- Layer 1.5 过滤率: 28%
- Layer 2 过滤率: 18% (如果启用)
- 总过滤率: 79%

### 性能指标
- Layer 0 平均耗时: 0.5ms
- Layer 1 平均耗时: 0.8ms
- Layer 1.5 平均耗时: 3.2ms
- Layer 2 平均耗时: 1.2s (AI 调用), 0.5ms (缓存命中)

### 发现的问题
- 无

### 建议
- 生产环境建议启用 AI 去噪
- 建议调整时间窗口为晚上 0-7 点
```

---

## 🎓 总结

推荐的测试顺序:

1. **第一步**: 使用方法 1 快速启动测试,验证基本功能
2. **第二步**: 运行方法 2 单元测试,验证各层逻辑
3. **第三步**: 执行方法 3 集成测试,验证完整流程
4. **第四步**: 进行性能测试,评估过滤效果

祝测试顺利! 🎉
