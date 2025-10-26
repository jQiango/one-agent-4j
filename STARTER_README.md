# One Agent 4J Starter - 使用指南

## 项目结构

```
one-agent-4j/
├── one-agent-4j-common/          # 公共模块（模型、工具类）
├── one-agent-4j-starter/         # ⭐ Spring Boot Starter（零侵入监控）
├── one-agent-4j-test/            # 测试应用（验证功能）
└── pom.xml                       # 父 POM
```

---

## 编译和运行

### 1. 编译项目

```bash
cd F:\work\ai\one-agent-4j
mvn clean install -DskipTests
```

### 2. 运行测试应用

```bash
cd one-agent-4j-test
mvn spring-boot:run
```

或者：

```bash
java -jar one-agent-4j-test/target/one-agent-4j-test-1.0.0-SNAPSHOT.jar
```

### 3. 验证自动装配

启动后，查看日志中是否包含以下信息：

```
===========================================
One Agent 4J 自动装配开始
应用名称: one-agent-test-app
环境: dev
采样率: 1.0
上报模式: async
服务器地址: null
===========================================
ExceptionReporter 初始化完成 - mode=async, serverUrl=null
ExceptionCollector 初始化完成 - appName=one-agent-test-app, environment=dev, samplingRate=1.0
注册 ExceptionCaptureFilter
注册 GlobalExceptionHandler
注册 ExceptionCaptureAspect
```

---

## 测试异常监控

测试应用已经提供了多个测试端点，访问以下 URL 触发异常：

### 1. 正常请求（不触发异常）

```bash
curl http://localhost:8080/test/hello
```

**响应：**
```
Hello, One Agent 4J!
```

### 2. 测试 NullPointerException

```bash
curl http://localhost:8080/test/null-pointer
```

**预期：**
- ✅ ControllerAdvice 捕获异常
- ✅ 日志输出异常指纹、类型、位置
- ✅ 异常信息收集完成

**日志示例：**
```
ControllerAdvice 捕获到异常 - error=null
收集到异常 - fingerprint=abc123, type=NullPointerException, location=TestController.testNullPointer:45
```

### 3. 测试 ArrayIndexOutOfBoundsException

```bash
curl http://localhost:8080/test/array-index
```

### 4. 测试 ArithmeticException

```bash
curl http://localhost:8080/test/arithmetic
```

### 5. 测试 IllegalArgumentException

```bash
curl "http://localhost:8080/test/illegal-argument?name="
```

### 6. 测试 Service 层异常（AOP 捕获）

```bash
curl http://localhost:8080/test/service-exception
```

**预期：**
- ✅ AOP 切面捕获异常
- ✅ ControllerAdvice 也捕获异常（多层捕获）
- ✅ 异常信息收集完成

**日志示例：**
```
AOP 捕获到异常 - error=业务异常：数据处理失败
收集到异常 - fingerprint=def456, type=RuntimeException, location=TestService.businessMethod:32
ControllerAdvice 捕获到异常 - error=业务异常：数据处理失败
收集到异常 - fingerprint=def456, type=RuntimeException, location=TestService.businessMethod:32
```

### 7. 测试嵌套异常

```bash
curl http://localhost:8080/test/nested-exception
```

**预期：**
- ✅ 捕获外层异常（RuntimeException）
- ✅ 堆栈包含 "Caused by" 信息

---

## 使用方式（第三方项目集成）

### Step 1: 添加依赖

在你的项目 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.all.in</groupId>
    <artifactId>one-agent-4j-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**仅此而已！** 无需修改任何业务代码。

### Step 2: 配置（可选）

在 `application.yml` 中添加配置（所有配置都是可选的）：

```yaml
one-agent:
  # 是否启用 (默认 true)
  enabled: true

  # 应用名称 (不配置则使用 spring.application.name)
  app-name: my-service

  # 环境 (不配置则使用 spring.profiles.active)
  environment: prod

  # 采样率 (0.0-1.0, 1.0 表示 100% 采样)
  sampling-rate: 1.0

  # 上报服务器地址
  server-url: http://one-agent-server:8080

  # 上报策略
  report-strategy:
    mode: async  # sync/async/batch
    batch-size: 10
    queue-size: 1000

  # 异常捕获配置
  capture-config:
    enable-filter: true
    enable-controller-advice: true
    enable-aop: true
```

### Step 3: 启动应用

启动你的 Spring Boot 应用，自动监控所有异常！

---

## 核心特性

### 1. ✨ 零侵入

- **无需修改业务代码**：只需添加 Maven 依赖
- **自动装配**：Spring Boot Starter 自动配置所有组件
- **开箱即用**：默认配置即可使用

### 2. 🎯 多层捕获

- **Filter 层**：捕获 Servlet 异常
- **Controller 层**：通过 @RestControllerAdvice 捕获
- **Service 层**：通过 AOP 切面捕获
- **三层覆盖**：确保不遗漏任何异常

### 3. 📊 完整信息

自动收集的异常信息包括：

- ✅ 异常类型和消息
- ✅ 完整堆栈（包含 Caused by）
- ✅ 异常指纹（用于去重）
- ✅ 错误位置（类名.方法名:行号）
- ✅ 应用名称和环境
- ✅ 主机名和 IP
- ✅ 线程信息
- ✅ 发生时间

### 4. 🚀 三种上报模式

#### 同步模式 (sync)
```yaml
one-agent:
  report-strategy:
    mode: sync
```
- 立即上报，阻塞请求
- 适合：低 QPS 场景

