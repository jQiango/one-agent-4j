# FAST 日志平台集成文档

## 概述

FAST 日志平台可以通过 One Agent 4J 的 REST API 上报异常，One Agent 会自动进行：
1. **AI 智能去噪**: 判断异常是否需要处理，过滤重复和噪音
2. **异常持久化**: 保存异常记录到数据库
3. **自动工单生成**: 为需要处理的异常创建工单

**与内部捕获的区别**: FAST 平台作为外部入口直接调用 API，但会走相同的 AI 去噪和工单生成流程。

## 架构流程

```
FAST 日志平台
    ↓ HTTP POST
FastLogCallbackController
    ↓
FastLogExceptionService
    ↓
AI 去噪判断 (AiDenoiseService)
    ↓
shouldAlert=true → 持久化 + 生成工单
shouldAlert=false → 返回过滤结果
```

## API 端点

### 基础 URL
```
http://localhost:8080/api/v1/fastlog
```

### 1. 单个异常上报

**POST** `/api/v1/fastlog/exception`

#### 请求体示例

```json
{
  "appName": "order-service",
  "environment": "prod",
  "exceptionType": "java.lang.NullPointerException",
  "exceptionMessage": "Cannot invoke method on null object",
  "stackTrace": "java.lang.NullPointerException: Cannot invoke method on null object\n\tat com.example.OrderService.processOrder(OrderService.java:123)\n\tat com.example.OrderController.createOrder(OrderController.java:45)",
  "errorLocation": "com.example.OrderService.processOrder:123",
  "errorClass": "com.example.OrderService",
  "errorMethod": "processOrder",
  "errorLine": 123,
  "instanceId": "order-service-pod-abc123",
  "hostname": "k8s-node-01",
  "ip": "10.0.1.15",
  "requestMethod": "POST",
  "requestUri": "/api/orders",
  "requestParams": "{\"orderId\":\"12345\"}",
  "clientIp": "192.168.1.100",
  "threadId": 4567,
  "threadName": "http-nio-8080-exec-10",
  "traceId": "trace-xyz789",
  "spanId": "span-abc123",
  "occurredAt": 1704441296000,
  "extra": {
    "fastLogId": "fast-log-uuid-12345",
    "logLevel": "ERROR"
  }
}
```

#### 请求参数说明

| 字段              | 类型              | 必填 | 说明                                        |
|-------------------|-------------------|------|---------------------------------------------|
| appName           | String            | ✓    | 应用名称                                    |
| environment       | String            | ✓    | 环境: dev/test/uat/prod                    |
| exceptionType     | String            | ✓    | 异常类型（完整类名）                        |
| exceptionMessage  | String            | ×    | 异常消息                                    |
| stackTrace        | String            | ×    | 完整堆栈信息                                |
| errorLocation     | String            | ×    | 错误位置（类名.方法名:行号）                |
| errorClass        | String            | ×    | 错误类名                                    |
| errorMethod       | String            | ×    | 错误方法名                                  |
| errorLine         | Integer           | ×    | 错误行号                                    |
| instanceId        | String            | ×    | 实例 ID                                     |
| hostname          | String            | ×    | 主机名                                      |
| ip                | String            | ×    | IP 地址                                     |
| requestMethod     | String            | ×    | HTTP 方法                                   |
| requestUri        | String            | ×    | 请求 URI                                    |
| requestParams     | String            | ×    | 请求参数（JSON 字符串）                     |
| clientIp          | String            | ×    | 客户端 IP                                   |
| threadId          | Long              | ×    | 线程 ID                                     |
| threadName        | String            | ×    | 线程名称                                    |
| traceId           | String            | ×    | TraceId（链路追踪）                         |
| spanId            | String            | ×    | SpanId（链路追踪）                          |
| occurredAt        | Long              | ×    | 发生时间（Unix 时间戳，毫秒）               |
| extra             | Map<String,String>| ×    | 扩展字段                                    |

#### 响应示例

**成功 - 生成工单**:
```json
{
  "code": 200,
  "message": "异常处理成功",
  "fingerprint": "abc123def456",
  "exceptionRecordId": 12345,
  "ticketNo": "TK20250105123456001",
  "ticketId": 678,
  "aiDecision": {
    "shouldAlert": true,
    "isDuplicate": false,
    "similarityScore": 0.0,
    "suggestedSeverity": "P1",
    "reason": "这是首次发生的异常，需要立即处理",
    "suggestion": "建议检查代码第 123 行的空值处理"
  },
  "timestamp": 1704441296000
}
```

**成功 - AI 过滤**:
```json
{
  "code": 200,
  "message": "异常已被 AI 过滤，不生成工单",
  "fingerprint": "abc123def456",
  "exceptionRecordId": null,
  "ticketNo": null,
  "ticketId": null,
  "aiDecision": {
    "shouldAlert": false,
    "isDuplicate": true,
    "similarityScore": 0.95,
    "suggestedSeverity": "P3",
    "reason": "该异常与最近 2 分钟内的 5 个异常高度相似，判定为重复",
    "suggestion": "建议合并处理，不需要重复报警"
  },
  "timestamp": 1704441296000
}
```

**错误响应**:
```json
{
  "code": 400,
  "message": "应用名称不能为空",
  "timestamp": 1704441296000
}
```

#### 响应字段说明

| 字段               | 类型      | 说明                                                    |
|--------------------|-----------|---------------------------------------------------------|
| code               | Integer   | 响应码：200=成功, 400=参数错误, 500=系统错误           |
| message            | String    | 响应消息                                                |
| fingerprint        | String    | 异常指纹（MD5）                                         |
| exceptionRecordId  | Long      | 异常记录 ID（被过滤时为 null）                          |
| ticketNo           | String    | 工单编号（被过滤或未生成时为 null）                     |
| ticketId           | Long      | 工单 ID（被过滤或未生成时为 null）                      |
| aiDecision         | Object    | AI 决策信息                                             |
| timestamp          | Long      | 响应时间戳                                              |

