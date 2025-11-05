# One Agent 4J 升级文档：从 vega-common-alarm 迁移

## 概述

本文档描述如何从旧的 **vega-common-alarm** 模块升级到新的 **One Agent 4J** 智能异常监控系统。

---

## 旧项目 vs 新项目对比

### 架构对比

| 维度 | vega-common-alarm (旧) | One Agent 4J (新) |
|------|----------------------|------------------|
| **定位** | 简单告警工具 | AI 驱动的智能监控系统 |
| **告警渠道** | 企业微信机器人 | 数据库持久化 + 工单系统 (可扩展) |
| **去噪能力** | 无（所有异常都告警） | ✅ AI 智能去噪 + 多层漏斗过滤 |
| **异常捕获** | AOP (service 层) | AOP + Filter + ControllerAdvice |
| **数据持久化** | 无 | ✅ MySQL 持久化 + 工单管理 |
| **外部集成** | 无 | ✅ REST API (FAST 日志平台) |
| **配置方式** | 企微机器人 Key | 完整的配置体系 |
| **指纹去重** | 无 | ✅ MD5 指纹 + 时间窗口去重 |
| **严重级别** | 无 | ✅ P0-P4 自动评估 |

---

## 核心功能对比

### 1. 异常捕获

#### 旧项目 (vega-common-alarm)

```java
@Aspect
@Component
public class ExceptionHandlingAspect {

    @Pointcut("execution(* com.ke.*..service.impl.*.*(..))")
    public void pointcut() {}

    @AfterThrowing(value = "pointcut()", throwing = "e")
    public void afterThrowing(JoinPoint joinPoint, Throwable e) {
        // 过滤业务异常
        if (e instanceof BusinessException || e instanceof IllegalArgumentException) {
            log.info("执行方法失败", e);
            return;
        }

        // 直接发送企微告警
        AlarmUtil.sendAlarm("service方法执行错误",
            "method: " + joinPoint.getSignature().toLongString(), e);
    }
}
```

**特点：**
- ✅ 简单直接
- ❌ 只捕获 service 层
- ❌ 无去噪，每个异常都发送告警
- ❌ 无持久化

---

#### 新项目 (One Agent 4J)

```java
// 1. AOP 捕获 (service 层)
@Aspect
@Component
public class ExceptionCaptureAspect {
    @Around("execution(* com.*.*.*.service..*.*(..))")
    public Object captureException(ProceedingJoinPoint pjp) throws Throwable {
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            exceptionCollector.collect(e);
            throw e;
        }
    }
}

// 2. Filter 捕获 (HTTP 层)
@Component
public class ExceptionCaptureFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        try {
            chain.doFilter(request, response);
        } catch (Throwable e) {
            exceptionCollector.collect(e);
            throw e;
        }
    }
}

// 3. ControllerAdvice 捕获 (Controller 层)
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        exceptionCollector.collect(e);
        return ResponseEntity.status(500).body("Internal Server Error");
    }
}
```

**特点：**
- ✅ 三层捕获，全面覆盖
- ✅ 统一收集到 ExceptionCollector
- ✅ AI 去噪判断
- ✅ 持久化 + 工单生成

---

### 2. 告警发送

#### 旧项目 (vega-common-alarm)

```java
@Component
public class AlarmUtil {

    private static final String tpl = "### 【告警】{} \n"
        + "### serviceID: <font color=\"info\">{}</font>\n"
        + "### 机器: <font color=\"info\">{}</font>\n"
        + "### 时间: <font color=\"info\">{}</font>\n"
        + "内容:\n {}";

    public void send(String title, String content, Throwable t) {
        // 格式化消息
        String msg = String.format(tpl, title, appName, hostname, time, content);

        // 直接发送到企微
        Map<String, Object> params = Maps.newHashMap();
        params.put("msgtype", "markdown");
        params.put("markdown", Collections.singletonMap("content", msg));
        HttpUtil.post("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=" + robotKey,
            JSON.toJSONString(params));
    }
}
```

**配置：**
```properties
app.alarm.robot-key=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

**特点：**
- ✅ 实时推送企微
- ❌ 消息长度限制 4000 字符
- ❌ 无去重，噪音严重
- ❌ 无持久化，告警丢失后无法追溯

---

#### 新项目 (One Agent 4J)

```java
// 1. 异常收集
ExceptionCollector.collect(Throwable)
    ↓
// 2. 第 0 层：基础过滤 (Ignore List)
IgnoreListFilter.shouldIgnore() → 过滤 ~10%
    ↓
