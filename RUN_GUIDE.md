# One Agent 4J - 运行指南

## 📁 项目结构

```
one-agent-4j/
├── one-agent-4j-common/          # 公共模块（模型、工具类）
├── one-agent-4j-starter/         # Spring Boot Starter（自动装配）
├── one-agent-4j-app/             # ⭐ 主应用（这个就是您要运行的！）
└── pom.xml                       # 父 POM
```

---

## 🚀 编译和运行

### 步骤 1: 编译项目

在项目根目录执行：

```bash
cd F:\work\ai\one-agent-4j
mvn clean install -DskipTests
```

**预期输出：**
```
[INFO] one-agent-4j-parent ................................ SUCCESS
[INFO] one-agent-4j-common ................................ SUCCESS
[INFO] one-agent-4j-starter ............................... SUCCESS
[INFO] one-agent-4j-app ................................... SUCCESS
[INFO] BUILD SUCCESS
```

---

### 步骤 2: 运行主应用

#### 方式 1: 在 IDEA 中运行（推荐）✅

1. 在 IDEA 中打开项目
2. 找到文件：`one-agent-4j-app/src/main/java/com/all/in/one/agent/Application.java`
3. 右键点击 `Application.java`
4. 选择 **"Run 'Application.main()'"**

#### 方式 2: 使用 Maven 命令

```bash
cd one-agent-4j-app
mvn spring-boot:run
```

#### 方式 3: 直接运行 JAR

```bash
java -jar one-agent-4j-app/target/one-agent-4j-app-1.0.0-SNAPSHOT.jar
```

---

## ✅ 验证自动装配

启动成功后，在日志中寻找以下关键信息：

```
===========================================
One Agent 4J 自动装配开始
应用名称: one-agent-4j
环境: dev
采样率: 1.0
上报模式: async
服务器地址: null
===========================================
2025-01-26 10:00:00.000 [main] INFO  ExceptionReporter - ExceptionReporter 初始化完成 - mode=async, serverUrl=null
2025-01-26 10:00:00.000 [main] INFO  ExceptionCollector - ExceptionCollector 初始化完成 - appName=one-agent-4j, environment=dev, samplingRate=1.0
2025-01-26 10:00:00.000 [main] INFO  AgentAutoConfiguration - 注册 ExceptionCaptureFilter
2025-01-26 10:00:00.000 [main] INFO  AgentAutoConfiguration - 注册 GlobalExceptionHandler
2025-01-26 10:00:00.000 [main] INFO  AgentAutoConfiguration - 注册 ExceptionCaptureAspect
```

**✅ 如果看到以上日志，说明 One Agent 自动装配成功！**

---

## 🧪 测试异常监控

应用启动后，打开浏览器或使用 curl 测试：

### 测试 1: 正常接口（基线）

```bash
curl http://localhost:8080/hello
```

**预期：** 正常返回 AI 回复（需要配置 API Key）

---

### 测试 2: NullPointerException

```bash
curl http://localhost:8080/test/null-pointer
```

**预期日志：**
```
2025-01-26 10:01:00.000 [http-nio-8080-exec-1] WARN  GlobalExceptionHandler - ControllerAdvice 捕获到异常 - error=null
2025-01-26 10:01:00.000 [http-nio-8080-exec-1] INFO  ExceptionCollector - 收集到异常 - fingerprint=abc123..., type=NullPointerException, location=DemoController.testNullPointer:62
```

**✅ 验证点：**
- 看到 "ControllerAdvice 捕获到异常"
- 看到 "收集到异常"
- 包含异常指纹、类型、位置

---

### 测试 3: ArrayIndexOutOfBoundsException

```bash
curl http://localhost:8080/test/array-index
```

**预期：** 捕获 ArrayIndexOutOfBoundsException

---

### 测试 4: ArithmeticException

```bash
curl http://localhost:8080/test/arithmetic
```

**预期：** 捕获 ArithmeticException（除零异常）

---

### 测试 5: RuntimeException

```bash
curl http://localhost:8080/test/runtime
```

**预期：** 捕获自定义 RuntimeException

---

## 📊 查看完整异常信息

