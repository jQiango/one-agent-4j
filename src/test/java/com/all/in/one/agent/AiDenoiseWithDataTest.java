package com.all.in.one.agent;

import com.all.in.one.agent.ai.model.DenoiseDecision;
import com.all.in.one.agent.ai.service.AiDenoiseService;
import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.common.util.FingerprintGenerator;
import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import com.all.in.one.agent.dao.mapper.AppAlarmRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 降噪完整测试（含数据库准备）
 * <p>
 * 本测试会向数据库写入测试数据，模拟真实的历史告警场景
 * 用于验证 AI 在有历史数据时的判断能力
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiDenoiseWithDataTest {

    @Autowired(required = false)
    private AiDenoiseService aiDenoiseService;

    @Autowired(required = false)
    private AppAlarmRecordMapper appAlarmRecordMapper;

    // 测试数据ID列表，用于清理
    private static final List<Long> testRecordIds = new ArrayList<>();

    @BeforeEach
    void checkServices() {
        if (aiDenoiseService == null) {
            log.warn("⚠️ AI 降噪服务未启用");
        }
        if (appAlarmRecordMapper == null) {
            log.warn("⚠️ 数据库 Mapper 未初始化");
        }
    }

    // ==================== 测试用例 1: 有历史数据的重复识别 ====================

    /**
     * 场景1: 先写入历史数据，再测试相同异常 - AI 应识别为重复
     */
    @Test
    @Order(1)
    void testCase1_DuplicateWithHistory() {
        if (aiDenoiseService == null || appAlarmRecordMapper == null) {
            log.warn("跳过测试: 服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 1: 有历史数据的重复异常识别");
        log.info("=".repeat(80));

        // 1. 准备历史数据：写入3条相同的异常记录
        log.info("\n📝 步骤1: 写入历史数据（3条）");
        for (int i = 1; i <= 3; i++) {
            AppAlarmRecord historyRecord = createHistoryRecord(
                    "java.lang.NullPointerException",
                    "Cannot invoke \"User.getName()\" because \"user\" is null",
                    "com.example.UserService.getUserInfo:100",
                    LocalDateTime.now().minusMinutes(i)  // 1分钟前、2分钟前、3分钟前
            );
            appAlarmRecordMapper.insert(historyRecord);
            testRecordIds.add(historyRecord.getId());
            log.info("  ✓ 已插入历史记录 #{}: id={}, fingerprint={}",
                    i, historyRecord.getId(), historyRecord.getFingerprint());
        }

        // 2. 创建新的相同异常
        log.info("\n📝 步骤2: 创建新异常（与历史完全相同）");
        ExceptionInfo newException = createExceptionInfo(
                "java.lang.NullPointerException",
                "Cannot invoke \"User.getName()\" because \"user\" is null",
                "com.example.UserService.getUserInfo:100",
                "/api/user/info"
        );

        // 3. 调用 AI 判断
        log.info("\n🤖 步骤3: 调用 AI 进行判断");
        long startTime = System.currentTimeMillis();
        DenoiseDecision decision = aiDenoiseService.shouldAlert(newException);
        long duration = System.currentTimeMillis() - startTime;

        // 4. 输出结果
        printDetailedResult(decision, duration);

        // 5. 验证
        log.info("\n✅ 验证结果:");
        log.info("  • 是否识别为重复: {} (预期: 是)", decision.isDuplicate() ? "是" : "否");
        log.info("  • 是否建议报警: {} (预期: 否)", decision.isShouldAlert() ? "是" : "否");
        log.info("  • 相似度分数: {} (预期: > 0.9)", decision.getSimilarityScore());

        // 断言
        assertTrue(decision.isDuplicate(), "AI 应该识别出这是重复异常");
        assertTrue(decision.getSimilarityScore() > 0.8, "相似度应该很高（> 0.8）");
    }

    // ==================== 测试用例 2: 相似但不同的异常 ====================

    /**
     * 场景2: 先写入历史数据，再测试相似异常 - AI 应判断相似度
     */
    @Test
    @Order(2)
    void testCase2_SimilarWithHistory() {
        if (aiDenoiseService == null || appAlarmRecordMapper == null) {
            log.warn("跳过测试: 服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 2: 相似异常的相似度判断");
        log.info("=".repeat(80));

        // 1. 写入历史数据：User.getName() 的空指针
        log.info("\n📝 步骤1: 写入历史数据");
        AppAlarmRecord historyRecord = createHistoryRecord(
                "java.lang.NullPointerException",
                "Cannot invoke \"User.getName()\" because \"user\" is null",
                "com.example.UserService.getUserInfo:100",
                LocalDateTime.now().minusMinutes(1)
        );
        appAlarmRecordMapper.insert(historyRecord);
        testRecordIds.add(historyRecord.getId());
        log.info("  ✓ 历史记录: {}", historyRecord.getExceptionMessage());

        // 2. 创建相似但不同的异常：User.getEmail() 的空指针
        log.info("\n📝 步骤2: 创建相似异常（同位置，不同方法调用）");
        ExceptionInfo newException = createExceptionInfo(
                "java.lang.NullPointerException",
                "Cannot invoke \"User.getEmail()\" because \"user\" is null",
                "com.example.UserService.getUserInfo:105",  // 行号稍有不同
                "/api/user/info"
        );
        log.info("  ✓ 新异常: {}", newException.getExceptionMessage());

        // 3. 调用 AI 判断
        log.info("\n🤖 步骤3: 调用 AI 判断相似度");
        long startTime = System.currentTimeMillis();
        DenoiseDecision decision = aiDenoiseService.shouldAlert(newException);
        long duration = System.currentTimeMillis() - startTime;

        // 4. 输出结果
        printDetailedResult(decision, duration);

        // 5. 验证
        log.info("\n✅ 验证结果:");
        log.info("  • 相似度分数: {} (预期: 0.6-0.9)", decision.getSimilarityScore());
        log.info("  • AI 理解: {}", decision.getReason());

        // 断言
        assertTrue(decision.getSimilarityScore() > 0.5, "相似异常的相似度应该 > 0.5");
        assertNotNull(decision.getReason(), "AI 应该给出判断原因");
    }

    // ==================== 测试用例 3: 不同类型异常 ====================

    /**
     * 场景3: 有历史的空指针，新来SQL异常 - AI 应识别为不同类型
     */
    @Test
    @Order(3)
    void testCase3_DifferentTypeWithHistory() {
        if (aiDenoiseService == null || appAlarmRecordMapper == null) {
            log.warn("跳过测试: 服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 3: 不同类型异常识别");
        log.info("=".repeat(80));

        // 1. 写入历史：空指针异常
        log.info("\n📝 步骤1: 写入历史数据（空指针异常）");
        AppAlarmRecord historyRecord = createHistoryRecord(
                "java.lang.NullPointerException",
                "Cannot invoke method on null object",
                "com.example.UserService.getUser:50",
                LocalDateTime.now().minusMinutes(1)
        );
        appAlarmRecordMapper.insert(historyRecord);
        testRecordIds.add(historyRecord.getId());

        // 2. 创建新异常：SQL异常
        log.info("\n📝 步骤2: 创建新异常（SQL异常）");
        ExceptionInfo newException = createExceptionInfo(
                "java.sql.SQLException",
                "Connection timeout after 30000ms",
                "com.example.OrderRepository.saveOrder:89",
                "/api/order/create"
        );

        // 3. 调用 AI 判断
        log.info("\n🤖 步骤3: 调用 AI 判断");
        long startTime = System.currentTimeMillis();
        DenoiseDecision decision = aiDenoiseService.shouldAlert(newException);
        long duration = System.currentTimeMillis() - startTime;

        printDetailedResult(decision, duration);

        // 验证：不同类型的异常应该报警
        log.info("\n✅ 验证: 不同类型异常应该被识别为新异常");
        assertFalse(decision.isDuplicate(), "不同类型的异常不应该判定为重复");
        assertTrue(decision.isShouldAlert(), "新类型异常应该报警");
    }

    // ==================== 测试用例 4: 频繁异常场景 ====================

    /**
     * 场景4: 写入多条历史，测试频繁异常的处理
     */
    @Test
    @Order(4)
    void testCase4_FrequentExceptionWithHistory() {
        if (aiDenoiseService == null || appAlarmRecordMapper == null) {
            log.warn("跳过测试: 服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 4: 频繁异常场景");
        log.info("=".repeat(80));

        // 1. 写入大量历史记录（模拟频繁发生）
        log.info("\n📝 步骤1: 写入频繁异常历史（10条）");
        for (int i = 1; i <= 10; i++) {
            AppAlarmRecord record = createHistoryRecord(
                    "java.util.concurrent.TimeoutException",
                    "Request timeout after 5000ms",
                    "com.example.PaymentService.processPayment:200",
                    LocalDateTime.now().minusSeconds(i * 10)  // 每10秒一次
            );
            appAlarmRecordMapper.insert(record);
            testRecordIds.add(record.getId());
        }
        log.info("  ✓ 已插入 10 条频繁超时异常（最近1分钟内）");

        // 2. 新异常
        log.info("\n📝 步骤2: 创建第11次相同异常");
        ExceptionInfo newException = createExceptionInfo(
                "java.util.concurrent.TimeoutException",
                "Request timeout after 5000ms",
                "com.example.PaymentService.processPayment:200",
                "/api/payment/process"
        );

        // 3. 调用 AI 判断
        log.info("\n🤖 步骤3: AI 分析频繁异常");
        long startTime = System.currentTimeMillis();
        DenoiseDecision decision = aiDenoiseService.shouldAlert(newException);
        long duration = System.currentTimeMillis() - startTime;

        printDetailedResult(decision, duration);

        // 验证
        log.info("\n✅ 验证: 频繁异常的处理");
        log.info("  • AI 建议: {}", decision.getSuggestion());
        log.info("  • 是否过滤: {}", !decision.isShouldAlert() ? "是（合并告警）" : "否（仍然报警）");

        assertNotNull(decision.getSuggestion(), "频繁异常应该有处理建议");
    }

    // ==================== 测试用例 5: 严重级别升级场景 ====================

    /**
     * 场景5: 历史是P3，新异常更严重 - AI应识别严重程度变化
     */
    @Test
    @Order(5)
    void testCase5_SeverityEscalation() {
        if (aiDenoiseService == null || appAlarmRecordMapper == null) {
            log.warn("跳过测试: 服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 5: 严重级别升级检测");
        log.info("=".repeat(80));

        // 1. 写入历史：测试环境的空指针（低优先级）
        log.info("\n📝 步骤1: 写入测试环境的历史异常（低优先级）");
        AppAlarmRecord historyRecord = createHistoryRecord(
                "java.lang.NullPointerException",
                "Cannot invoke method on null object",
                "com.example.UserService.getUser:50",
                LocalDateTime.now().minusMinutes(1)
        );
        historyRecord.setEnvironment("test");  // 测试环境
        appAlarmRecordMapper.insert(historyRecord);
        testRecordIds.add(historyRecord.getId());
        log.info("  ✓ 历史记录: 测试环境空指针异常");

        // 2. 创建新异常：生产环境相同位置（应提升优先级）
        log.info("\n📝 步骤2: 创建生产环境的相同异常（应升级）");
        ExceptionInfo newException = createExceptionInfo(
                "java.lang.NullPointerException",
                "Cannot invoke method on null object",
                "com.example.UserService.getUser:50",
                "/api/user/get"
        );
        newException.setEnvironment("prod");  // 生产环境

        // 3. 调用 AI 判断
        log.info("\n🤖 步骤3: AI 判断严重级别变化");
        long startTime = System.currentTimeMillis();
        DenoiseDecision decision = aiDenoiseService.shouldAlert(newException);
        long duration = System.currentTimeMillis() - startTime;

        printDetailedResult(decision, duration);

        // 验证
        log.info("\n✅ 验证: AI 是否识别环境变化");
        log.info("  • 历史环境: test");
        log.info("  • 新异常环境: prod");
        log.info("  • AI 判断: {}", decision.isShouldAlert() ? "应该报警（环境升级）" : "过滤");

        assertTrue(decision.isShouldAlert(), "生产环境的异常应该报警，即使测试环境曾出现");
    }

    // ==================== 测试用例 6: 混合场景 ====================

    /**
     * 场景6: 数据库中有多种类型的历史异常
     */
    @Test
    @Order(6)
    void testCase6_MixedHistoryScenario() {
        if (aiDenoiseService == null || appAlarmRecordMapper == null) {
            log.warn("跳过测试: 服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 6: 混合历史场景");
        log.info("=".repeat(80));

        // 1. 写入多种类型的历史
        log.info("\n📝 步骤1: 写入多种类型的历史数据");

        // 空指针 x2
        for (int i = 0; i < 2; i++) {
            AppAlarmRecord record = createHistoryRecord(
                    "java.lang.NullPointerException",
                    "NPE in service layer",
                    "com.example.UserService.process:100",
                    LocalDateTime.now().minusMinutes(2)
            );
            appAlarmRecordMapper.insert(record);
            testRecordIds.add(record.getId());
        }

        // SQL异常 x1
        AppAlarmRecord sqlRecord = createHistoryRecord(
                "java.sql.SQLException",
                "Connection refused",
                "com.example.OrderService.save:50",
                LocalDateTime.now().minusMinutes(1)
        );
        appAlarmRecordMapper.insert(sqlRecord);
        testRecordIds.add(sqlRecord.getId());

        // 超时异常 x3
        for (int i = 0; i < 3; i++) {
            AppAlarmRecord record = createHistoryRecord(
                    "java.util.concurrent.TimeoutException",
                    "Timeout after 30s",
                    "com.example.PaymentService.pay:200",
                    LocalDateTime.now().minusSeconds(i * 20)
            );
            appAlarmRecordMapper.insert(record);
            testRecordIds.add(record.getId());
        }

        log.info("  ✓ 已插入: 2条空指针 + 1条SQL异常 + 3条超时异常");

        // 2. 测试新的超时异常（应该识别出频繁）
        log.info("\n📝 步骤2: 创建第4次超时异常");
        ExceptionInfo newException = createExceptionInfo(
                "java.util.concurrent.TimeoutException",
                "Timeout after 30s",
                "com.example.PaymentService.pay:200",
                "/api/payment/pay"
        );

        // 3. AI 判断
        log.info("\n🤖 步骤3: AI 分析（在混合历史中识别模式）");
        long startTime = System.currentTimeMillis();
        DenoiseDecision decision = aiDenoiseService.shouldAlert(newException);
        long duration = System.currentTimeMillis() - startTime;

        printDetailedResult(decision, duration);

        // 验证
        log.info("\n✅ 验证: AI 是否识别出超时异常的频繁模式");
        assertTrue(decision.isDuplicate() || decision.getSimilarityScore() > 0.8,
                "AI 应该识别出这是频繁的超时异常");
    }

    // ==================== 测试用例 7: 清空历史后的首次异常 ====================

    /**
     * 场景7: 清空历史后，新异常应该被识别为首次
     */
    @Test
    @Order(7)
    void testCase7_FirstExceptionAfterCleanup() {
        if (aiDenoiseService == null || appAlarmRecordMapper == null) {
            log.warn("跳过测试: 服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("🧪 测试用例 7: 清空历史后的首次异常");
        log.info("=".repeat(80));

        // 1. 清理之前的测试数据
        log.info("\n🗑️  步骤1: 清理测试数据");
        cleanupTestData();
        log.info("  ✓ 测试数据已清理");

        // 2. 创建新异常
        log.info("\n📝 步骤2: 创建新异常（无历史记录）");
        ExceptionInfo newException = createExceptionInfo(
                "com.example.BusinessException",
                "Business validation failed: insufficient balance",
                "com.example.PaymentService.validateBalance:150",
                "/api/payment/validate"
        );

        // 3. AI 判断
        log.info("\n🤖 步骤3: AI 判断（无历史上下文）");
        long startTime = System.currentTimeMillis();
        DenoiseDecision decision = aiDenoiseService.shouldAlert(newException);
        long duration = System.currentTimeMillis() - startTime;

        printDetailedResult(decision, duration);

        // 验证
        log.info("\n✅ 验证: 首次异常的处理");
        assertFalse(decision.isDuplicate(), "无历史记录时不应判定为重复");
        assertTrue(decision.isShouldAlert(), "首次异常通常应该报警");
        assertEquals(0.0, decision.getSimilarityScore(), "无历史时相似度应为0");
    }

    // ==================== 测试用例 8: 统计和性能 ====================

    /**
     * 场景8: 查看整体统计和性能指标
     */
    @Test
    @Order(8)
    void testCase8_PerformanceAndStatistics() {
        if (aiDenoiseService == null) {
            log.warn("跳过测试: 服务未启用");
            return;
        }

        log.info("\n" + "=".repeat(80));
        log.info("📊 整体性能统计");
        log.info("=".repeat(80));

        AiDenoiseService.AiDenoiseStats stats = aiDenoiseService.getStats();

        log.info("\n📈 累计指标:");
        log.info("  • 总检查次数: {}", stats.getTotalChecked());
        log.info("  • 缓存命中: {} 次 ({:.1f}%)",
                stats.getTotalCacheHit(),
                stats.getCacheHitRate() * 100);
        log.info("  • AI实际调用: {} 次", stats.getTotalAiCall());
        log.info("  • 被过滤: {} 次 ({:.1f}%)",
                stats.getTotalFiltered(),
                stats.getFilterRate() * 100);

        log.info("\n💾 缓存信息:");
        log.info("  • 缓存大小: {}", stats.getCacheSize());
        log.info("  • 驱逐次数: {}", stats.getCacheEvictionCount());

        log.info("\n💰 成本分析:");
        double avgCostPerCall = 0.001;  // 假设每次调用0.001元
        double totalCost = stats.getTotalAiCall() * avgCostPerCall;
        double cacheSaving = stats.getTotalCacheHit() * avgCostPerCall;
        log.info("  • API调用成本: ¥{:.3f}", totalCost);
        log.info("  • 缓存节省成本: ¥{:.3f}", cacheSaving);
        log.info("  • 总节省率: {:.1f}%", stats.getCacheHitRate() * 100);

        log.info("\n⚡ 性能评价:");
        if (stats.getCacheHitRate() > 0.8) {
            log.info("  ✅ 缓存命中率优秀 (> 80%)");
        } else if (stats.getCacheHitRate() > 0.5) {
            log.info("  ⚠️  缓存命中率一般 (50-80%)");
        } else {
            log.info("  ❌ 缓存命中率较低 (< 50%)");
        }

        if (stats.getFilterRate() > 0.7) {
            log.info("  ✅ 过滤率优秀 (> 70%)");
        } else if (stats.getFilterRate() > 0.5) {
            log.info("  ⚠️  过滤率一般 (50-70%)");
        } else {
            log.info("  ℹ️  过滤率较低 (< 50%) - 可能是新异常较多");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建历史告警记录（写入数据库）
     */
    private AppAlarmRecord createHistoryRecord(String exceptionType,
                                                String message,
                                                String errorLocation,
                                                LocalDateTime occurredAt) {
        AppAlarmRecord record = new AppAlarmRecord();

        // 应用信息
        record.setAppName("one-agent-4j");
        record.setEnvironment("prod");
        record.setInstanceId("test-instance");
        record.setHostname("test-host");
        record.setIp("192.168.1.100");

        // 异常信息
        record.setExceptionType(exceptionType);
        record.setExceptionMessage(message);
        record.setStackTrace(generateStackTrace(exceptionType, errorLocation, message));
        record.setFingerprint(FingerprintGenerator.generate(exceptionType, errorLocation));

        // 错误位置
        parseErrorLocation(record, errorLocation);

        // 时间
        record.setOccurredAt(occurredAt);
        record.setReportedAt(LocalDateTime.now());

        // AI 字段
        record.setAiProcessed(false);

        return record;
    }

    /**
     * 创建测试用的异常信息（用于AI判断）
     */
    private ExceptionInfo createExceptionInfo(String exceptionType,
                                               String message,
                                               String errorLocation,
                                               String requestUri) {
        ExceptionInfo info = new ExceptionInfo();

        // 基本信息
        info.setAppName("one-agent-4j");
        info.setEnvironment("prod");
        info.setInstanceId("test-instance");
        info.setHostname("test-host");
        info.setIp("192.168.1.100");

        // 异常信息
        info.setExceptionType(exceptionType);
        info.setExceptionMessage(message);
        info.setErrorLocation(errorLocation);
        info.setStackTrace(generateStackTrace(exceptionType, errorLocation, message));
        info.setFingerprint(FingerprintGenerator.generate(exceptionType, errorLocation));

        // 解析错误位置
        parseErrorLocation(info, errorLocation);

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

        // 时间
        info.setOccurredAt(Instant.now());
        info.setReportedAt(Instant.now());

        return info;
    }

    /**
     * 解析错误位置 (AppAlarmRecord)
     */
    private void parseErrorLocation(AppAlarmRecord record, String errorLocation) {
        try {
            String[] parts = errorLocation.split(":");
            if (parts.length == 2) {
                String classAndMethod = parts[0];
                int lastDot = classAndMethod.lastIndexOf('.');

                if (lastDot > 0) {
                    record.setErrorClass(classAndMethod.substring(0, lastDot));
                    record.setErrorMethod(classAndMethod.substring(lastDot + 1));
                }

                record.setErrorLine(Integer.parseInt(parts[1]));
                record.setErrorLocation(errorLocation);
            }
        } catch (Exception e) {
            log.warn("解析错误位置失败: {}", errorLocation);
        }
    }

    /**
     * 解析错误位置 (ExceptionInfo)
     */
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

    /**
     * 生成堆栈信息
     */
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

    /**
     * 格式化输出 AI 决策结果
     */
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

    /**
     * 清理测试数据
     */
    private void cleanupTestData() {
        if (testRecordIds.isEmpty()) {
            return;
        }

        log.info("\n🗑️  清理测试数据: {} 条", testRecordIds.size());
        for (Long id : testRecordIds) {
            try {
                appAlarmRecordMapper.deleteById(id);
            } catch (Exception e) {
                log.warn("删除测试记录失败: id={}", id);
            }
        }
        testRecordIds.clear();
        log.info("  ✓ 清理完成");
    }

    /**
     * 测试结束后清理
     */
    @AfterAll
    static void afterAll(@Autowired(required = false) AppAlarmRecordMapper mapper) {
        if (mapper != null && !testRecordIds.isEmpty()) {
            log.info("\n" + "=".repeat(80));
            log.info("🧹 清理所有测试数据");
            log.info("=".repeat(80));

            for (Long id : testRecordIds) {
                try {
                    mapper.deleteById(id);
                    log.info("  ✓ 已删除测试记录: id={}", id);
                } catch (Exception e) {
                    log.warn("  ✗ 删除失败: id={}", id);
                }
            }
            testRecordIds.clear();
            log.info("✅ 清理完成！");
        }
    }
}
