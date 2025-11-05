# HTTP 请求日志功能实现文档

## 功能概述

基于旧项目 `vega-common-log-starter` 的 `HttpLogRequestFilter`，在 One Agent 4J 中实现了**增强版 HTTP 请求日志功能**，用于打印所有 HTTP 请求的入参和出参。

---

## 旧项目 vs 新项目对比

### 功能对比

| 功能 | vega HttpLogRequestFilter | One Agent HttpLogFilter | 升级点 |
|------|--------------------------|------------------------|--------|
| **请求入参打印** | ✅ URI + Method + Params + Body | ✅ 同样支持 | - |
| **响应出参打印** | ✅ Status + Latency + Body | ✅ 同样支持 | - |
| **MDC 上下文** | ✅ 支持 | ✅ 支持 | - |
| **响应截断** | ✅ 4096 字节 | ✅ 可配置 | ✅ 更灵活 |
| **排除 URI** | ✅ 硬编码 + Apollo 配置 | ✅ 配置文件 | ✅ 更简单 |
| **请求头打印** | ✅ 硬编码特定头 | ✅ 可配置 | ✅ 更灵活 |
| **慢请求识别** | ❌ 无 | ✅ 可配置阈值 + WARN 日志 | ✅ **新增** |
| **请求 Body 限制** | ❌ 无 | ✅ 可配置 | ✅ **新增** |
| **与异常监控集成** | ❌ 无 | ✅ 自动注入异常上下文 | ✅ **新增** |
| **配置化程度** | ⚠️ 部分配置 | ✅ 完全配置化 | ✅ **升级** |
| **开关控制** | ⚠️ 只能全局关闭 | ✅ 细粒度控制 | ✅ **升级** |

---

## 核心实现

### 1. HttpLogProperties - 配置类

**文件位置：** `src/main/java/com/all/in/one/agent/starter/logging/HttpLogProperties.java`

**配置前缀：** `one-agent.http-log`

**支持的配置项：**

```properties
# 是否启用（默认 true）
one-agent.http-log.enabled=true

# 打印控制
one-agent.http-log.log-request=true
one-agent.http-log.log-request-body=true
one-agent.http-log.log-response=true
one-agent.http-log.log-headers=false

# 长度限制（字节）
one-agent.http-log.request-body-limit=4096
one-agent.http-log.response-body-limit=4096

# 慢请求阈值（毫秒）
one-agent.http-log.slow-request-threshold=3000

# 排除规则
one-agent.http-log.exclude-uri-prefixes=/actuator,/swagger
one-agent.http-log.exclude-uris=/favicon.ico,/health

# MDC 支持
one-agent.http-log.enable-mdc=true

# 请求头过滤
one-agent.http-log.include-headers=Authorization,X-Request-Id
```

---

### 2. HttpLogFilter - 日志过滤器

**文件位置：** `src/main/java/com/all/in/one/agent/starter/logging/HttpLogFilter.java`

**核心功能：**

#### 2.1 请求入参日志

```java
log.info("HTTP Request IN: POST /api/users?page=1 | clientIp=192.168.1.100 | params=name=test&age=25 | body={\"userId\":\"99\"}");
```

**打印内容：**
- ✅ HTTP Method (GET/POST/PUT/DELETE)
- ✅ Request URI
- ✅ Query String
- ✅ 客户端 IP（支持 X-Forwarded-For）
- ✅ 请求参数 (Query/Form)
- ✅ 请求 Body（JSON/XML 等）
- ✅ 请求头（可选）

---

#### 2.2 响应出参日志

```java
log.info("HTTP Response OUT: POST /api/users | status=200 | latency=45ms | response={\"code\":200,\"data\":{...}}");
```

**打印内容：**
- ✅ HTTP Status Code
- ✅ 请求耗时（毫秒）
- ✅ 响应 Body

**慢请求告警：**
```java
log.warn("[SLOW REQUEST] HTTP Response OUT: POST /api/users | status=200 | latency=3500ms | ...");
```