#### 异步模式 (async)
```yaml
one-agent:
  report-strategy:
    mode: async
    queue-size: 1000
    thread-pool-size: 2
```
- 放入队列，异步上报
- 适合：高 QPS 场景（推荐）

#### 批量模式 (batch)
```yaml
one-agent:
  report-strategy:
    mode: batch
    batch-size: 10
    max-wait-time: 5000
```
- 批量聚合后上报
- 适合：超高 QPS 场景

### 5. 🎛️ 灵活配置

#### 采样率控制

```yaml
one-agent:
  sampling-rate: 0.1  # 10% 采样
```

#### 忽略特定异常

```yaml
one-agent:
  capture-config:
    ignored-exceptions:
      - org.springframework.security.access.AccessDeniedException
      - com.example.BusinessException
```

#### 忽略特定包路径

```yaml
one-agent:
  capture-config:
    ignored-packages:
      - org.springframework
      - com.example.internal
```

#### 自定义 AOP 切入点

```yaml
one-agent:
  capture-config:
    aop-pointcut: "execution(* com.mycompany.*.service..*.*(..))"
```

---

## 关键组件说明

### 1. ExceptionInfo (异常信息模型)

位置：`one-agent-4j-common/src/main/java/com/all/in/one/agent/common/model/ExceptionInfo.java`

包含完整的异常上下文信息，支持序列化和传输。

### 2. AgentProperties (配置属性)

位置：`one-agent-4j-common/src/main/java/com/all/in/one/agent/common/config/AgentProperties.java`

所有配置项的定义，带默认值和说明。

### 3. ExceptionCollector (异常收集器)

位置：`one-agent-4j-starter/src/main/java/com/all/in/one/agent/starter/collector/ExceptionCollector.java`

负责：
- 采样控制
- 异常过滤
- 异常信息构建
- 委托上报

### 4. ExceptionReporter (异常上报器)

位置：`one-agent-4j-starter/src/main/java/com/all/in/one/agent/starter/reporter/ExceptionReporter.java`

负责：
- 三种上报模式实现
- HTTP 上报
- 队列管理
- 批量聚合

### 5. 三层捕获机制

- **ExceptionCaptureFilter**：Filter 层捕获
- **GlobalExceptionHandler**：Controller 层捕获
- **ExceptionCaptureAspect**：Service 层 AOP 捕获

### 6. AgentAutoConfiguration (自动装配类)

位置：`one-agent-4j-starter/src/main/java/com/all/in/one/agent/starter/autoconfigure/AgentAutoConfiguration.java`

负责：
- 读取配置
- 创建 Bean
- 注册组件
- 条件装配

---

## 异常指纹算法

异常指纹用于去重和聚合，计算方式：

```
fingerprint = MD5(exceptionType + ":" + errorLocation)
```

示例：

```
exceptionType: NullPointerException
errorLocation: com.example.UserService.getUser:123

fingerprint = MD5("NullPointerException:com.example.UserService.getUser:123")
            = "abc123def456..."
```

**相同指纹的异常会被识别为同一类问题**，便于后续降噪和聚合。

---

## 下一步

### 阶段二：告警降噪

实现四层降噪策略：

1. **过滤层**：环境过滤、黑名单过滤
2. **去重层**：时间窗口去重、指纹去重
3. **聚合层**：按服务/实例/调用链聚合
4. **分级层**：自动评估严重级别（P0-P4）

### 阶段三：工单管理

自动生成工单：
- 从告警自动创建工单
- 智能分派处理人
- 完整状态流转
- SLA 监控

### 阶段四：AI 分析

RAG 增强的智能分析：
- 堆栈分析
- 根因定位
- 解决方案推荐
- 历史案例检索

### 阶段五：对话交互

AI Agent 对话系统：
- 自然语言查询
- 工具调用
- 多渠道接入

---

## 常见问题

### Q: 为什么需要三层捕获？

A: 不同层捕获的异常类型不同：

- **Filter**：捕获 Servlet 容器级别的异常
- **ControllerAdvice**：捕获 Controller 层未处理的异常
- **AOP**：捕获 Service 层业务异常

三层结合确保全覆盖。

### Q: 会不会影响性能？

A: 影响极小：

- **异步模式**：不阻塞请求，性能影响 < 1ms
- **采样控制**：可配置采样率降低开销
- **批量模式**：高 QPS 场景进一步优化

### Q: 如何禁用 One Agent？

A: 两种方式：

方式 1：配置文件
```yaml
one-agent:
  enabled: false
```

方式 2：启动参数
```bash
java -jar app.jar --one-agent.enabled=false
```

### Q: 如何只捕获特定层的异常？

A: 禁用其他层：

```yaml
one-agent:
  capture-config:
    enable-filter: false
    enable-controller-advice: true
    enable-aop: false
```

### Q: 异常会被吞掉吗？

A: **不会！** 所有捕获的异常都会继续抛出，不影响原有的异常处理逻辑。

---

## 总结

✅ **零侵入**：只需添加依赖，无需修改代码

✅ **自动装配**：Spring Boot Starter 自动配置

✅ **三层捕获**：Filter + ControllerAdvice + AOP

✅ **完整信息**：指纹、堆栈、位置、环境等

✅ **三种模式**：同步/异步/批量

✅ **灵活配置**：采样率、过滤规则、切入点

✅ **高性能**：异步上报，性能影响极小

**现在就开始使用 One Agent 4J，让异常监控零侵入！** 🚀
