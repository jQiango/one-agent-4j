# 第 0 层：基础过滤层（Ignore List）实现文档

## 实现概述

第 0 层是多层漏斗去噪机制的第一道防线，通过静态黑名单快速过滤明确不需要处理的异常。

**预期效果：** 过滤约 10% 的异常噪音
**性能要求：** < 1ms
**实现方式：** 基于配置的规则匹配

---

## 已实现的组件

### 1. 配置类：`IgnoreListProperties`

**文件位置：** `src/main/java/com/all/in/one/agent/starter/filter/IgnoreListProperties.java`

**功能：**
- 使用 `@ConfigurationProperties` 从配置文件加载过滤规则
- 支持 7 种过滤维度：异常类型、包前缀、错误位置、应用名、环境、消息关键词、HTTP 状态码

**配置前缀：** `one-agent.ignore-list`

**支持的配置项：**
```properties
one-agent.ignore-list.enabled=true                          # 是否启用
one-agent.ignore-list.exception-types=...                   # 异常类型黑名单
one-agent.ignore-list.package-prefixes=...                  # 包前缀黑名单
one-agent.ignore-list.error-locations=...                   # 错误位置黑名单（支持通配符）
one-agent.ignore-list.app-names=...                         # 应用名称黑名单
one-agent.ignore-list.environments=...                      # 环境黑名单
one-agent.ignore-list.message-keywords=...                  # 消息关键词黑名单
one-agent.ignore-list.http-status-codes=...                 # HTTP 状态码黑名单
```

---

### 2. 过滤器：`IgnoreListFilter`

**文件位置：** `src/main/java/com/all/in/one/agent/starter/filter/IgnoreListFilter.java`

**核心方法：**
```java
public boolean shouldIgnore(ExceptionInfo exceptionInfo)
```

**过滤逻辑：**
1. 检查应用名称
2. 检查环境
3. 检查异常类型（支持完整类名和简单类名）
4. 检查包前缀
5. 检查错误位置（支持精确匹配和通配符 `*`）
6. 检查异常消息关键词（不区分大小写）
7. 检查 HTTP 状态码

**统计功能：**
```java
FilterStats getStats()     // 获取过滤统计
void resetStats()          // 重置统计
```

**统计指标：**
- `totalChecked`: 总检查次数
- `totalFiltered`: 已过滤次数
- `filterRate`: 过滤率

---

### 3. 集成到 `ExceptionCollector`

**修改文件：** `src/main/java/com/all/in/one/agent/starter/collector/ExceptionCollector.java`

**集成点：**
```java
// 构建 ExceptionInfo 后，立即进行第 0 层过滤
ExceptionInfo exceptionInfo = ExceptionInfoBuilder.build(...);

if (ignoreListFilter.shouldIgnore(exceptionInfo)) {
    log.debug("异常被第 0 层过滤");
    return;  // 直接过滤，不再继续处理
}
```

**过滤流程：**
```
异常捕获
  ↓
快速过滤（基于 Throwable）  ← 旧逻辑，保留
  ↓
构建 ExceptionInfo
  ↓
第 0 层：基础过滤          ← 新增
  ↓
第 1 层：指纹去重          ← 待实现
  ↓
AI 去噪
  ↓
持久化 + 工单生成
```

---

### 4. 自动配置：`AgentAutoConfiguration`

**修改文件：** `src/main/java/com/all/in/one/agent/starter/autoconfigure/AgentAutoConfiguration.java`

**Bean 依赖关系：**
```
IgnoreListProperties (自动注入)
       ↓
IgnoreListFilter
       ↓
ExceptionCollector
```

---

### 5. 默认配置

**文件位置：** `src/main/resources/application.properties`

**默认过滤规则：**
```properties
# 启用第 0 层过滤
one-agent.ignore-list.enabled=true

# 忽略常见噪音异常
one-agent.ignore-list.exception-types=AccessDeniedException,NoHandlerFoundException,HttpRequestMethodNotSupportedException

# 忽略监控和文档组件
one-agent.ignore-list.package-prefixes=org.springframework.boot.actuate,springfox.documentation

# 忽略健康检查接口
one-agent.ignore-list.error-locations=*.health,*.heartbeat,*.ping

# 忽略健康检查相关消息
one-agent.ignore-list.message-keywords=health check,heartbeat,actuator,swagger

# 忽略客户端错误状态码
one-agent.ignore-list.http-status-codes=404,401,403
```

