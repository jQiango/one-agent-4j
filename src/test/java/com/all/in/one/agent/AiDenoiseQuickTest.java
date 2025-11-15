package com.all.in.one.agent;

import com.all.in.one.agent.ai.model.DenoiseDecision;
import com.all.in.one.agent.ai.service.AiDenoiseService;
import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.common.util.FingerprintGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

/**
 * AI 降噪快速测试
 * <p>
 * 用于快速验证单个场景，方便调试和迭代
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@SpringBootTest
class AiDenoiseQuickTest {

    @Autowired(required = false)
    private AiDenoiseService aiDenoiseService;

    /**
     * 快速测试：自定义异常场景
     *
     * 使用方法：
     * 1. 修改下面的异常信息
     * 2. 运行测试
     * 3. 查看 AI 的判断结果
     */
    @Test
    void quickTest_CustomException() {
        if (aiDenoiseService == null) {
            log.error("❌ AI 降噪服务未启用！");
            log.info("请检查配置:");
            log.info("  1. one-agent.ai-denoise.enabled=true");
            log.info("  2. langchain4j.open-ai.chat-model.api-key=<your-key>");
            log.info("  3. langchain4j.open-ai.chat-model.base-url=https://api.siliconflow.cn");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🚀 AI 降噪快速测试");
        log.info("=".repeat(80));

        // ========== 在这里修改你要测试的异常信息 ==========

        String exceptionType = "java.lang.NullPointerException";
        String exceptionMessage = "Cannot invoke \"User.getName()\" because \"user\" is null";
        String errorLocation = "com.example.UserService.getUserInfo:100";
        String requestUri = "/api/user/info";
        String environment = "prod";  // dev/test/uat/prod

        // ====================================================

        // 创建异常信息
        ExceptionInfo exceptionInfo = createExceptionInfo(
                exceptionType,
                exceptionMessage,
                errorLocation,
                requestUri,
                environment
        );

        log.info("\n📋 测试异常信息:");
        log.info("  类型: {}", exceptionType);
        log.info("  消息: {}", exceptionMessage);
        log.info("  位置: {}", errorLocation);
        log.info("  接口: {}", requestUri);
        log.info("  环境: {}", environment);
        log.info("  指纹: {}", exceptionInfo.getFingerprint());

        // 调用 AI 判断
        log.info("\n⏳ 正在调用 AI 进行分析...");
        long startTime = System.currentTimeMillis();

        DenoiseDecision decision = aiDenoiseService.shouldAlert(exceptionInfo);

        long duration = System.currentTimeMillis() - startTime;

        // 输出结果
        printDetailedResult(decision, duration);

        // 输出统计信息
        printStatistics();
    }

    /**
     * 批量测试：多个相似异常
     */
    @Test
    void quickTest_BatchSimilarExceptions() {
        if (aiDenoiseService == null) {
            log.error("❌ AI 降噪服务未启用！");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🔄 批量测试：相似异常");
        log.info("=".repeat(80));

        // 测试 5 个相似的异常
        for (int i = 1; i <= 5; i++) {
            log.info("\n--- 测试第 {} 个异常 ---", i);

            ExceptionInfo exception = createExceptionInfo(
                    "java.lang.NullPointerException",
                    String.format("Cannot invoke method on null object [attempt-%d]", i),
                    "com.example.UserService.getUser:123",
                    "/api/user/get",
                    "prod"
            );

            long startTime = System.currentTimeMillis();
            DenoiseDecision decision = aiDenoiseService.shouldAlert(exception);
            long duration = System.currentTimeMillis() - startTime;

            log.info("结果: {} | 重复: {} | 相似度: {} | 耗时: {}ms",
                    decision.isShouldAlert() ? "报警" : "过滤",
                    decision.isDuplicate() ? "是" : "否",
                    decision.getSimilarityScore(),
                    duration);

            // 短暂等待
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        printStatistics();
    }

    /**
     * 对比测试：不同严重程度
     */
    @Test
    void quickTest_CompareSeverity() {
        if (aiDenoiseService == null) {
            log.error("❌ AI 降噪服务未启用！");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("⚖️  对比测试：不同严重程度");
        log.info("=".repeat(80));

        String[][] testCases = {
                {"java.lang.OutOfMemoryError", "Java heap space", "P0"},
                {"java.sql.SQLException", "Connection refused", "P0/P1"},
                {"java.util.concurrent.TimeoutException", "Operation timeout", "P1/P2"},
                {"java.lang.NullPointerException", "Cannot invoke method", "P2/P3"},
                {"java.lang.IllegalArgumentException", "Invalid parameter", "P3/P4"}
        };

        for (String[] testCase : testCases) {
            String type = testCase[0];
            String message = testCase[1];
            String expectedSeverity = testCase[2];

            log.info("\n--- {} ---", type);

            ExceptionInfo exception = createExceptionInfo(
                    type,
                    message,
                    "com.example.TestService.test:100",
                    "/api/test",
                    "prod"
            );

            DenoiseDecision decision = aiDenoiseService.shouldAlert(exception);

            log.info("预期严重级别: {} | AI 判断: {} | 是否报警: {}",
                    expectedSeverity,
                    decision.getSuggestedSeverity(),
                    decision.isShouldAlert() ? "是" : "否");
        }
    }

    // ==================== 辅助方法 ====================

    private ExceptionInfo createExceptionInfo(String exceptionType,
                                               String message,
                                               String errorLocation,
                                               String requestUri,
                                               String environment) {
        ExceptionInfo info = new ExceptionInfo();

        // 基本信息
        info.setAppName("one-agent-4j");
        info.setEnvironment(environment);
        info.setInstanceId("instance-001");
        info.setHostname("localhost");
        info.setIp("127.0.0.1");

        // 异常信息
        info.setExceptionType(exceptionType);
        info.setExceptionMessage(message);
        info.setErrorLocation(errorLocation);
        info.setStackTrace(generateStackTrace(exceptionType, errorLocation, message));

        // 生成指纹
        info.setFingerprint(FingerprintGenerator.generate(
                exceptionType, errorLocation));

        // 解析错误位置
        parseErrorLocation(info, errorLocation);

        // 请求信息
        ExceptionInfo.RequestInfo requestInfo = new ExceptionInfo.RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setUri(requestUri);
        requestInfo.setClientIp("192.168.1.100");
        info.setRequestInfo(requestInfo);

        // 线程信息
        ExceptionInfo.ThreadInfo threadInfo = new ExceptionInfo.ThreadInfo();
        threadInfo.setThreadId(Thread.currentThread().getId());
        threadInfo.setThreadName(Thread.currentThread().getName());
        info.setThreadInfo(threadInfo);

        // 时间
        info.setOccurredAt(Instant.now());
        info.setReportedAt(Instant.now());

        return info;
    }

    private void parseErrorLocation(ExceptionInfo info, String errorLocation) {
        try {
            String[] parts = errorLocation.split(":");
            if (parts.length == 2) {
                String classAndMethod = parts[0];
                int lastDot = classAndMethod.lastIndexOf('.');

                if (lastDot > 0) {
                    info.setErrorClass(classAndMethod.substring(0, lastDot));
                    info.setErrorMethod(classAndMethod.substring(lastDot + 1));
                }

                info.setErrorLine(Integer.parseInt(parts[1]));
            }
        } catch (Exception e) {
            log.warn("解析错误位置失败: {}", errorLocation);
        }
    }

    private String generateStackTrace(String exceptionType, String errorLocation, String message) {
        String[] parts = errorLocation.split(":");
        String location = parts[0];
        String line = parts.length > 1 ? parts[1] : "0";

        return String.format("""
                %s: %s
                    at %s(SourceFile:%s)
                    at com.example.Controller.handleRequest(Controller.java:45)
                    at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1040)
                """, exceptionType, message, location, line);
    }

    private void printDetailedResult(DenoiseDecision decision, long duration) {
        log.info("\n" + "=".repeat(80));
        log.info("🤖 AI 分析结果");
        log.info("=".repeat(80));

        // 主要决策
        if (decision.isShouldAlert()) {
            log.info("✅ 决策: 需要报警");
        } else {
            log.info("❌ 决策: 过滤（不报警）");
        }

        // 详细信息
        log.info("\n📊 详细信息:");
        log.info("  • 是否重复: {}", decision.isDuplicate() ? "是" : "否");
        log.info("  • 相似度: {}", decision.getSimilarityScore());
        log.info("  • 建议严重级别: {}", decision.getSuggestedSeverity());

        if (decision.getRelatedExceptionIds() != null && !decision.getRelatedExceptionIds().isEmpty()) {
            log.info("  • 相关异常ID: {}", decision.getRelatedExceptionIds());
        }

        // 原因分析
        log.info("\n💡 判断原因:");
        log.info("  {}", decision.getReason());

        // 处理建议
        if (decision.getSuggestion() != null && !decision.getSuggestion().isEmpty()) {
            log.info("\n🔧 处理建议:");
            log.info("  {}", decision.getSuggestion());
        }

        // 性能指标
        log.info("\n⚡ 性能指标:");
        log.info("  • 响应耗时: {}ms", duration);

        if (duration < 100) {
            log.info("  • 性能评价: 优秀（缓存命中）");
        } else if (duration < 2000) {
            log.info("  • 性能评价: 良好（AI调用）");
        } else {
            log.info("  • 性能评价: 较慢（需要优化）");
        }

        log.info("=".repeat(80));
    }

    private void printStatistics() {
        if (aiDenoiseService == null) {
            return;
        }

        AiDenoiseService.AiDenoiseStats stats = aiDenoiseService.getStats();

        log.info("\n" + "=".repeat(80));
        log.info("📈 累计统计信息");
        log.info("=".repeat(80));

        log.info("总体指标:");
        log.info("  • 总检查次数: {}", stats.getTotalChecked());
        log.info("  • 缓存命中: {} 次 ({:.1f}%)",
                stats.getTotalCacheHit(),
                stats.getCacheHitRate() * 100);
        log.info("  • AI调用: {} 次", stats.getTotalAiCall());
        log.info("  • 被过滤: {} 次 ({:.1f}%)",
                stats.getTotalFiltered(),
                stats.getFilterRate() * 100);

        log.info("\n缓存信息:");
        log.info("  • 当前缓存大小: {}", stats.getCacheSize());
        log.info("  • 缓存驱逐次数: {}", stats.getCacheEvictionCount());

        // 成本估算
        double estimatedCost = stats.getTotalAiCall() * 0.001;
        log.info("\n💰 成本估算:");
        log.info("  • API调用次数: {}", stats.getTotalAiCall());
        log.info("  • 预估成本: ¥{:.3f} (假设 ¥0.001/次)", estimatedCost);

        log.info("=".repeat(80) + "\n");
    }
}