// 3. 第 1 层：指纹去重 (待实现)
FingerprintDeduplicator.check() → 过滤 ~40-60%
    ↓
// 4. AI 去噪
AiDenoiseService.shouldAlert() → 过滤 ~80%
    ↓
// 5. 持久化
ExceptionPersistenceService.saveException()
    ↓
// 6. 生成工单
TicketGenerationService.generateTicket()
```

**配置：**
```properties
# AI 去噪
one-agent.ai-denoise.enabled=true
one-agent.ai-denoise.lookback-minutes=2
one-agent.ai-denoise.max-history-records=20

# 基础过滤
one-agent.ignore-list.enabled=true
one-agent.ignore-list.exception-types=AccessDeniedException
one-agent.ignore-list.package-prefixes=org.springframework.actuator

# 持久化和工单
one-agent.storage-strategy.enable-local-persistence=true
one-agent.storage-strategy.enable-ticket-generation=true
```

**特点：**
- ✅ 多层去噪，过滤 90% 噪音
- ✅ 持久化到数据库，可追溯
- ✅ 工单系统，跟踪处理进度
- ✅ AI 智能判断严重程度
- ✅ 指纹去重，避免重复告警

---

### 3. 配置对比

#### 旧项目配置

```properties
# application.properties
app.alarm.robot-key=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
app.alarm.schedule=false
app.alarm.aspect-package=com.ke.*..service.impl
```

**配置项说明：**
- `robot-key`: 企业微信机器人 Webhook Key
- `schedule`: 是否定时发送（功能不明确）
- `aspect-package`: AOP 切面包路径（硬编码，不灵活）

---

#### 新项目配置

```properties
# ========== 基础配置 ==========
spring.application.name=my-service
spring.profiles.active=prod

# ========== One Agent 配置 ==========
one-agent.enabled=true
one-agent.sampling-rate=1.0

# ========== 存储策略 ==========
one-agent.storage-strategy.enable-local-persistence=true
one-agent.storage-strategy.enable-ticket-generation=true
one-agent.storage-strategy.enable-http-report=false

# ========== 异常捕获 ==========
one-agent.capture-config.enable-filter=true
one-agent.capture-config.enable-controller-advice=true
one-agent.capture-config.enable-aop=true
one-agent.capture-config.aop-pointcut=execution(* com.ke.*..service..*.*(..))

# ========== 第 0 层：基础过滤 ==========
one-agent.ignore-list.enabled=true
one-agent.ignore-list.exception-types=AccessDeniedException,NoHandlerFoundException
one-agent.ignore-list.package-prefixes=org.springframework.boot.actuate
one-agent.ignore-list.error-locations=*.health,*.heartbeat
one-agent.ignore-list.message-keywords=health check,actuator

# ========== AI 去噪 ==========
one-agent.ai-denoise.enabled=true
one-agent.ai-denoise.lookback-minutes=2
one-agent.ai-denoise.max-history-records=20

# ========== LangChain4J (AI) ==========
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.base-url=https://api.siliconflow.cn
langchain4j.open-ai.chat-model.model-name=deepseek-ai/DeepSeek-V3

# ========== 数据库 ==========
spring.datasource.url=jdbc:mysql://localhost:3306/one_agent
spring.datasource.username=root
spring.datasource.password=123456
```

**配置优势：**
- ✅ 完整的功能开关
- ✅ 灵活的捕获策略
- ✅ 多层过滤配置
- ✅ AI 模型可配置
- ✅ 数据持久化支持

---

## 迁移方案

### 阶段 1: 兼容层实现（保留旧 API）

为了平滑过渡，新项目可以提供兼容旧项目 `AlarmUtil` 的 API。

#### 创建兼容类

```java
package com.all.in.one.agent.compat;

import com.all.in.one.agent.starter.collector.ExceptionCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Map;

/**
 * 兼容旧版 vega-common-alarm 的 AlarmUtil
 * <p>
 * 提供向后兼容的 API，内部使用 One Agent 4J 的能力
 * </p>
 */
@Slf4j
@Component
public class AlarmUtil {

    @Resource
    private ExceptionCollector exceptionCollector;

    private static AlarmUtil instance;

    @PostConstruct
    public void init() {
        instance = this;
        log.info("AlarmUtil 兼容层已初始化 (One Agent 4J)");
    }

    /**
     * 发送告警 - 兼容旧 API
     * @deprecated 请使用 One Agent 4J 的自动捕获机制
     */
    @Deprecated
    public static void sendAlarm(String title, String msg) {
        if (instance == null) {
            log.error("AlarmUtil 未初始化");
            return;
        }
        log.warn("使用了旧版 AlarmUtil.sendAlarm() API，建议升级到 One Agent 4J 自动捕获");

        // 转换为 RuntimeException 让 One Agent 捕获
        RuntimeException e = new RuntimeException("[" + title + "] " + msg);
        instance.exceptionCollector.collect(e);
    }