如果想看到完整的异常信息 JSON，可以临时修改代码打印：

编辑 `one-agent-4j-starter/src/main/java/com/all/in/one/agent/starter/collector/ExceptionCollector.java`

在 `collect` 方法中添加：

```java
log.info("完整异常信息: {}", JSON.toJSONString(exceptionInfo));
```

重启应用，触发异常后就能在日志中看到完整的 JSON 对象，包括：
- 应用名称、环境
- 异常类型、消息
- 完整堆栈
- 异常指纹
- 错误位置
- 主机名、IP
- 线程信息

---

## 🎛️ 配置测试

### 测试 1: 禁用 One Agent

编辑 `one-agent-4j-app/src/main/resources/application.properties`：

```properties
one-agent.enabled=false
```

重启应用，应该看不到 One Agent 的初始化日志。

---

### 测试 2: 调整采样率

```properties
one-agent.sampling-rate=0.1  # 只有 10% 的异常会被采集
```

重启应用，多次访问异常接口，只有部分会被收集。

---

### 测试 3: 禁用某层捕获

```properties
one-agent.capture-config.enable-filter=false
one-agent.capture-config.enable-controller-advice=true
one-agent.capture-config.enable-aop=false
```

重启应用，只有 ControllerAdvice 会捕获异常。

---

## 🐛 常见问题

### 问题 1: 编译失败

**原因：** Maven 或 JDK 版本问题

**解决：**
```bash
# 检查版本
mvn -version  # 应该 >= 3.6
java -version # 应该是 JDK 17

# 清理重新编译
mvn clean install -U
```

---

### 问题 2: 看不到 One Agent 日志

**检查：**
1. `one-agent.enabled` 是否为 `true`
2. 日志级别：`logging.level.com.all.in.one.agent=DEBUG`

---

### 问题 3: 端口冲突

**错误：** `Port 8080 was already in use`

**解决：** 修改 `application.properties`
```properties
server.port=8081
```

---

## 📋 验证检查清单

```
编译和启动：
□ mvn clean install 编译成功
□ 应用启动成功
□ 看到 "One Agent 4J 自动装配开始" 日志
□ 看到 "ExceptionCollector 初始化完成"
□ 看到 "注册 ExceptionCaptureFilter"
□ 看到 "注册 GlobalExceptionHandler"
□ 看到 "注册 ExceptionCaptureAspect"

异常捕获测试：
□ NullPointerException 被捕获
□ ArrayIndexOutOfBoundsException 被捕获
□ ArithmeticException 被捕获
□ RuntimeException 被捕获
□ 异常信息包含指纹、类型、位置

配置测试：
□ enabled=false 可以禁用
□ sampling-rate 采样率生效
□ 可以禁用某层捕获
```

---

## 🎯 核心验证点

### ✅ 成功标志

1. **启动时日志包含**：
   - "One Agent 4J 自动装配开始"
   - "ExceptionCollector 初始化完成"
   - "注册 ExceptionCaptureFilter/GlobalExceptionHandler/ExceptionCaptureAspect"

2. **触发异常时日志包含**：
   - "ControllerAdvice 捕获到异常"
   - "收集到异常"
   - fingerprint、type、location

3. **配置生效**：
   - `enabled=false` 可以禁用
   - 采样率控制有效

---

## 📌 重要说明

### 🔥 零侵入特性

**您的项目只需要：**

1. ✅ 在 `pom.xml` 中添加一个依赖：
```xml
<dependency>
    <groupId>com.all.in</groupId>
    <artifactId>one-agent-4j-starter</artifactId>
</dependency>
```

2. ✅ 在 `application.properties` 中添加配置（可选）：
```properties
one-agent.enabled=true
```

**无需修改任何业务代码！所有异常自动监控！**

---

## 🚀 下一步

验证完成后，可以继续实现：

1. **持久化**：创建 collector 服务，保存异常到数据库
2. **告警降噪**：实现四层降噪策略（97% 降噪率）
3. **工单管理**：自动生成工单
4. **AI 分析**：RAG 增强的根因分析
5. **对话交互**：AI Agent 对话系统

告诉我测试结果，我会继续优化！🎉