**aiDecision 字段**:

| 字段               | 类型      | 说明                                  |
|--------------------|-----------|---------------------------------------|
| shouldAlert        | Boolean   | 是否需要报警                          |
| isDuplicate        | Boolean   | 是否重复异常                          |
| similarityScore    | Double    | 相似度（0.0-1.0）                    |
| suggestedSeverity  | String    | 建议严重级别：P0/P1/P2/P3/P4          |
| reason             | String    | 判断原因                              |
| suggestion         | String    | 处理建议                              |

### 2. 批量异常上报

**POST** `/api/v1/fastlog/exception/batch`

批量上报多个异常，请求体为数组格式：

```json
[
  {
    "appName": "order-service",
    "environment": "prod",
    "exceptionType": "java.lang.NullPointerException",
    ...
  },
  {
    "appName": "payment-service",
    "environment": "prod",
    "exceptionType": "java.sql.SQLException",
    ...
  }
]
```

响应为数组格式，每个元素对应一个异常的处理结果。

### 3. 健康检查

**GET** `/api/v1/fastlog/health`

检查 API 服务是否正常。

**响应**: `FAST Log Callback API is running`

### 4. API 信息

**GET** `/api/v1/fastlog/info`

获取 API 基本信息和端点列表。

## 集成步骤

### 1. 配置 One Agent

在 `application.properties` 中确保以下配置启用：

```properties
# 启用 API
one-agent.api.enabled=true

# 启用本地持久化
one-agent.storage-strategy.enable-local-persistence=true

# 启用工单生成
one-agent.storage-strategy.enable-ticket-generation=true

# 启用 AI 去噪（推荐）
one-agent.ai-denoise.enabled=true
one-agent.ai-denoise.lookback-minutes=2
one-agent.ai-denoise.max-history-records=20
```

### 2. FAST 平台配置

在 FAST 日志平台中配置：

1. **目标地址**: `http://one-agent-host:8080/api/v1/fastlog/exception`
2. **请求方法**: `POST`
3. **Content-Type**: `application/json`
4. **触发条件**: 检测到 ERROR 级别日志且包含堆栈信息

### 3. 测试连接

```bash
curl -X GET http://localhost:8080/api/v1/fastlog/health
```

### 4. 测试异常上报

```bash
curl -X POST http://localhost:8080/api/v1/fastlog/exception \
  -H "Content-Type: application/json" \
  -d '{
    "appName": "test-service",
    "environment": "dev",
    "exceptionType": "java.lang.NullPointerException",
    "exceptionMessage": "Test exception",
    "occurredAt": 1704441296000
  }'
```

## 工单查询

FAST 平台上报的异常生成工单后，工单信息会在响应中返回（包含 `ticketNo` 和 `ticketId`）。

工单的后续管理和状态更新可以通过数据库直接操作 `ticket` 表，或者开发相应的管理界面。

## AI 去噪策略

AI 会根据以下因素判断是否需要报警：

1. **重复性**: 与最近 2 分钟内的异常对比，相似度 > 0.8 判定为重复
2. **频繁性**: 短时间内大量相似异常，建议合并处理
3. **新颖性**: 新类型或新位置的异常，优先报警
4. **严重性**: 根据异常类型自动评估严重级别

**AI 过滤的异常不会生成工单**，但会在响应中返回过滤原因，FAST 平台可以选择性记录。

## 异常指纹生成

One Agent 会为每个异常生成唯一指纹（MD5），用于：
- 去重判断
- 关联相同问题的工单
- AI 相似度分析

指纹生成规则：`MD5(exceptionType + errorLocation + keyMessage)`

## 最佳实践

1. **完整堆栈**: 尽量提供完整的堆栈信息，帮助 AI 更准确判断
2. **错误位置**: 提供 `errorLocation` 字段，提高指纹准确性
3. **链路追踪**: 提供 `traceId` 和 `spanId`，便于问题排查
4. **批量上报**: 对于高频异常，使用批量接口减少网络开销
5. **监控响应**: 关注 `aiDecision` 字段，了解 AI 的判断逻辑

## 故障排查

### API 不可用

检查配置：
```properties
one-agent.api.enabled=true
```

查看日志：
```
grep "FastLogCallbackController" logs/application.log
```

### 异常未生成工单

可能原因：
1. **AI 过滤**: 查看响应中的 `aiDecision.reason`
2. **工单生成未启用**: 检查 `one-agent.storage-strategy.enable-ticket-generation=true`
3. **异常持久化失败**: 查看应用日志

### AI 去噪不生效

检查配置：
```properties
one-agent.ai-denoise.enabled=true
langchain4j.open-ai.chat-model.api-key=your-key
```

查看日志确认 AI 服务是否正常初始化。

## 安全建议

1. **内网部署**: 将 One Agent 部署在内网，仅允许 FAST 平台访问
2. **网络隔离**: 通过防火墙或安全组限制只有 FAST 平台可以访问 API
3. **限流保护**: 在网关层配置限流，防止异常风暴
4. **日志脱敏**: 确保 FAST 平台上报的数据不包含敏感信息

## 监控指标

建议监控以下指标：

- FAST 异常上报量（QPS）
- AI 过滤率（过滤数/总数）
- 工单生成率
- API 响应时间（P95/P99）
- AI 去噪耗时

## 联系与反馈

如有问题，请查看：
- One Agent 应用日志
- 数据库 `exception_record` 和 `ticket` 表
- FAST 平台的上报日志