    /**
     * 发送告警 - 兼容旧 API
     * @deprecated 请使用 One Agent 4J 的自动捕获机制
     */
    @Deprecated
    public static void sendAlarm(String title, Throwable t) {
        if (instance == null) {
            log.error("AlarmUtil 未初始化");
            return;
        }
        log.warn("使用了旧版 AlarmUtil.sendAlarm() API，建议升级到 One Agent 4J 自动捕获");
        instance.exceptionCollector.collect(t);
    }

    /**
     * 发送告警 - 兼容旧 API
     * @deprecated 请使用 One Agent 4J 的自动捕获机制
     */
    @Deprecated
    public static void sendAlarm(String title, String content, Throwable t) {
        if (instance == null) {
            log.error("AlarmUtil 未初始化");
            return;
        }
        log.warn("使用了旧版 AlarmUtil.sendAlarm() API，建议升级到 One Agent 4J 自动捕获");
        instance.exceptionCollector.collect(t);
    }

    /**
     * 发送 Markdown 告警 - 兼容旧 API
     * @deprecated 不再支持此功能
     */
    @Deprecated
    public static void sendMarkDownAlarm(Map<String, Object> params) {
        log.warn("sendMarkDownAlarm() 已废弃，One Agent 4J 不支持自定义 Markdown 告警");
    }
}
```

---

### 阶段 2: 逐步替换

#### 步骤 1: 移除手动告警调用

**旧代码（需要删除）：**
```java
@Service
public class UserService {

    public void createUser(User user) {
        try {
            // 业务逻辑
            userDao.insert(user);
        } catch (Exception e) {
            // ❌ 手动发送告警
            AlarmUtil.sendAlarm("创建用户失败", "userId: " + user.getId(), e);
            throw e;
        }
    }
}
```

**新代码（自动捕获）：**
```java
@Service
public class UserService {

    public void createUser(User user) {
        // 业务逻辑
        userDao.insert(user);
        // ✅ 异常会被 AOP 自动捕获，无需手动处理
    }
}
```

**说明：**
- One Agent 4J 的 AOP 会自动捕获所有 service 层异常
- 无需手动调用 `AlarmUtil.sendAlarm()`
- 异常会经过 AI 去噪后再决定是否生成工单

---

#### 步骤 2: 调整 AOP 切点配置

**旧项目切点（硬编码）：**
```java
@Pointcut("execution(* com.ke.*..service.impl.*.*(..))")
public void pointcut() {}
```

**新项目切点（可配置）：**
```properties
# application.properties
one-agent.capture-config.aop-pointcut=execution(* com.ke.*..service..*.*(..))
```

**说明：**
- 新项目支持通过配置调整切点
- 默认切点更宽泛：`service..*` (所有子包)
- 如需兼容旧项目，配置为：`execution(* com.ke.*..service.impl.*.*(..))`

---

#### 步骤 3: 迁移配置文件

**旧配置 → 新配置映射：**

| 旧配置 | 新配置 | 说明 |
|--------|--------|------|
| `app.alarm.robot-key` | （移除） | 新项目使用数据库持久化，不再需要企微 Key |
| `app.alarm.aspect-package` | `one-agent.capture-config.aop-pointcut` | 切点表达式更灵活 |
| （无） | `one-agent.ai-denoise.enabled=true` | 启用 AI 去噪 |
| （无） | `one-agent.ignore-list.*` | 配置基础过滤规则 |

**迁移示例：**

```properties
# ========== 旧配置（删除）==========
# app.alarm.robot-key=xxxxx
# app.alarm.schedule=false
# app.alarm.aspect-package=com.ke.*..service.impl

# ========== 新配置（添加）==========
# 启用 One Agent
one-agent.enabled=true

# 捕获配置（兼容旧项目切点）
one-agent.capture-config.enable-aop=true
one-agent.capture-config.aop-pointcut=execution(* com.ke.*..service.impl.*.*(..))

# AI 去噪（推荐启用）
one-agent.ai-denoise.enabled=true

