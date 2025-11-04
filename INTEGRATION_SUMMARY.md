# One Agent 4J - 集成总结

## 功能概览

One Agent 4J 是一个 AI 驱动的智能异常监控系统，提供以下核心能力：

1. **多入口异常采集**
   - 内部捕获：Filter、ControllerAdvice、AOP
   - 外部 API：FAST 日志平台集成

2. **AI 智能去噪**
   - 基于 LangChain4J 和 DeepSeek-V3
   - 自动识别重复异常
   - 智能评估严重级别

3. **自动工单生成**
   - 异常指纹去重
   - SLA 时间管理
   - 状态流转追踪

4. **外部平台回调**
   - 工单状态更新
   - 支持评论和解决方案记录

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    异常入口（双入口）                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  内部捕获入口                    外部 API 入口           │
│  ┌──────────────┐               ┌──────────────┐        │
│  │ Filter       │               │ FAST 日志    │        │
│  │ ControllerAdv│    ═══════>   │ 平台         │        │
│  │ AOP          │               │ HTTP POST    │        │
│  └──────┬───────┘               └──────┬───────┘        │
│         │                               │                │
│         v                               v                │
│  ┌──────────────┐               ┌──────────────┐        │
│  │ExceptionColle│               │FastLogExcepti│        │
│  │ctor          │               │onService     │        │
│  └──────┬───────┘               └──────┬───────┘        │
│         │                               │                │
│         └───────────┬───────────────────┘                │
│                     │                                    │
└─────────────────────┼────────────────────────────────────┘
                      │
                      v
          ┌───────────────────────┐
          │  AI 智能去噪           │
          │  (AiDenoiseService)   │
          │  - 查询历史异常        │
          │  - LLM 判断相似度      │
          │  - 返回 shouldAlert    │
          └───────────┬───────────┘
                      │
            ┌─────────┴─────────┐
            │                   │
            v                   v
      shouldAlert=false    shouldAlert=true
      返回过滤结果              │
                               v
                    ┌──────────────────┐
                    │  异常持久化        │
                    │  (exception_record)│
                    └──────────┬─────────┘
                               │
                               v
                    ┌──────────────────┐
                    │  工单生成          │
                    │  (ticket)         │
                    │  - 去重合并        │
                    │  - 严重级别计算    │
                    │  - SLA 设置        │
                    └──────────┬─────────┘
                               │
                               v
                    ┌──────────────────┐
                    │  工单管理          │
                    │  - 数据库操作      │
                    │  - 管理界面        │
                    └────────────────────┘
```

## 关键组件

### 1. 异常采集层
| 组件                          | 用途                              |
|-------------------------------|-----------------------------------|
| `ExceptionCaptureFilter`      | HTTP 请求异常捕获                 |
| `GlobalExceptionHandler`      | Controller 异常捕获               |
| `ExceptionCaptureAspect`      | Service 层异常捕获（AOP）         |
| `ExceptionCollector`          | 异常收集中心，防递归捕获          |

### 2. 外部集成层
| 组件                          | 用途                              |
|-------------------------------|-----------------------------------|
| `FastLogCallbackController`   | FAST 平台异常上报 API             |
| `FastLogExceptionService`     | FAST 异常处理服务                 |

### 3. AI 去噪层
| 组件                          | 用途                              |
|-------------------------------|-----------------------------------|
| `AiDenoiseService`            | AI 去噪主服务                     |
| `DenoiseAiService`            | LangChain4J AI 接口               |
| `DenoisePrompt`               | 提示词模板（资源文件）            |
| `DenoiseDecision`             | AI 决策结果模型                   |

### 4. 持久化层
| 组件                          | 用途                              |
|-------------------------------|-----------------------------------|
| `ExceptionPersistenceService` | 异常记录持久化                    |
| `TicketGenerationService`     | 工单生成和管理                    |
| `ExceptionRecordMapper`       | 异常记录 DAO                      |
| `TicketMapper`                | 工单 DAO                          |

## API 端点清单

### FAST 日志平台集成
```
POST   /api/v1/fastlog/exception         # 单个异常上报
POST   /api/v1/fastlog/exception/batch   # 批量异常上报
GET    /api/v1/fastlog/health             # 健康检查
GET    /api/v1/fastlog/info               # API 信息
```

## 配置清单

### 必需配置
```properties
# AI 服务配置（必需）
langchain4j.open-ai.chat-model.api-key=your-api-key
langchain4j.open-ai.chat-model.base-url=https://api.siliconflow.cn
langchain4j.open-ai.chat-model.model-name=deepseek-ai/DeepSeek-V3

# 数据库配置（必需）
spring.datasource.url=jdbc:mysql://localhost:3306/one_agent
spring.datasource.username=root
spring.datasource.password=password
```

### 功能开关
```properties
# 系统总开关
one-agent.enabled=true

# 存储策略
one-agent.storage-strategy.enable-local-persistence=true
one-agent.storage-strategy.enable-ticket-generation=true
one-agent.storage-strategy.enable-http-report=false

# AI 去噪
one-agent.ai-denoise.enabled=true
one-agent.ai-denoise.lookback-minutes=2
one-agent.ai-denoise.max-history-records=20

# 异常捕获
one-agent.capture-config.enable-filter=true
one-agent.capture-config.enable-controller-advice=true
one-agent.capture-config.enable-aop=true

# API 接口
one-agent.api.enabled=true
```

## 数据流转

### 1. FAST 平台异常上报流程
```
1. FAST 平台检测到异常日志
   ↓
