# One Agent 4J 设计文档索引

本文档提供 One Agent 4J 智能服务治理平台所有设计文档的导航和概览。

---

## 项目简介

**One Agent 4J** 是一个基于 AI 的智能服务治理平台,专注于解决微服务架构中的异常告警泛滥问题。通过告警降噪、智能分析、工单管理和 AI 对话等能力,大幅提升运维效率。

### 核心特性

- 🔥 **零侵入监控**: Spring Boot Starter 自动装配,无需修改业务代码
- 🧠 **智能降噪**: 97% 告警降噪率,4层降噪策略
- 🤖 **AI 分析**: RAG 增强的异常根因分析和解决方案推荐
- 📋 **工单管理**: 完整的工单生命周期管理,自动分派和 SLA 监控
- 💬 **对话交互**: LangChain4J 驱动的自然语言对话系统
- 📚 **知识库**: 自动积累历史处理方案,持续学习
- 📊 **可观测性**: Prometheus + Grafana + ELK 全方位监控

---

## 设计文档列表

### 1. [项目整体设计](./PROJECT_DESIGN.md)

**核心内容:**
- Maven 多模块项目结构
- Spring Boot Starter 零侵入设计
- 自动装配和异常捕获机制
- 三种上报模式: 同步/异步/批量

**关键模块:**
- `one-agent-4j-starter`: 核心 Starter,提供自动监控能力
- `one-agent-4j-collector`: 告警收集服务
- `one-agent-4j-analyzer`: 智能分析引擎
- `one-agent-4j-platform`: 管理平台

**适合阅读对象:** 架构师、后端开发

---

### 2. [系统架构设计](./ARCHITECTURE.md)

**核心内容:**
- 五层架构设计: 告警平台 → 网关 → MQ → 核心处理 → 存储 → 交互
- 核心服务接口定义
- 数据库表设计 (5 张核心表)
- 技术栈选型

**关键组件:**
- `AlertController`: 多源告警接入
- `AlertDenoiseService`: 告警降噪
- `AlertAnalysisService`: AI 分析
- `AgentService`: 对话服务
- `KnowledgeService`: 知识库

**适合阅读对象:** 架构师、技术经理

---

### 3. [告警降噪策略](./DENOISE_STRATEGY.md)

**核心内容:**
- 四层降噪架构: 过滤 → 去重 → 聚合 → 分级
- 指纹算法和时间窗口去重
- 向量相似度智能去重
- 自动严重级别分类 (P0-P4)

**关键技术:**
- 基于 MD5 的异常指纹
- Redis 时间窗口去重 (5 分钟)
- Milvus 向量相似度去重
- 规则引擎自动评级

**预期效果:**
- 告警降噪率 > 97%
- P0 告警准确率 100%

**适合阅读对象:** 算法工程师、后端开发

---

### 4. [RAG 堆栈分析](./RAG_STACK_ANALYSIS.md)

**核心内容:**
- 堆栈信息解析和结构化
- 源代码索引和向量化
- 三种检索策略: 精确匹配/语义搜索/调用链
- AI 根因分析和解决方案生成

**关键组件:**
- `StackTraceParser`: 堆栈解析器
- `CodeIndexService`: 代码索引服务
- `RagRetrievalService`: RAG 检索服务
- `StackAnalysisService`: 堆栈分析服务

**技术亮点:**
- JavaParser AST 代码解析
- BGE-Large 中文向量模型
- 上下文构建和 Prompt 工程

**适合阅读对象:** AI 工程师、算法工程师

---

### 5. [工单管理系统](./TICKET_SYSTEM_DESIGN.md)

**核心内容:**
- 工单完整数据模型 (包含您要求的所有字段)
- 工单状态机设计 (9 种状态流转)
- 自动工单生成和智能分派
- SLA 监控和告警
- 工单统计和趋势分析

**关键功能:**
- 自动从告警生成工单
- 智能分派: 基于历史记录/代码归属/服务负责人
- 完整生命周期: PENDING → ASSIGNED → ACCEPTED → IN_PROGRESS → RESOLVED → CLOSED
- 处理方案记录和知识沉淀

**数据库表:**
- `ticket`: 工单主表 (包含服务、类型、优先级、负责人、处理人、状态、方案等)
- `ticket_status_history`: 状态流转历史
- `ticket_collaboration`: 协作记录

**适合阅读对象:** 产品经理、后端开发

---