---

## 使用示例

### 示例 1: 忽略特定异常类型

```properties
# 忽略 NullPointerException（不推荐，仅作演示）
one-agent.ignore-list.exception-types=NullPointerException
```

**效果：**
```java
try {
    String s = null;
    s.length();  // NullPointerException
} catch (Exception e) {
    collector.collect(e);  // ✓ 被第 0 层过滤
}
```

---

### 示例 2: 忽略测试包的异常

```properties
# 忽略所有测试代码的异常
one-agent.ignore-list.package-prefixes=com.example.test,org.junit
```

**效果：**
```java
package com.example.test;

public class TestService {
    public void test() {
        throw new RuntimeException("test");  // ✓ 被过滤
    }
}
```

---

### 示例 3: 忽略健康检查接口

```properties
# 使用通配符忽略所有 health 方法
one-agent.ignore-list.error-locations=*.health,*.heartbeat
```

**效果：**
```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public String health() {
        throw new RuntimeException();  // ✓ 被过滤
    }

    @GetMapping("/api/users")
    public String users() {
        throw new RuntimeException();  // ✗ 不被过滤
    }
}
```

---

### 示例 4: 忽略本地开发环境

```properties
# 本地开发环境不上报异常
one-agent.ignore-list.environments=local,dev
```

**效果：**
- `spring.profiles.active=local` → 所有异常被过滤
- `spring.profiles.active=prod` → 正常处理

---

### 示例 5: 忽略包含特定关键词的异常

```properties
# 忽略包含 "timeout" 的超时异常
one-agent.ignore-list.message-keywords=timeout,connection reset
```

**效果：**
```java
throw new RuntimeException("Database connection timeout");  // ✓ 被过滤
throw new RuntimeException("User not found");               // ✗ 不被过滤
```

---

## 通配符支持

### 错误位置通配符

```properties
# 忽略所有 health 方法
one-agent.ignore-list.error-locations=*.health

# 忽略 HealthController 的所有方法
one-agent.ignore-list.error-locations=HealthController.*

# 精确匹配
one-agent.ignore-list.error-locations=com.example.HealthController.check
```

**匹配规则：**
- `*.methodName`: 匹配所有类的指定方法
- `ClassName.*`: 匹配指定类的所有方法
- `full.package.ClassName.methodName`: 精确匹配

---

## 监控和调优

### 1. 查看过滤统计

```java
@Autowired
private IgnoreListFilter ignoreListFilter;

@Scheduled(fixedRate = 60000)
public void logFilterStats() {
    FilterStats stats = ignoreListFilter.getStats();
    log.info("第 0 层过滤统计: 总检查={}, 已过滤={}, 过滤率={:.2f}%",
        stats.getTotalChecked(),
        stats.getTotalFiltered(),
        stats.getFilterRate() * 100
    );
}
```

---

### 2. 调整过滤规则

**观察日志：**
```
异常被第 0 层过滤 - fingerprint=xxx, type=AccessDeniedException, location=SecurityFilter.doFilter:45
```

**根据日志调整：**
- 如果误杀了重要异常 → 从黑名单中移除
- 如果还有噪音异常 → 添加到黑名单

---

### 3. 性能验证

**目标：** 每次检查 < 1ms

**测试方法：**
```java
long start = System.nanoTime();
boolean ignored = ignoreListFilter.shouldIgnore(exceptionInfo);
long elapsed = System.nanoTime() - start;
log.info("过滤耗时: {} ns ({} ms)", elapsed, elapsed / 1_000_000.0);
```

**优化建议：**
- 黑名单不要过大（< 100 项）
- 优先使用异常类型和包前缀过滤（最快）
- 避免复杂的正则表达式

---

## 最佳实践

### 1. 分层过滤策略