2. POST /api/v1/fastlog/exception
   ↓
3. FastLogExceptionService.processException()
   ↓
4. 转换为 ExceptionInfo
   ↓
5. AI 去噪判断
   ├─ shouldAlert=false → 返回过滤结果
   └─ shouldAlert=true  → 继续处理
      ↓
6. 持久化到 exception_record 表
   ↓
7. 生成或更新 ticket 表
   ↓
8. 返回响应（包含 ticketNo 和 AI 决策）
```

### 2. 内部异常捕获流程
```
1. 应用抛出异常
   ↓
2. 捕获层拦截（Filter/ControllerAdvice/AOP）
   ↓
3. ExceptionCollector.collect()
   ├─ 检查递归（ThreadLocal 防护）
   └─ 基础过滤（ignored-exceptions/packages）
      ↓
4. 通知监听器 → ExceptionProcessService
   ↓
5. AI 去噪判断
   ├─ shouldAlert=false → 仅记录日志
   └─ shouldAlert=true  → 继续处理
      ↓
6. 持久化 + 生成工单（同上）
```


## 核心特性

### 1. 防递归捕获
使用 ThreadLocal 标记，避免监控系统自身异常导致的递归捕获。

**位置**: `ExceptionCollector.PROCESSING`

### 2. 异常指纹
基于 MD5(exceptionType + errorLocation + message) 生成唯一指纹，用于去重和关联。

**位置**: `FingerprintGenerator`

### 3. AI 去噪与提示词模板化
- 查询最近 2 分钟内的历史异常
- 从资源文件加载提示词模板 (`src/main/resources/prompts/denoise-prompt-template.txt`)
- 使用变量替换填充实际数据
- 调用 LLM 判断是否需要报警
- 返回相似度、严重级别、处理建议

**位置**: `AiDenoiseService` + `DenoisePrompt`
**提示词模板**: `src/main/resources/prompts/denoise-prompt-template.txt`

### 4. 工单去重
相同指纹的异常会更新同一工单的 `occurrence_count`，而不是创建新工单。

**位置**: `TicketGenerationService.generateTicket()`

### 5. SLA 管理
根据严重级别自动计算期望解决时间：
- P0: 30 分钟
- P1: 2 小时
- P2: 24 小时
- P3: 3 天
- P4: 7 天

**位置**: `TicketGenerationService.calculateExpectedResolveTime()`

## 文档索引

| 文档                          | 内容                              |
|-------------------------------|-----------------------------------|
| `README.md`                   | （待创建）项目概述和快速开始      |
| `CLAUDE.md`                   | 架构设计和开发指南                |
| `FASTLOG_INTEGRATION.md`      | FAST 平台集成详细文档             |
| `INTEGRATION_SUMMARY.md`      | 本文档：集成总结                  |

## 测试建议

### 1. 测试 FAST 平台集成
```bash
# 健康检查
curl http://localhost:8080/api/v1/fastlog/health

# 上报异常
curl -X POST http://localhost:8080/api/v1/fastlog/exception \
  -H "Content-Type: application/json" \
  -d '{
    "appName": "test-service",
    "environment": "dev",
    "exceptionType": "java.lang.NullPointerException",
    "exceptionMessage": "Test exception"
  }'
```

### 2. 测试内部异常捕获
```bash
# 触发测试异常
curl http://localhost:8080/test/null-pointer
curl http://localhost:8080/test/runtime
```

### 3. 测试 AI 去噪
上报 2 次相同的异常，观察第二次是否被 AI 过滤。

## 监控指标

建议监控以下关键指标：

1. **异常捕获量**: 每分钟捕获的异常数
2. **AI 过滤率**: 被 AI 过滤的异常占比
3. **工单生成率**: 生成工单的异常占比
4. **平均去噪耗时**: AI 去噪的平均响应时间
5. **工单解决时长**: 平均工单解决时长
6. **SLA 超时率**: 超出 SLA 的工单占比

## 故障排查

### 问题：异常未被捕获
- 检查捕获开关是否启用
- 检查异常是否在 ignored-exceptions 列表中
- 检查 AOP pointcut 是否匹配

### 问题：AI 去噪不生效
- 检查 `one-agent.ai-denoise.enabled=true`
- 检查 OpenAI API Key 是否配置
- 查看日志确认 AI 服务初始化状态

### 问题：工单未生成
- 检查 AI 是否过滤（查看响应中的 aiDecision）
- 检查 `one-agent.storage-strategy.enable-ticket-generation=true`
- 查看异常记录表是否有数据

### 问题：FAST 平台上报失败
- 检查网络连通性
- 检查 API 是否启用
- 查看应用日志和数据库日志

## 性能优化建议

1. **数据库索引**: 已在 exception_record 和 ticket 表上添加必要索引
2. **AI 缓存**: 考虑缓存相同指纹的 AI 决策结果（短期缓存）
3. **批量上报**: FAST 平台使用批量接口减少网络开销
4. **异步处理**: HTTP 上报使用异步模式
5. **限流保护**: 在网关层配置限流，防止异常风暴

## 后续扩展方向

1. **多模型支持**: 支持不同的 LLM 模型
2. **自定义规则**: 支持用户自定义去噪规则
3. **告警通知**: 集成钉钉、企业微信等通知渠道
4. **趋势分析**: 异常趋势统计和可视化
5. **根因分析**: 基于 AI 的根因分析功能