### 6. [AI Agent 对话系统](./AI_AGENT_DESIGN.md)

**核心内容:**
- 多轮对话和上下文管理
- 意图识别和工具调用 (Function Calling)
- RAG 检索增强
- 流式响应
- 多渠道接入 (Web/钉钉/Slack)

**关键组件:**
- `ConversationManager`: 会话管理器
- `AgentService`: AI Agent 引擎
- `IntentRecognizer`: 意图识别器
- `ToolRegistry`: 工具注册中心

**工具定义:**
- `query_ticket`: 查询工单
- `update_ticket`: 更新工单
- `analyze_issue`: 分析问题
- `query_alerts`: 查询告警
- `get_statistics`: 获取统计

**技术实现:**
- LangChain4J 框架
- OpenAI/DeepSeek 模型
- WebSocket 实时通信
- 钉钉机器人集成

**适合阅读对象:** AI 工程师、前端开发

---

### 7. [知识库构建方案](./KNOWLEDGE_BASE_DESIGN.md)

**核心内容:**
- 多源知识采集: 代码/工单/文档/日志
- 文本分割和向量化
- 混合检索: 向量 + 关键词 + RRF 融合
- 重排序优化

**关键服务:**
- `CodeScannerService`: 源代码扫描和索引
- `SolutionExtractorService`: 工单方案提取
- `VectorStoreService`: Milvus 向量存储
- `HybridRetrievalService`: 混合检索

**数据模型:**
- `knowledge_base`: 知识库主表
- `code_index`: 代码索引表
- `solution_library`: 解决方案库

**检索策略:**
- 向量检索 (BGE-Large)
- 关键词检索 (Elasticsearch)
- RRF 结果融合
- LLM 重排序

**预期效果:**
- 检索准确率 > 85%
- 响应时间 < 100ms
- 方案复用率 > 70%

**适合阅读对象:** AI 工程师、搜索工程师

---

### 8. [系统监控和告警](./MONITORING_DESIGN.md)

**核心内容:**
- 多维度监控指标体系
- Prometheus 告警规则
- Grafana 仪表盘设计
- 日志规范和链路追踪

**监控指标:**

**系统指标:**
- JVM 内存、GC、线程
- HTTP QPS、延迟、错误率
- 数据库连接池
- Redis、MQ 性能

**业务指标:**
- 告警接收/过滤/去重
- 工单创建/解决/SLA
- AI Agent 调用/Token 消耗
- RAG 检索性能

**告警规则:**
- JVM 堆内存 > 85%
- HTTP 5xx 错误率 > 1%
- P0 工单未处理 > 10min
- LLM 调用失败率 > 5%
- SLA 超时率 > 10%

**可观测性方案:**
- Prometheus + Grafana (指标)
- ELK Stack (日志)
- Jaeger (链路追踪)
- Alertmanager + DingTalk (告警)

**适合阅读对象:** 运维工程师、SRE

---

## 技术栈总览

### 后端框架
- **Spring Boot 3.4+**: 应用框架
- **LangChain4J 1.7+**: AI Agent 框架
- **Spring Cloud Sleuth**: 链路追踪

### AI 模型
- **LLM**: DeepSeek-V3 / GPT-4
- **Embedding**: BGE-Large-zh-v1.5
- **向量数据库**: Milvus 2.3+

### 数据存储
- **MySQL 8.0+**: 关系数据库
- **Redis 7.0+**: 缓存和去重
- **Elasticsearch 8.0+**: 全文检索
- **Milvus 2.3+**: 向量存储

### 消息队列
- **RabbitMQ 3.12+**: 异步消息

### 监控体系
- **Prometheus**: 指标采集
- **Grafana**: 可视化
- **ELK Stack**: 日志分析
- **Jaeger**: 链路追踪
- **Alertmanager**: 告警管理

### 代码解析
- **JavaParser**: Java 代码 AST 解析

---

## 快速导航

### 按角色查阅

**架构师 / 技术经理:**
1. [项目整体设计](./PROJECT_DESIGN.md) - 了解整体架构
2. [系统架构设计](./ARCHITECTURE.md) - 了解技术选型
3. [系统监控和告警](./MONITORING_DESIGN.md) - 了解可观测性

**后端开发:**
1. [项目整体设计](./PROJECT_DESIGN.md) - 了解模块划分
2. [工单管理系统](./TICKET_SYSTEM_DESIGN.md) - 实现工单功能
3. [告警降噪策略](./DENOISE_STRATEGY.md) - 实现降噪算法

