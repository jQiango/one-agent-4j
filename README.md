# One Agent 4J

**一体化智能异常监控系统**

基于 Spring Boot 3 + LangChain4J 的 AI 驱动异常监控系统，自动捕获异常、智能过滤噪音、持久化存储并生成工单。

---

## ✨ 核心特性

- 🎯 **智能降噪**: AI 过滤重复和无价值异常
- 🤖 **自动分派**: 基于配置自动分配责任人
- 📚 **知识库**: 自动匹配解决方案
- 📈 **趋势监控**: 实时监控异常趋势，智能预警
- 📱 **飞书通知**: Markdown 交互卡片通知
- 🔄 **零配置**: Convention over Configuration

---

## 🚀 快速开始

### 前置条件

- Java 17+
- Maven 3.6+
- MySQL 8.0+

### 1. 克隆项目

```bash
git clone <your-repo-url>
cd one-agent-4j
```

### 2. 数据库初始化

```bash
# 第一期
mysql -u root -p < sql/phase1_init.sql

# 第二期（优化版）
mysql -u root -p < sql/phase2_init_optimized.sql
```

### 3. 配置文件

编辑 `src/main/resources/application.yml`:

```yaml
# 数据库配置（必需）
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/one_agent
    username: root
    password: your_password

# 责任人配置（必需）
one-agent:
  responsibility:
    default-owner: "zhangsan"
    service-owners:
      payment-service: "lisi"
      order-service: "wangwu"
    feishu-mapping:
      zhangsan: "ou_xxx123"
      lisi: "ou_xxx456"

# AI降噪配置（可选）
langchain4j:
  open-ai:
    chat-model:
      api-key: ${OPENAI_API_KEY}
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

### 5. 测试功能

```bash
# 测试知识库
curl http://localhost:8080/api/solution/search?keyword=NullPointerException

# 测试趋势分析
curl http://localhost:8080/api/trend/analyze/payment-service?days=7
```

---

## 📚 文档

所有文档已整理到 `.claude/docs/` 目录：

### 快速导航
- **[文档索引](.claude/docs/文档索引.md)** - 📖 推荐从这里开始
- **[项目状态](.claude/docs/项目状态.md)** - 📊 当前开发状态
- **[第一期快速开始](.claude/docs/第一期快速开始.md)** - 🚀 部署指南
- **[第二期功能验证](.claude/docs/第二期功能验证.md)** - ✅ 验证步骤

### 功能文档
- **[第一期总结](.claude/docs/第一期总结.md)** - 智能分派、知识库、飞书通知
- **[第二期总结](.claude/docs/第二期总结.md)** - 异常趋势监控（优化版）

### 技术文档
- **[第二期技术设计](.claude/docs/第二期技术设计.md)** - 详细技术设计
- **[第二期优化方案](.claude/docs/第二期优化方案.md)** - 优化说明
- **[飞书集成指南](.claude/docs/飞书集成指南.md)** - 飞书配置
- **[测试指南](.claude/docs/测试指南.md)** - 测试说明
- **[降噪策略](.claude/docs/降噪策略.md)** - AI降噪策略
- **[未来功能规划](.claude/docs/未来功能规划.md)** - 功能路线图

### 开发文档
- **[CLAUDE.md](.claude/CLAUDE.md)** - Claude Code 开发指南

---

## 🎯 核心功能

### 第一期（已完成）✅

#### 1. 智能分派与责任人识别
- yml 配置责任人映射
- 自动分派工单
- 支持飞书 @ 提醒

#### 2. 解决方案知识库
- 精确匹配 + 模糊匹配
- 自动评分（1-5星）
- 使用统计和成功率

#### 3. 飞书机器人通知
- Markdown 交互卡片
- 按优先级分级通知
- 支持 @ 提醒

### 第二期（进行中）🚀

#### 1. 异常趋势监控（已完成并优化）✅
- 实时监控异常趋势
- 线性回归算法检测
- 未来7天预测
- 飞书直发告警

#### 2. 异常模式识别（待开发）📝
- 周期性检测
- 突发检测
- 关联分析

#### 4. 根因分析（待开发）📝
- 调用链路分析
- AI辅助诊断
- 历史案例匹配

---

## 🏗️ 技术栈

- **核心框架**: Spring Boot 3.4.8
- **数据访问**: MyBatis-Plus 3.5.5
- **数据库**: MySQL 8.0+
- **AI集成**: LangChain4J 1.7.1
- **开发工具**: Java 17, Maven

---

## 📊 架构设计

### 异常处理流程

```
异常捕获 (Filter/ControllerAdvice/AOP)
   ↓
异常收集 (ExceptionCollector)
   ↓
基础过滤 (Ignore List)
   ↓
AI降噪 (LangChain4J)
   ↓
异常持久化 (MySQL)
   ↓
工单生成 + 自动分派
   ↓
飞书通知 (@责任人)
```

### 趋势监控流程

```
定时任务 (每小时/每天)
   ↓
聚合统计 (按天/小时)
   ↓
持久化 (alarm_trend_stat)
   ↓
趋势分析 (线性回归)
   ↓
智能预警 (飞书直发)
```

---

## 🔧 配置说明

### 最小配置

只需配置2项即可启动：

```yaml
# 1. 数据库连接
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/one_agent
    username: root
    password: your_password

# 2. 责任人配置
one-agent:
  responsibility:
    default-owner: "zhangsan"
```

### 完整配置

参考 [第一期快速开始](.claude/docs/第一期快速开始.md)

---

## 📈 项目状态

- ✅ **第一期**: 100% 完成（智能分派、知识库、飞书通知）
- ✅ **第二期功能1**: 100% 完成并优化（异常趋势监控）
- 📝 **第二期功能2**: 待开发（异常模式识别）
- 📝 **第二期功能4**: 待开发（根因分析）

详见 [项目状态](.claude/docs/项目状态.md)

---

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📝 许可证

[MIT License](LICENSE)

---

## 📞 联系方式

- **文档**: [.claude/docs/文档索引.md](.claude/docs/文档索引.md)
- **Issues**: GitHub Issues
- **开发指南**: [.claude/CLAUDE.md](.claude/CLAUDE.md)

---

**开始使用**: [第一期快速开始](.claude/docs/第一期快速开始.md) 🚀