---

#### 2.3 MDC 上下文

自动设置以下 MDC 字段，供日志系统使用：

```java
MDC.put("requestId", "uuid-12345");
MDC.put("traceId", "trace-67890");
MDC.put("method", "POST");
MDC.put("uri", "/api/users");
MDC.put("clientIp", "192.168.1.100");
MDC.put("latency", "45");
MDC.put("status", "200");
MDC.put("bltag", "request_in");  // 或 "request_out"
```

**作用：**
- ✅ 日志聚合和查询
- ✅ 分布式追踪
- ✅ ELK/Splunk 等日志系统的字段过滤

---

### 3. HttpLogContextHolder - 上下文持有者

**文件位置：** `src/main/java/com/all/in/one/agent/starter/logging/HttpLogContextHolder.java`

**作用：**
- 使用 ThreadLocal 存储当前请求的上下文
- 供异常捕获等其他组件使用

**API：**
```java
// 获取当前请求上下文
HttpLogFilter.HttpLogContext context = HttpLogContextHolder.getContext();
if (context != null) {
    String requestId = context.getRequestId();
    String traceId = context.getTraceId();
    String clientIp = context.getClientIp();
    long latency = context.getLatency();
}
```

---

### 4. ExceptionInfoBuilder 集成

**升级点：** 异常发生时，自动从 HTTP 上下文获取请求信息

**实现：** `ExceptionInfoBuilder.extractTraceId()`

```java
private static String extractTraceId() {
    // 1. 尝试从 HTTP 上下文获取
    HttpLogFilter.HttpLogContext context = HttpLogContextHolder.getContext();
    if (context != null) {
        return context.getTraceId();
    }

    // 2. 尝试从 MDC 获取
    return MDC.get("traceId");
}
```

**效果：**
- ✅ 异常记录自动包含 traceId 和 spanId
- ✅ 可以关联请求日志和异常日志
- ✅ 支持分布式追踪

---

## 工作流程

```
HTTP 请求到达
  ↓
HttpLogFilter 拦截 (HIGHEST_PRECEDENCE + 1)
  ↓
1. 创建 HttpLogContext
   - requestId, traceId, method, uri, clientIp, startTime
   ↓
2. 存储到 ThreadLocal
   - HttpLogContextHolder.setContext(context)
   ↓
3. 设置 MDC
   - MDC.put("requestId", ...)
   - MDC.put("traceId", ...)
   ↓
4. 打印请求入参日志
   - log.info("HTTP Request IN: ...")
   ↓
5. 执行请求 (chain.doFilter)
   ↓
6. 发生异常？
   ├─ 是 → ExceptionCollector.collect()
   │         ↓
   │       ExceptionInfoBuilder.build()
   │         ↓
   │       extractTraceId() ← 从 HttpLogContext 获取
   │         ↓
   │       ExceptionInfo 包含完整上下文
   └─ 否 → 继续
   ↓
7. 打印响应出参日志
   - log.info("HTTP Response OUT: ...")
   - 如果 latency > threshold → log.warn("[SLOW REQUEST] ...")
   ↓
8. 清理上下文
   - HttpLogContextHolder.clear()
   - MDC.clear()
```

---

## 日志示例

### 正常请求

```
2025-01-05 23:30:00.123 INFO  [httpLogFilter] HTTP Request IN: POST /api/users?page=1 | clientIp=192.168.1.100 | params=page=1&size=10 | body={"name":"张三","age":25}

2025-01-05 23:30:00.168 INFO  [httpLogFilter] HTTP Response OUT: POST /api/users | status=200 | latency=45ms | response={"code":200,"data":{"id":"12345"}}
```

---

### 慢请求

```
2025-01-05 23:30:00.123 INFO  [httpLogFilter] HTTP Request IN: GET /api/report/export

2025-01-05 23:30:03.623 WARN  [httpLogFilter] [SLOW REQUEST] HTTP Response OUT: GET /api/report/export | status=200 | latency=3500ms | response={...}
```

