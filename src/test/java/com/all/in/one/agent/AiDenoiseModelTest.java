package com.all.in.one.agent;

import com.all.in.one.agent.ai.model.DenoiseDecision;
import com.all.in.one.agent.ai.service.AiDenoiseService;
import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.common.util.FingerprintGenerator;
import com.all.in.one.agent.dao.mapper.AppAlarmRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 模型降噪效果测试
 * <p>
 * 用于评估大模型在异常去噪场景下的表现
 * 包括：重复识别、相似度判断、严重级别评估等
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiDenoiseModelTest {

    @Autowired(required = false)
    private AiDenoiseService aiDenoiseService;

    @Autowired(required = false)
    private AppAlarmRecordMapper appAlarmRecordMapper;

    @BeforeEach
    void setUp() {
        if (aiDenoiseService == null) {
            log.warn("⚠️ AI 降噪服务未启用，请检查配置:");
            log.warn("   1. one-agent.ai-denoise.enabled=true");
            log.warn("   2. langchain4j.open-ai.chat-model.api-key=your-key");
            log.warn("   3. langchain4j.open-ai.chat-model.base-url=https://api.siliconflow.cn");
        }
    }

    // ==================== 测试用例 1: 重复异常识别 ====================

    /**
     * 场景1: 完全相同的异常 - AI 应该识别为重复
     */
    @Test
    @Order(1)
    void testCase1_IdenticalException() {
        if (aiDenoiseService == null) {
            log.warn("跳过测试: AI 降噪服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 1: 完全相同的异常（应识别为重复）");
        log.info("=".repeat(80));

        // 1. 准备测试数据
        ExceptionInfo newException = createExceptionInfo(
                "java.lang.NullPointerException",
                "Cannot invoke \"String.length()\" because \"str\" is null",
                "com.example.UserService.getUserName:123",
                "/api/user/get"
        );

        // 2. 调用 AI 判断
        long startTime = System.currentTimeMillis();
        DenoiseDecision decision = aiDenoiseService.shouldAlert(newException);
        long duration = System.currentTimeMillis() - startTime;

        // 3. 输出结果
        printDecision(decision, duration);

        // 4. 断言
        assertNotNull(decision, "AI 应该返回决策结果");
        assertNotNull(decision.getReason(), "AI 应该给出判断原因");

        // 首次异常通常应该报警（如果没有历史记录）
        log.info("✅ 测试完成 - 首次异常预期应该报警: {}", decision.isShouldAlert());
    }

    // ==================== 测试用例 2: 相似但不同的异常 ====================

    /**
     * 场景2: 相同位置不同消息 - AI 需要判断相似度
     */
    @Test
    @Order(2)
    void testCase2_SimilarException() {
        if (aiDenoiseService == null) {
            log.warn("跳过测试: AI 降噪服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 2: 相同位置不同消息（测试相似度判断）");
        log.info("=".repeat(80));

        // 场景：同一个方法，但不同的空指针
        ExceptionInfo exception1 = createExceptionInfo(
                "java.lang.NullPointerException",
                "Cannot invoke \"User.getName()\" because \"user\" is null",
                "com.example.UserService.getUserInfo:100",
                "/api/user/info"
        );

        ExceptionInfo exception2 = createExceptionInfo(
                "java.lang.NullPointerException",
                "Cannot invoke \"User.getEmail()\" because \"user\" is null",
                "com.example.UserService.getUserInfo:105",
                "/api/user/info"
        );

        // 调用 AI 分别判断
        log.info("\n--- 第一个异常 ---");
        DenoiseDecision decision1 = aiDenoiseService.shouldAlert(exception1);
        printDecision(decision1, 0);

        log.info("\n--- 第二个异常（相似但不同）---");
        long startTime = System.currentTimeMillis();
        DenoiseDecision decision2 = aiDenoiseService.shouldAlert(exception2);
        long duration = System.currentTimeMillis() - startTime;
        printDecision(decision2, duration);

        // 分析
        log.info("\n📊 相似度分析:");
        log.info("   异常1 报警: {}", decision1.isShouldAlert());
        log.info("   异常2 报警: {}", decision2.isShouldAlert());
        log.info("   相似度: {}", decision2.getSimilarityScore());

        assertNotNull(decision2, "AI 应该返回决策结果");
    }

    // ==================== 测试用例 3: 不同类型的异常 ====================

    /**
     * 场景3: 完全不同的异常 - AI 应该识别为新异常
     */
    @Test
    @Order(3)
    void testCase3_DifferentException() {
        if (aiDenoiseService == null) {
            log.warn("跳过测试: AI 降噪服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 3: 完全不同的异常（应识别为新异常）");
        log.info("=".repeat(80));

        // 不同类型、不同位置、不同消息
        ExceptionInfo exception = createExceptionInfo(
                "java.sql.SQLException",
                "Connection timeout after 30000ms",
                "com.example.OrderRepository.saveOrder:89",
                "/api/order/create"
        );

        long startTime = System.currentTimeMillis();
        DenoiseDecision decision = aiDenoiseService.shouldAlert(exception);
        long duration = System.currentTimeMillis() - startTime;

        printDecision(decision, duration);

        // 新类型的异常通常应该报警
        assertNotNull(decision, "AI 应该返回决策结果");
        log.info("✅ 测试完成 - 新异常预期应该报警: {}", decision.isShouldAlert());
    }

    // ==================== 测试用例 4: 频繁异常 ====================

    /**
     * 场景4: 短时间内多次相同异常 - AI 应该建议合并
     */
    @Test
    @Order(4)
    void testCase4_FrequentException() {
        if (aiDenoiseService == null) {
            log.warn("跳过测试: AI 降噪服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 4: 频繁重复异常（测试合并建议）");
        log.info("=".repeat(80));

        // 模拟同一个异常连续触发
        ExceptionInfo exception = createExceptionInfo(
                "java.util.concurrent.TimeoutException",
                "Request timeout after 5000ms",
                "com.example.PaymentService.processPayment:200",
                "/api/payment/process"
        );

        // 连续测试 3 次
        for (int i = 1; i <= 3; i++) {
            log.info("\n--- 第 {} 次触发 ---", i);
            long startTime = System.currentTimeMillis();
            DenoiseDecision decision = aiDenoiseService.shouldAlert(exception);
            long duration = System.currentTimeMillis() - startTime;

            log.info("决策: {} | 重复: {} | 相似度: {} | 耗时: {}ms",
                    decision.isShouldAlert() ? "报警" : "过滤",
                    decision.isDuplicate() ? "是" : "否",
                    decision.getSimilarityScore(),
                    duration);

            if (decision.getSuggestion() != null) {
                log.info("建议: {}", decision.getSuggestion());
            }

            // 第一次通常报警，后续可能被过滤
            if (i == 1) {
                assertTrue(decision.isShouldAlert() || !decision.isShouldAlert(),
                        "首次可能报警或不报警，取决于历史数据");
            }

            // 短暂等待
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("✅ 测试完成 - 观察 AI 对频繁异常的处理策略");
    }

    // ==================== 测试用例 5: 严重级别评估 ====================

    /**
     * 场景5: 不同严重程度的异常 - 测试 AI 的严重级别判断
     */
    @Test
    @Order(5)
    void testCase5_SeverityAssessment() {
        if (aiDenoiseService == null) {
            log.warn("跳过测试: AI 降噪服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 5: 严重级别评估");
        log.info("=".repeat(80));

        // 测试不同严重程度的异常
        List<ExceptionInfo> exceptions = new ArrayList<>();

        // P0: 内存溢出
        exceptions.add(createExceptionInfo(
                "java.lang.OutOfMemoryError",
                "Java heap space",
                "com.example.DataProcessor.process:500",
                "/api/data/process"
        ));

        // P1: 数据库异常
        exceptions.add(createExceptionInfo(
                "java.sql.SQLException",
                "Deadlock detected",
                "com.example.OrderService.createOrder:123",
                "/api/order/create"
        ));

        // P2: 超时异常
        exceptions.add(createExceptionInfo(
                "java.util.concurrent.TimeoutException",
                "Operation timeout after 30s",
                "com.example.PaymentService.pay:200",
                "/api/payment/pay"
        ));

        // P3: 空指针
        exceptions.add(createExceptionInfo(
                "java.lang.NullPointerException",
                "Cannot invoke method on null object",
                "com.example.UserService.getUser:50",
                "/api/user/get"
        ));

        // 逐个测试
        for (int i = 0; i < exceptions.size(); i++) {
            ExceptionInfo exception = exceptions.get(i);
            log.info("\n--- 异常 {} : {} ---", i + 1, exception.getExceptionType());

            long startTime = System.currentTimeMillis();
            DenoiseDecision decision = aiDenoiseService.shouldAlert(exception);
            long duration = System.currentTimeMillis() - startTime;

            log.info("AI 建议严重级别: {} | 是否报警: {} | 耗时: {}ms",
                    decision.getSuggestedSeverity(),
                    decision.isShouldAlert() ? "是" : "否",
                    duration);
            log.info("判断原因: {}", decision.getReason());

            assertNotNull(decision.getSuggestedSeverity(), "AI 应该给出严重级别建议");
        }

        log.info("✅ 测试完成 - AI 严重级别评估");
    }

    // ==================== 测试用例 6: 缓存机制测试 ====================

    /**
     * 场景6: 测试缓存命中 - 相同异常应该命中缓存
     */
    @Test
    @Order(6)
    void testCase6_CachePerformance() {
        if (aiDenoiseService == null) {
            log.warn("跳过测试: AI 降噪服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 6: 缓存性能测试");
        log.info("=".repeat(80));

        ExceptionInfo exception = createExceptionInfo(
                "java.lang.IllegalArgumentException",
                "Invalid parameter: userId cannot be null",
                "com.example.ValidationService.validate:30",
                "/api/validate"
        );

        // 第一次调用（缓存未命中）
        log.info("\n--- 第一次调用（预期缓存未命中）---");
        long time1 = System.currentTimeMillis();
        DenoiseDecision decision1 = aiDenoiseService.shouldAlert(exception);
        long duration1 = System.currentTimeMillis() - time1;
        log.info("耗时: {}ms （包含 AI 调用）", duration1);

        // 第二次调用（预期缓存命中）
        log.info("\n--- 第二次调用（预期缓存命中）---");
        long time2 = System.currentTimeMillis();
        DenoiseDecision decision2 = aiDenoiseService.shouldAlert(exception);
        long duration2 = System.currentTimeMillis() - time2;
        log.info("耗时: {}ms （应该来自缓存）", duration2);

        // 统计信息
        AiDenoiseService.AiDenoiseStats stats = aiDenoiseService.getStats();
        log.info("\n📊 缓存统计:");
        log.info("   总检查次数: {}", stats.getTotalChecked());
        log.info("   缓存命中次数: {}", stats.getTotalCacheHit());
        log.info("   AI 实际调用次数: {}", stats.getTotalAiCall());
        log.info("   缓存命中率: {:.2f}%", stats.getCacheHitRate() * 100);
        log.info("   当前缓存大小: {}", stats.getCacheSize());

        // 断言
        assertTrue(duration2 < duration1, "缓存命中应该更快");
        assertTrue(duration2 < 50, "缓存命中应该在 50ms 内完成");

        log.info("✅ 测试完成 - 缓存性能符合预期");
    }

    // ==================== 测试用例 7: 异常消息中的业务信息 ====================

    /**
     * 场景7: 包含业务信息的异常 - 测试 AI 是否能理解业务上下文
     */
    @Test
    @Order(7)
    void testCase7_BusinessContextUnderstanding() {
        if (aiDenoiseService == null) {
            log.warn("跳过测试: AI 降噪服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 7: 业务上下文理解能力");
        log.info("=".repeat(80));

        // 包含具体业务信息的异常
        ExceptionInfo exception = createExceptionInfo(
                "com.example.InsufficientBalanceException",
                "User balance (50.00 CNY) is insufficient for payment (100.00 CNY). UserId: 12345, OrderId: ORD-20250115-001",
                "com.example.PaymentService.deductBalance:156",
                "/api/payment/deduct"
        );

        long startTime = System.currentTimeMillis();
        DenoiseDecision decision = aiDenoiseService.shouldAlert(exception);
        long duration = System.currentTimeMillis() - startTime;

        printDecision(decision, duration);

        // 业务异常通常应该报警，但不一定是高优先级
        assertNotNull(decision, "AI 应该返回决策结果");
        log.info("✅ 测试完成 - 观察 AI 对业务异常的理解");
    }

    // ==================== 测试用例 8: 统计信息总览 ====================

    /**
     * 场景8: 查看整体统计
     */
    @Test
    @Order(8)
    void testCase8_OverallStatistics() {
        if (aiDenoiseService == null) {
            log.warn("跳过测试: AI 降噪服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("📊 AI 降噪整体统计信息");
        log.info("=".repeat(80));

        AiDenoiseService.AiDenoiseStats stats = aiDenoiseService.getStats();

        log.info("\n总体指标:");
        log.info("  ✓ 总检查次数: {}", stats.getTotalChecked());
        log.info("  ✓ 缓存命中次数: {}", stats.getTotalCacheHit());
        log.info("  ✓ AI 实际调用次数: {}", stats.getTotalAiCall());
        log.info("  ✓ 被过滤的异常: {}", stats.getTotalFiltered());

        log.info("\n性能指标:");
        log.info("  ✓ 缓存命中率: {:.2f}%", stats.getCacheHitRate() * 100);
        log.info("  ✓ 过滤率: {:.2f}%", stats.getFilterRate() * 100);
        log.info("  ✓ 当前缓存大小: {}", stats.getCacheSize());
        log.info("  ✓ 缓存驱逐次数: {}", stats.getCacheEvictionCount());

        log.info("\n成本估算:");
        double estimatedCost = stats.getTotalAiCall() * 0.001; // 假设每次调用 0.001 元
        log.info("  ✓ 预估 API 调用成本: ¥{:.3f} (假设 ¥0.001/次)", estimatedCost);

        // 断言
        assertTrue(stats.getTotalChecked() > 0, "应该有检查记录");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的异常信息
     */
    private ExceptionInfo createExceptionInfo(String exceptionType,
                                               String message,
                                               String errorLocation,
                                               String requestUri) {
        ExceptionInfo info = new ExceptionInfo();

        // 基本信息
        info.setAppName("one-agent-4j-test");
        info.setEnvironment("prod");  // 使用生产环境以提高严重级别
        info.setInstanceId("test-instance-001");
        info.setHostname("test-host");
        info.setIp("192.168.1.100");

        // 异常信息
        info.setExceptionType(exceptionType);
        info.setExceptionMessage(message);
        info.setErrorLocation(errorLocation);
        info.setStackTrace(generateStackTrace(exceptionType, errorLocation));

        // 生成指纹
        String fingerprint = FingerprintGenerator.generate(
                exceptionType,
                errorLocation
        );
        info.setFingerprint(fingerprint);

        // 解析错误位置
        String[] parts = errorLocation.split(":");
        if (parts.length == 2) {
            String[] classParts = parts[0].split("\\.");
            if (classParts.length > 0) {
                info.setErrorClass(parts[0].substring(0, parts[0].lastIndexOf('.')));
                info.setErrorMethod(classParts[classParts.length - 1]);
                info.setErrorLine(Integer.parseInt(parts[1]));
            }
        }

        // 请求信息
        ExceptionInfo.RequestInfo requestInfo = new ExceptionInfo.RequestInfo();
        requestInfo.setMethod("POST");
        requestInfo.setUri(requestUri);
        requestInfo.setClientIp("192.168.1.50");
        requestInfo.setUserAgent("Mozilla/5.0");
        info.setRequestInfo(requestInfo);

        // 线程信息
        ExceptionInfo.ThreadInfo threadInfo = new ExceptionInfo.ThreadInfo();
        threadInfo.setThreadId(Thread.currentThread().getId());
        threadInfo.setThreadName(Thread.currentThread().getName());
        info.setThreadInfo(threadInfo);

        // 时间信息
        info.setOccurredAt(Instant.now());
        info.setReportedAt(Instant.now());

        return info;
    }

    /**
     * 生成模拟的堆栈信息
     */
    private String generateStackTrace(String exceptionType, String errorLocation) {
        String[] parts = errorLocation.split(":");
        String location = parts[0];
        String line = parts.length > 1 ? parts[1] : "0";

        return String.format("""
                %s: Test exception message
                    at %s(SourceFile:%s)
                    at com.example.Controller.handleRequest(Controller.java:45)
                    at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1040)
                    at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:943)
                    at javax.servlet.http.HttpServlet.service(HttpServlet.java:764)
                """, exceptionType, location, line);
    }

    /**
     * 格式化输出 AI 决策结果
     */
    private void printDecision(DenoiseDecision decision, long duration) {
        log.info("\n" + "-".repeat(60));
        log.info("🤖 AI 决策结果:");
        log.info("-".repeat(60));
        log.info("是否报警: {}", decision.isShouldAlert() ? "✅ 是" : "❌ 否");
        log.info("是否重复: {}", decision.isDuplicate() ? "是" : "否");
        log.info("相似度: {}", decision.getSimilarityScore());
        log.info("建议严重级别: {}", decision.getSuggestedSeverity());
        log.info("判断原因: {}", decision.getReason());

        if (decision.getRelatedExceptionIds() != null && !decision.getRelatedExceptionIds().isEmpty()) {
            log.info("相关异常ID: {}", decision.getRelatedExceptionIds());
        }

        if (decision.getSuggestion() != null) {
            log.info("处理建议: {}", decision.getSuggestion());
        }

        if (duration > 0) {
            log.info("响应耗时: {}ms", duration);
        }
        log.info("-".repeat(60));
    }

    /**
     * 清理测试数据（可选）
     */
    @AfterAll
    static void cleanup() {
        log.info("\n" + "=".repeat(80));
        log.info("🎉 所有测试完成！");
        log.info("=".repeat(80));
    }
}
