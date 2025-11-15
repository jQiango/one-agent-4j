# One Agent 4J 测试类说明

本目录包含 One Agent 4J 的所有测试类。

---

## 📋 测试类列表

| 测试类 | 说明 | 用途 |
|--------|------|------|
| **ApplicationTests.java** | 基础测试 | 验证 Spring Context 加载 |
| **FunnelDenoiseTest.java** | 漏斗降噪测试 | 测试多层降噪机制 (Layer 0/1/1.5) |
| **RecursiveExceptionTest.java** | 递归异常测试 | 测试递归异常捕获的防护机制 |
| **AiDenoiseModelTest.java** ⭐ | AI 模型完整测试 | 全面评估 AI 降噪效果 (8个场景) |
| **AiDenoiseQuickTest.java** ⭐ | AI 快速测试 | 快速验证单个场景 |

---

## 🚀 快速开始

### 运行 AI 模型测试

```bash
# 快速测试（推荐先运行这个）
mvn test -Dtest=AiDenoiseQuickTest#quickTest_CustomException

# 完整测试套件
mvn test -Dtest=AiDenoiseModelTest

# 运行所有测试
mvn test
```

---

## ⚙️ 前置条件

### 1. 数据库
```bash
# 确保 MySQL 已启动
mysql -u root -p < sql/init.sql
```

### 2. AI 配置
```properties
# application.properties
one-agent.ai-denoise.enabled=true
langchain4j.open-ai.chat-model.api-key=your-api-key
langchain4j.open-ai.chat-model.base-url=https://api.siliconflow.cn
langchain4j.open-ai.chat-model.model-name=deepseek-ai/DeepSeek-V3
```

### 3. 环境变量（可选）
```bash
# Windows
set OPENAI_API_KEY=your-key

# Linux/Mac
export OPENAI_API_KEY=your-key
```

---

## 📊 测试场景说明

### AiDenoiseModelTest (完整测试)

| 测试方法 | 测试场景 | 预期结果 |
|---------|---------|---------|
| testCase1 | 完全相同的异常 | 首次报警，重复过滤 |
| testCase2 | 相似但不同的异常 | 相似度 > 0.7 |
| testCase3 | 完全不同的异常 | 新异常应报警 |
| testCase4 | 频繁重复异常 | 建议合并告警 |
| testCase5 | 严重级别评估 | P0-P4 准确判断 |
| testCase6 | 缓存性能 | 命中 < 50ms |
| testCase7 | 业务上下文理解 | 理解业务异常 |
| testCase8 | 统计信息 | 完整指标输出 |

### AiDenoiseQuickTest (快速测试)

| 测试方法 | 说明 |
|---------|------|
| quickTest_CustomException | 🔧 **可修改参数** - 测试自定义场景 |
| quickTest_BatchSimilarExceptions | 批量测试相似异常 |
| quickTest_CompareSeverity | 对比不同严重程度 |

---

## 🎯 推荐测试流程

### 步骤1: 验证基础功能
```bash
mvn test -Dtest=ApplicationTests
```

### 步骤2: 测试漏斗降噪
```bash
mvn test -Dtest=FunnelDenoiseTest
```

### 步骤3: 快速验证 AI
```bash
mvn test -Dtest=AiDenoiseQuickTest#quickTest_CustomException
```

### 步骤4: 完整 AI 评估
```bash
mvn test -Dtest=AiDenoiseModelTest
```

---

## 🐛 常见问题

### Q: 编译错误 "找不到符号"

**解决方案**:
```bash
# 清理并重新编译
mvn clean compile
```

### Q: 测试提示 "AI 降噪服务未启用"

**解决方案**:
检查配置文件中是否启用：
```properties
one-agent.ai-denoise.enabled=true
langchain4j.open-ai.chat-model.api-key=your-key
```

### Q: 数据库连接失败

**解决方案**:
```bash
# 1. 检查 MySQL 是否运行
mysqladmin ping

# 2. 检查数据库是否存在
mysql -u root -p -e "SHOW DATABASES LIKE 'one_agent';"

# 3. 重新初始化
mysql -u root -p < sql/init.sql
```

---

## 📚 相关文档

- 📖 完整测试指南: `../../../../../../AI_MODEL_TEST_GUIDE.md`
- 📖 项目架构: `../../../../../../CLAUDE.md`
- 📖 降噪策略: `../../../../../../DENOISE_STRATEGY.md`

---

**Good Luck!** 🎉
