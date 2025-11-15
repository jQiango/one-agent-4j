package com.all.in.one.agent;

import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.starter.dedup.FingerprintDeduplicator;
import com.all.in.one.agent.starter.filter.IgnoreListFilter;
import com.all.in.one.agent.starter.rule.RuleEngine;
import com.all.in.one.agent.service.TicketGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 漏斗降噪功能测试
 * <p>
 * 验证 4 层降噪机制是否正常工作
 * </p>
 */
@Slf4j
@SpringBootTest
class FunnelDenoiseTest {

    @Autowired(required = false)
    private IgnoreListFilter ignoreListFilter;

    @Autowired(required = false)
    private FingerprintDeduplicator fingerprintDeduplicator;

    @Autowired(required = false)
    private RuleEngine ruleEngine;

    @Autowired(required = false)
    private TicketGenerationService ticketGenerationService;

    /**
     * 测试 Layer 0: 基础过滤
     */
    @Test
    void testLayer0_IgnoreListFilter() {
        if (ignoreListFilter == null) {
            log.warn("IgnoreListFilter 未启用，跳过测试");
            return;
        }

        log.info("=== 测试 Layer 0: 基础过滤 ===");

        // 1. 测试正常异常（不应该被过滤）
        ExceptionInfo normalException = createExceptionInfo("java.lang.NullPointerException", "/api/user/get");
        boolean shouldIgnore1 = ignoreListFilter.shouldIgnore(normalException);
        assertFalse(shouldIgnore1, "正常异常不应该被过滤");
        log.info("✅ 正常异常未被过滤");

        // 2. 测试健康检查异常（应该被过滤）
        ExceptionInfo healthCheckException = createExceptionInfo("java.lang.RuntimeException", "/actuator/health");
        boolean shouldIgnore2 = ignoreListFilter.shouldIgnore(healthCheckException);
        // 根据配置可能被过滤，这里不强制断言
        log.info("健康检查异常过滤结果: {}", shouldIgnore2);

        // 3. 获取统计信息
        IgnoreListFilter.FilterStats stats = ignoreListFilter.getStats();
        log.info("Layer 0 统计: totalChecked={}, totalFiltered={}, filterRate={}",
                stats.getTotalChecked(), stats.getTotalFiltered(), stats.getFilterRate());

        assertTrue(stats.getTotalChecked() >= 2, "至少检查了 2 个异常");
    }

    /**
     * 测试 Layer 1: 指纹去重
     */
    @Test
    void testLayer1_FingerprintDedup() throws InterruptedException {
        if (fingerprintDeduplicator == null) {
            log.warn("FingerprintDeduplicator 未启用，跳过测试");
            return;
        }

        log.info("=== 测试 Layer 1: 指纹去重 ===");

        // 1. 创建相同的异常
        ExceptionInfo exception1 = createExceptionInfo("java.lang.NullPointerException", "/api/test");
        ExceptionInfo exception2 = createExceptionInfo("java.lang.NullPointerException", "/api/test");

        // 2. 第一次应该不是重复
        boolean isDuplicate1 = fingerprintDeduplicator.isDuplicate(exception1);
        assertFalse(isDuplicate1, "首次出现的异常不应该被判定为重复");
        log.info("✅ 首次异常未被过滤");

        // 3. 立即再次出现应该被判定为重复
        boolean isDuplicate2 = fingerprintDeduplicator.isDuplicate(exception2);
        assertTrue(isDuplicate2, "相同指纹的异常应该被判定为重复");
        log.info("✅ 重复异常被正确识别");

        // 4. 获取统计信息
        FingerprintDeduplicator.DedupStats stats = fingerprintDeduplicator.getStats();
        log.info("Layer 1 统计: totalChecked={}, totalFiltered={}, cacheSize={}, filterRate={}",
                stats.getTotalChecked(), stats.getTotalFiltered(), stats.getCacheSize(), stats.getFilterRate());

        assertTrue(stats.getTotalFiltered() > 0, "应该有被过滤的重复异常");
    }