**AI 工程师:**
1. [RAG 堆栈分析](./RAG_STACK_ANALYSIS.md) - 实现堆栈分析
2. [AI Agent 对话系统](./AI_AGENT_DESIGN.md) - 实现对话功能
3. [知识库构建方案](./KNOWLEDGE_BASE_DESIGN.md) - 实现知识库

**运维 / SRE:**
1. [系统监控和告警](./MONITORING_DESIGN.md) - 配置监控告警
2. [系统架构设计](./ARCHITECTURE.md) - 了解部署架构

**产品经理:**
1. [项目整体设计](./PROJECT_DESIGN.md) - 了解产品功能
2. [工单管理系统](./TICKET_SYSTEM_DESIGN.md) - 了解工单流程
3. [AI Agent 对话系统](./AI_AGENT_DESIGN.md) - 了解交互方式

### 按功能查阅

**异常监控:**
- [项目整体设计](./PROJECT_DESIGN.md) - Starter 自动装配

**告警处理:**
- [系统架构设计](./ARCHITECTURE.md) - 告警接入
- [告警降噪策略](./DENOISE_STRATEGY.md) - 降噪算法

**问题分析:**
- [RAG 堆栈分析](./RAG_STACK_ANALYSIS.md) - AI 分析

**工单管理:**
- [工单管理系统](./TICKET_SYSTEM_DESIGN.md) - 完整流程

**用户交互:**
- [AI Agent 对话系统](./AI_AGENT_DESIGN.md) - 对话功能

**知识沉淀:**
- [知识库构建方案](./KNOWLEDGE_BASE_DESIGN.md) - 知识库

**系统运维:**
- [系统监控和告警](./MONITORING_DESIGN.md) - 监控告警

---

## 预期效果总结

### 业务指标

| 指标 | 目标值 | 说明 |
|-----|-------|-----|
| 告警降噪率 | > 97% | 从 10 万条降至 3000 条 |
| 工单自动生成率 | 100% | 告警自动转工单 |
| 工单解决效率提升 | 60% | AI 辅助分析 |
| SLA 达成率 | > 95% | 自动监控预警 |
| 方案复用率 | > 70% | 知识库推荐 |
| 用户满意度 | > 90% | 对话式交互 |

### 技术指标

| 指标 | 目标值 | 说明 |
|-----|-------|-----|
| 告警处理延迟 | < 1s | P99 延迟 |
| AI 分析响应时间 | < 2s | 包含 RAG 检索 |
| RAG 检索准确率 | > 85% | 混合检索 + 重排序 |
| 系统可用性 | > 99.9% | 高可用部署 |
| 故障发现时间 | < 1min | 实时监控告警 |

---

## 项目启动顺序

### 阶段一: 基础设施 (Week 1-2)

1. 创建 Maven 多模块项目结构
2. 实现 `one-agent-4j-starter` 核心 Starter
3. 实现异常捕获和上报
4. 部署基础中间件 (MySQL/Redis/RabbitMQ)

### 阶段二: 告警处理 (Week 3-4)

1. 实现告警接入 API
2. 实现四层降噪策略
3. 实现工单自动生成
4. 实现工单状态流转

### 阶段三: AI 能力 (Week 5-6)

1. 集成 LangChain4J
2. 实现 RAG 堆栈分析
3. 实现代码索引和向量化
4. 部署 Milvus 向量数据库

### 阶段四: 对话系统 (Week 7-8)

1. 实现 AI Agent 对话引擎
2. 实现工具调用
3. 实现多渠道接入
4. 集成钉钉机器人

### 阶段五: 知识库 (Week 9-10)

1. 实现知识采集
2. 实现混合检索
3. 实现解决方案提取
4. 实现知识库管理

### 阶段六: 监控运维 (Week 11-12)

1. 集成 Prometheus + Grafana
2. 配置告警规则
3. 实现日志规范
4. 集成链路追踪

---

## 贡献指南

1. 阅读相关设计文档
2. 遵循代码规范和日志规范
3. 编写单元测试和集成测试
4. 提交前运行 `mvn clean verify`
5. 提交 Pull Request

---

## 联系方式

- **项目仓库**: (待添加)
- **文档仓库**: (待添加)
- **问题反馈**: (待添加)

---

**最后更新**: 2025-01-24

**文档版本**: v1.0

**状态**: ✅ 设计完成,待实现