```properties
# 第一优先级：异常类型（最快）
one-agent.ignore-list.exception-types=AccessDeniedException

# 第二优先级：包前缀（快）
one-agent.ignore-list.package-prefixes=org.springframework.security

# 第三优先级：错误位置（中等）
one-agent.ignore-list.error-locations=*.health

# 最后：消息关键词（较慢，但灵活）
one-agent.ignore-list.message-keywords=health check
```

---

### 2. 环境差异化配置

```properties
# application.properties (所有环境)
one-agent.ignore-list.exception-types=AccessDeniedException

# application-dev.properties (开发环境)
one-agent.ignore-list.environments=local,dev

# application-prod.properties (生产环境)
one-agent.ignore-list.http-status-codes=404,401
```

---

### 3. 动态调整

**Spring Boot Actuator 端点（可选扩展）：**
```java
@RestController
@RequestMapping("/actuator/one-agent")
public class OneAgentEndpoint {

    @PostMapping("/ignore-list/add")
    public void addIgnoreRule(@RequestParam String type,
                              @RequestParam String value) {
        // 动态添加过滤规则
    }

    @GetMapping("/ignore-list/stats")
    public FilterStats getStats() {
        return ignoreListFilter.getStats();
    }
}
```

---

## 常见问题

### Q1: 过滤规则不生效？

**检查清单：**
1. 确认 `one-agent.ignore-list.enabled=true`
2. 检查配置文件是否正确加载
3. 查看启动日志中的配置信息：
   ```
   ========== 第 0 层：基础过滤配置 ==========
   忽略异常类型: [...]
   ...
   ```
4. 确认异常信息格式匹配

---

### Q2: 过滤率太低（< 5%）？

**可能原因：**
- 黑名单规则太少
- 规则不匹配实际异常

**解决方案：**
1. 查看日志，找出高频噪音异常
2. 添加对应的过滤规则
3. 使用通配符扩大匹配范围

---

### Q3: 误杀了重要异常？

**排查方法：**
1. 检查日志：`异常被第 0 层过滤`
2. 确认是哪个规则导致的
3. 从黑名单中移除或细化规则

**示例：**
```properties
# 错误：忽略所有 RuntimeException（太宽泛）
one-agent.ignore-list.exception-types=RuntimeException

# 正确：只忽略特定子类
one-agent.ignore-list.exception-types=AccessDeniedException,NoHandlerFoundException
```

---

### Q4: 如何测试过滤规则？

**测试代码：**
```java
@SpringBootTest
class IgnoreListFilterTest {

    @Autowired
    private IgnoreListFilter filter;

    @Test
    void testFilterHealthCheck() {
        ExceptionInfo info = ExceptionInfo.builder()
            .exceptionType("RuntimeException")
            .errorLocation("HealthController.health:10")
            .build();

        boolean ignored = filter.shouldIgnore(info);
        assertTrue(ignored, "健康检查异常应该被过滤");
    }
}
```

---

## 下一步

第 0 层已实现完成，接下来可以实现：

### 第 1 层：指纹去重（Fingerprint Dedup）
- 使用 Caffeine Cache 实现时间窗口去重
- 预期过滤率：40-60%
- 参考文档：`DENOISE_STRATEGY.md` 第 1 层

### 第 2 层：规则引擎（Rule Engine）
- 频率限制、时间窗口、环境规则
- 预期过滤率：20-30%

### 第 3 层：轻量 AI（Fast AI）
- 使用嵌入模型或小模型
- 预期过滤率：10-20%

### 第 4 层：深度 AI（Deep AI）
- 当前已实现（LLM 判断）
- 预期过滤率：最终 5-10% 通过

---

## 总结

✅ **已完成：**
- IgnoreListProperties 配置类
- IgnoreListFilter 过滤器
- 集成到 ExceptionCollector
- 默认配置和文档

✅ **核心特性：**
- 7 种过滤维度
- 通配符支持
- 统计功能
- 性能优化（< 1ms）

✅ **预期效果：**
- 过滤 ~10% 的明显噪音异常
- 为后续层级减轻负担
- 提升整体系统性能

🎯 **验证方式：**
1. 启动应用，查看启动日志
2. 触发异常，观察过滤日志
3. 定期检查过滤统计
4. 根据实际情况调整规则