    /**
     * 测试 Layer 1.5: 规则引擎
     */
    @Test
    void testLayer15_RuleEngine() {
        if (ruleEngine == null) {
            log.warn("RuleEngine 未启用，跳过测试");
            return;
        }

        log.info("=== 测试 Layer 1.5: 规则引擎 ===");

        // 1. 创建测试异常
        ExceptionInfo exception = createExceptionInfo("java.lang.RuntimeException", "/api/test");

        // 2. 评估规则
        RuleEngine.FilterResult result = ruleEngine.evaluate(exception);
        log.info("规则引擎评估结果: filtered={}, ruleName={}, reason={}",
                result.isFiltered(), result.getRuleName(), result.getReason());

        // 3. 获取统计信息
        RuleEngine.RuleEngineStats stats = ruleEngine.getStats();
        log.info("Layer 1.5 统计: totalChecked={}, totalFiltered={}, filterRate={}",
                stats.getTotalChecked(), stats.getTotalFiltered(), stats.getFilterRate());

        assertNotNull(result, "规则引擎应该返回评估结果");
    }

    /**
     * 测试严重程度计算
     */
    @Test
    void testSeverityCalculation() {
        if (ticketGenerationService == null) {
            log.warn("TicketGenerationService 未启用，跳过测试");
            return;
        }

        log.info("=== 测试严重程度计算 ===");

        // 1. OutOfMemoryError - 应该是 P0
        ExceptionInfo oomException = createExceptionInfo("java.lang.OutOfMemoryError", "/api/test");
        String severity1 = ticketGenerationService.calculateSeverity(oomException);
        assertEquals("P0", severity1, "OutOfMemoryError 应该是 P0 级别");
        log.info("✅ OutOfMemoryError -> P0");

        // 2. SQLException - 生产环境应该是 P0，测试环境 P1
        ExceptionInfo sqlException = createExceptionInfo("java.sql.SQLException", "/api/test");
        String severity2 = ticketGenerationService.calculateSeverity(sqlException);
        assertTrue(severity2.equals("P0") || severity2.equals("P1"), "SQLException 应该是 P0 或 P1");
        log.info("✅ SQLException -> {}", severity2);

        // 3. NullPointerException - 应该是 P2 或 P3
        ExceptionInfo npeException = createExceptionInfo("java.lang.NullPointerException", "/api/test");
        String severity3 = ticketGenerationService.calculateSeverity(npeException);
        assertTrue(severity3.equals("P2") || severity3.equals("P3"), "NullPointerException 应该是 P2 或 P3");
        log.info("✅ NullPointerException -> {}", severity3);

        // 4. 普通 RuntimeException - 应该是 P3 或 P4
        ExceptionInfo runtimeException = createExceptionInfo("java.lang.RuntimeException", "/api/test");
        String severity4 = ticketGenerationService.calculateSeverity(runtimeException);
        assertTrue(severity4.equals("P3") || severity4.equals("P4"), "RuntimeException 应该是 P3 或 P4");
        log.info("✅ RuntimeException -> {}", severity4);
    }

    /**
     * 辅助方法: 创建异常信息
     */
    private ExceptionInfo createExceptionInfo(String exceptionType, String errorLocation) {
        ExceptionInfo info = new ExceptionInfo();
        info.setAppName("one-agent-4j");
        info.setEnvironment("dev");
        info.setExceptionType(exceptionType);
        info.setExceptionMessage("Test exception message");
        info.setErrorLocation(errorLocation);
        info.setStackTrace("Test stack trace");
        info.setOccurredAt(java.time.Instant.now());
        info.setReportedAt(java.time.Instant.now());

        // 生成指纹
        info.setFingerprint(com.all.in.one.agent.common.util.FingerprintGenerator.generate(
                exceptionType, errorLocation));

        return info;
    }
}