---

### 异常请求

```
2025-01-05 23:30:00.123 INFO  [httpLogFilter] HTTP Request IN: POST /api/orders | clientIp=192.168.1.100 | body={"orderId":"12345"}

2025-01-05 23:30:00.145 ERROR [exceptionCollector] 收集到异常 - fingerprint=abc123, type=NullPointerException, location=OrderService.create:45, traceId=trace-67890

2025-01-05 23:30:00.168 INFO  [httpLogFilter] HTTP Response OUT: POST /api/orders | status=500 | latency=45ms | response={"code":500,"message":"Internal Server Error"}
```

**关键点：**
- ✅ 异常日志中自动包含 `traceId=trace-67890`
- ✅ 可以通过 traceId 关联请求日志和异常日志
- ✅ 完整的请求链路追踪

---

## 配置示例

### 生产环境推荐配置

```properties
# 启用 HTTP 日志
one-agent.http-log.enabled=true

# 打印请求和响应
one-agent.http-log.log-request=true
one-agent.http-log.log-request-body=true
one-agent.http-log.log-response=true

# 限制打印长度（避免日志过大）
one-agent.http-log.request-body-limit=4096
one-agent.http-log.response-body-limit=4096

# 慢请求阈值 3 秒
one-agent.http-log.slow-request-threshold=3000

# 排除健康检查等接口
one-agent.http-log.exclude-uri-prefixes=/actuator,/swagger
one-agent.http-log.exclude-uris=/favicon.ico,/health,/ping

# 不打印请求头（避免泄露敏感信息）
one-agent.http-log.log-headers=false

# 启用 MDC
one-agent.http-log.enable-mdc=true
```

---

### 开发环境配置

```properties
# 启用 HTTP 日志
one-agent.http-log.enabled=true

# 打印所有内容
one-agent.http-log.log-request=true
one-agent.http-log.log-request-body=true
one-agent.http-log.log-response=true
one-agent.http-log.log-headers=true

# 无限制打印
one-agent.http-log.request-body-limit=-1
one-agent.http-log.response-body-limit=-1

# 慢请求阈值 1 秒
one-agent.http-log.slow-request-threshold=1000

# 打印特定请求头
one-agent.http-log.include-headers=Authorization,X-Request-Id,User-Agent

# 启用 MDC
one-agent.http-log.enable-mdc=true
```

---

### 性能敏感场景

```properties
# 只打印关键信息
one-agent.http-log.enabled=true
one-agent.http-log.log-request=true
one-agent.http-log.log-request-body=false  # 不打印 Body
one-agent.http-log.log-response=true
one-agent.http-log.response-body-limit=500  # 只打印前 500 字节

# 慢请求阈值 5 秒
one-agent.http-log.slow-request-threshold=5000

# 排除高频接口
one-agent.http-log.exclude-uri-prefixes=/api/metrics,/api/heartbeat
```

---

## 与异常监控的集成

### 场景：异常发生时关联请求日志

**流程：**
1. HTTP 请求进入，HttpLogFilter 记录日志
2. 请求处理过程中发生异常
3. ExceptionCollector 捕获异常
4. ExceptionInfoBuilder 自动从 HttpLogContext 获取 traceId
5. 异常记录包含完整的请求上下文

**查询示例：**
```sql
-- 根据 traceId 查询异常
SELECT * FROM exception_record WHERE trace_id = 'trace-67890';

-- 查询特定 URI 的所有异常
SELECT * FROM exception_record WHERE request_uri = '/api/orders';
```

**日志分析：**
```bash
# 根据 traceId 查询所有相关日志
grep "trace-67890" application.log

# 结果：
# 2025-01-05 23:30:00.123 [trace-67890] HTTP Request IN: POST /api/orders
# 2025-01-05 23:30:00.145 [trace-67890] 收集到异常 - NullPointerException
# 2025-01-05 23:30:00.168 [trace-67890] HTTP Response OUT: POST /api/orders | status=500
```