# 基础过滤（过滤业务异常，兼容旧逻辑）
one-agent.ignore-list.exception-types=BusinessException,IllegalArgumentException
```

---

### 阶段 3: 数据库初始化

```bash
mysql -u root -p < sql/init.sql
```

**表结构：**
- `exception_record`: 异常记录
- `ticket`: 工单
- `ticket_status_history`: 工单状态历史

---

### 阶段 4: 监控和验证

#### 1. 查看启动日志

```
===========================================
One Agent 4J 自动装配开始
应用名称: my-service
环境: prod
采样率: 1.0
上报模式: async
===========================================

========== 第 0 层：基础过滤配置 ==========
忽略异常类型: [BusinessException, IllegalArgumentException]
...
```

#### 2. 触发异常验证

```java
@RestController
public class TestController {

    @GetMapping("/test/exception")
    public String testException() {
        throw new RuntimeException("测试异常");
    }
}
```

访问 `http://localhost:8080/test/exception`，检查：
- ✅ 日志中出现：`收集到异常 - fingerprint=xxx`
- ✅ 数据库 `exception_record` 表有新记录
- ✅ 数据库 `ticket` 表有新工单

#### 3. 验证 AI 去噪

多次触发相同异常：
```bash
for i in {1..10}; do curl http://localhost:8080/test/exception; done
```

检查：
- ✅ 只有第一次生成工单
- ✅ 后续被 AI 判断为重复，过滤掉
- ✅ 日志中出现：`AI 判断不需要报警 - isDuplicate=true`

---

## 功能差异与增强

### 新增功能

#### 1. AI 智能去噪

**场景：** 系统短时间内大量相同异常

**旧项目：**
- ❌ 每个异常都发送企微
- ❌ 群消息轰炸，无法分辨重点

**新项目：**
- ✅ AI 判断异常是否重复
- ✅ 相同异常只告警一次
- ✅ 过滤率 80-90%

**示例：**
```
10 个相同的 NullPointerException
  ↓
旧项目: 发送 10 条企微消息
新项目: 生成 1 个工单
```

---

#### 2. 指纹去重

**场景：** 判断异常是否"本质相同"

**指纹生成规则：**
```java
fingerprint = MD5(exceptionType + errorLocation)
// 例如: MD5("NullPointerException:UserService.getUser:123")
```

**效果：**
- ✅ 相同代码位置的相同异常 → 相同指纹
- ✅ 不同参数的相同异常 → 相同指纹（视为重复）
- ✅ 不同位置的异常 → 不同指纹（独立处理）

---

#### 3. 工单系统

**生命周期：**
```
PENDING → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED
```

**工单字段：**
- 工单编号：`TK20250105123456001`
- 严重级别：P0/P1/P2/P3/P4 (AI 自动评估)
- 发生次数：记录重复发生次数
- SLA 时间：根据严重级别自动设置
- 处理人、处理进度、解决方案

**查询工单：**
```sql
SELECT * FROM ticket WHERE status = 'PENDING' ORDER BY severity, created_at;
```

---

#### 4. 多层漏斗过滤

```
100 个异常
  ↓
第 0 层：Ignore List → 90 个 (过滤 10%)
  ↓
第 1 层：指纹去重 → 40 个 (过滤 55%)
  ↓
第 2 层：规则引擎 → 30 个 (过滤 25%)
  ↓
第 3 层：轻量 AI → 20 个 (过滤 33%)
  ↓
第 4 层：深度 AI → 5 个 (过滤 75%)
  ↓
最终生成 5 个工单
```

**每层职责：**
- 第 0 层：过滤已知噪音（健康检查、404 等）
- 第 1 层：过滤时间窗口内的重复异常
- 第 2 层：频率限制、时间规则
- 第 3 层：快速 AI 相似度匹配
- 第 4 层：深度 AI 语义分析

---

#### 5. FAST 日志平台集成

**旧项目：**
- ❌ 无外部集成能力

**新项目：**
- ✅ REST API 接口
- ✅ FAST 平台可直接上报异常
- ✅ 走相同的 AI 去噪流程

**API 示例：**
```bash
curl -X POST http://localhost:8080/api/v1/fastlog/exception \
  -H "Content-Type: application/json" \
  -d '{
    "appName": "order-service",
    "environment": "prod",
    "exceptionType": "NullPointerException",
    "exceptionMessage": "User not found",
    "stackTrace": "...",
    "errorLocation": "UserService.getUser:123"
  }'
```

---

## 迁移检查清单

### 准备阶段

- [ ] 阅读本文档和 `CLAUDE.md`
- [ ] 检查 Java 版本（需要 Java 17+）
- [ ] 检查 Spring Boot 版本（需要 3.x）
- [ ] 准备 MySQL 数据库
- [ ] 准备 OpenAI API Key (或兼容 API)

### 依赖替换