---

## 升级点总结

### 相比旧项目的改进

#### 1. ✅ 慢请求识别

**旧项目：** 无慢请求识别
**新项目：** 配置阈值，自动打印 WARN 日志

```properties
one-agent.http-log.slow-request-threshold=3000
```

---

#### 2. ✅ 更灵活的配置

**旧项目：** 部分硬编码，部分依赖 Apollo
**新项目：** 完全配置文件驱动

| 配置项 | 旧项目 | 新项目 |
|--------|--------|--------|
| 启用开关 | `vega.http.log.filter.print.enable` | `one-agent.http-log.enabled` |
| 响应限制 | `vega.http.log.response.limit` | `one-agent.http-log.response-body-limit` + `request-body-limit` |
| 排除 URI | `vega.http.log.filter.exclude` (Apollo) | `one-agent.http-log.exclude-uris` (文件) |

---

#### 3. ✅ 与异常监控集成

**旧项目：** HTTP 日志和异常监控分离
**新项目：** 自动关联，共享上下文

**效果：**
- ✅ 异常记录自动包含 traceId
- ✅ 可以通过 traceId 关联请求日志
- ✅ 完整的问题追踪链路

---

#### 4. ✅ 更细粒度的控制

**新项目支持：**
- 单独控制请求参数打印
- 单独控制请求 Body 打印
- 单独控制响应 Body 打印
- 单独控制请求头打印
- 配置需要打印的请求头

---

#### 5. ✅ 更好的性能

**优化点：**
- 请求 Body 长度限制（避免大文件打印）
- 响应 Body 长度限制
- 可选的请求头打印（默认关闭）
- 排除高频接口

---

## 使用建议

### 1. 生产环境

- ✅ 启用 HTTP 日志
- ✅ 限制 Body 打印长度（4KB）
- ✅ 不打印请求头（避免泄露 Token）
- ✅ 排除健康检查等高频接口
- ✅ 设置合理的慢请求阈值（3-5 秒）

---

### 2. 开发/测试环境

- ✅ 启用所有日志
- ✅ 无限制打印
- ✅ 打印请求头（方便调试）
- ✅ 降低慢请求阈值（1 秒）

---

### 3. 性能敏感场景

- ⚠️ 不打印请求/响应 Body
- ⚠️ 只打印关键信息（URI、Status、Latency）
- ⚠️ 排除高频接口

---

### 4. 问题排查

**场景 1：API 响应慢**
1. 查看慢请求日志：`grep "SLOW REQUEST" application.log`
2. 分析耗时接口
3. 优化代码

**场景 2：异常追踪**
1. 从异常记录获取 traceId
2. 查询请求日志：`grep "<traceId>" application.log`
3. 查看完整请求链路

**场景 3：客户端问题**
1. 根据 clientIp 过滤日志
2. 查看该客户端的所有请求
3. 分析问题模式

---

## 总结

### ✅ 实现的功能

1. ✅ 打印所有 HTTP 请求的入参和出参
2. ✅ 支持 MDC 上下文传递
3. ✅ 慢请求自动识别和告警
4. ✅ 灵活的配置化
5. ✅ 与异常监控无缝集成
6. ✅ 性能优化（长度限制、排除规则）

### ✅ 相比旧项目的升级

1. ✅ 更灵活的配置
2. ✅ 慢请求识别
3. ✅ 与异常监控集成
4. ✅ 更细粒度的控制
5. ✅ 更好的性能

### ✅ 使用场景

- ✅ 接口调试和问题排查
- ✅ 性能监控和分析
- ✅ 异常追踪和关联
- ✅ 客户端行为分析
- ✅ 审计日志

---

**One Agent 4J - 让请求日志和异常监控更智能** 🚀