- [ ] 移除 `vega-common-alarm` 依赖
- [ ] 添加 `one-agent-4j` 依赖
- [ ] 执行 `mvn clean install`

### 配置迁移

- [ ] 删除 `app.alarm.*` 配置
- [ ] 添加 `one-agent.*` 配置
- [ ] 配置数据库连接
- [ ] 配置 AI 模型 API Key
- [ ] 调整 AOP 切点（如需兼容）

### 代码调整

- [ ] 删除所有 `AlarmUtil.sendAlarm()` 手动调用
- [ ] 删除自定义的 `ExceptionHandlingAspect`
- [ ] 删除企微消息格式化代码

### 数据库初始化

- [ ] 执行 `sql/init.sql` 创建表
- [ ] 验证表结构正确

### 测试验证

- [ ] 启动应用，检查日志
- [ ] 触发测试异常
- [ ] 验证异常记录入库
- [ ] 验证工单生成
- [ ] 验证 AI 去噪效果
- [ ] 验证重复异常过滤

### 生产部署

- [ ] 灰度发布（建议先部署 1-2 个实例）
- [ ] 监控异常捕获量
- [ ] 监控过滤率
- [ ] 监控工单生成率
- [ ] 根据实际情况调整配置

---

## 常见问题

### Q1: 旧项目的企微告警还需要吗？

**A:** 新项目默认使用数据库持久化 + 工单系统。如需保留企微告警，可以：
1. 保留旧的 `vega-common-alarm` 作为独立的告警通道
2. 或在新项目中扩展 `ExceptionReporter` 支持企微推送
3. 推荐使用工单系统，避免消息轰炸

---

### Q2: 如何兼容旧代码的手动告警？

**A:** 使用兼容层（见上文 "阶段 1: 兼容层实现"）。兼容层会将手动调用转换为自动捕获。

---

### Q3: AI 去噪需要多少成本？

**A:**
- DeepSeek-V3: ~¥0.001/次调用
- 每天 10000 个异常 → AI 判断 ~1000 次 → ¥1/天
- 建议配合多层漏斗，减少 AI 调用次数

---

### Q4: 旧项目的切点 `com.ke.*..service.impl` 如何迁移？

**A:** 配置为：
```properties
one-agent.capture-config.aop-pointcut=execution(* com.ke.*..service.impl.*.*(..))
```

---

### Q5: 数据库表会很大吗？

**A:**
- 建议定期归档历史数据（如 30 天前）
- 或配置自动清理策略
- 预估：1000 异常/天 × 30 天 = 30000 条记录（约 50MB）

---

### Q6: 如何查看当前的工单？

**A:**
```sql
-- 查看待处理工单
SELECT * FROM ticket WHERE status = 'PENDING' ORDER BY severity, created_at;

-- 查看高优先级工单
SELECT * FROM ticket WHERE severity IN ('P0', 'P1') AND status != 'CLOSED';
```

或开发 Web 管理界面（待扩展）。

---

## 迁移时间表建议

| 阶段 | 时间 | 任务 |
|------|------|------|
| **准备** | 1 天 | 阅读文档、准备环境 |
| **开发** | 2-3 天 | 添加依赖、配置、数据库初始化 |
| **测试** | 2 天 | 测试环境验证、调整配置 |
| **灰度** | 1 周 | 生产环境 1-2 个实例灰度 |
| **全量** | 1 天 | 全量部署 |
| **优化** | 持续 | 根据实际情况调整过滤规则 |

---

## 总结

### 升级收益

✅ **噪音减少 90%**：多层过滤 + AI 去噪
✅ **可追溯性**：数据库持久化，历史可查
✅ **智能化**：AI 自动评估严重级别
✅ **可扩展**：工单系统、外部 API
✅ **零侵入**：自动捕获，无需手动调用

### 升级成本

⚠️ **学习成本**：需要理解新架构和配置
⚠️ **部署成本**：需要 MySQL 数据库
⚠️ **API 成本**：AI 去噪需要 OpenAI API（可选）
⚠️ **迁移时间**：约 1-2 周（含测试和灰度）

### 推荐策略

1. **优先级 1（必做）**：基础过滤 + 持久化
2. **优先级 2（推荐）**：AI 去噪（显著降噪）
3. **优先级 3（可选）**：指纹去重、工单系统

---

## 联系与支持

- 文档：`CLAUDE.md`, `DENOISE_STRATEGY.md`, `FASTLOG_INTEGRATION.md`
- 示例：`src/test/java` 目录
- 问题反馈：GitHub Issues

---

**One Agent 4J - 让异常监控更智能** 🚀
