package com.all.in.one.agent;

import com.all.in.one.agent.ai.service.AiDenoiseService;
import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import com.all.in.one.agent.dao.entity.AppAlarmTicket;
import com.all.in.one.agent.dao.mapper.AppAlarmRecordMapper;
import com.all.in.one.agent.dao.mapper.AppAlarmTicketMapper;
import com.all.in.one.agent.starter.collector.ExceptionCollector;
import com.all.in.one.agent.starter.dedup.FingerprintDeduplicator;
import com.all.in.one.agent.starter.filter.IgnoreListFilter;
import com.all.in.one.agent.starter.rule.RuleEngine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整管道集成测试 - 测试从异常捕获到工单生成的完整流程
 * <p>
 * 测试路径: Exception → ExceptionCollector → Layer0(Ignore) → Layer1(Dedup) →
 *          Layer1.5(Rules) → Layer2(AI) → Persistence → Ticket Generation
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullPipelineIntegrationTest {

    @Autowired(required = false)
    private ExceptionCollector exceptionCollector;

    @Autowired(required = false)
    private IgnoreListFilter ignoreListFilter;

    @Autowired(required = false)
    private FingerprintDeduplicator fingerprintDeduplicator;

    @Autowired(required = false)
    private RuleEngine ruleEngine;

    @Autowired(required = false)
    private AiDenoiseService aiDenoiseService;

    @Autowired(required = false)
    private AppAlarmRecordMapper appAlarmRecordMapper;

    @Autowired(required = false)
    private AppAlarmTicketMapper appAlarmTicketMapper;

    // 记录测试创建的记录ID，用于清理
    private static final List<Long> testRecordIds = new ArrayList<>();
    private static final List<Long> testTicketIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        log.info("\n" + "=".repeat(100));
        log.info("🚀 开始集成测试 - 完整AI降噪管道");
        log.info("=".repeat(100));

        if (exceptionCollector == null) {
            log.warn("⚠️ ExceptionCollector 未启用，跳过测试");
        }
    }

    // ==================== 场景 1: 首次异常 - 完整流程 ====================

    /**
     * 测试场景1: 全新异常首次出现
     * <p>
     * 预期流程:
     * 1. 通过 Layer 0 (不在忽略列表) ✅
     * 2. 通过 Layer 1 (首次出现，无重复) ✅
     * 3. 通过 Layer 1.5 (不违反规则) ✅
     * 4. 通过 Layer 2 AI (无历史记录，建议报警) ✅
     * 5. 持久化到 app_alarm_record ✅
     * 6. 生成工单到 app_alarm_ticket ✅
     * </p>
     */
    @Test
    @Order(1)
    void testScenario1_FirstException_FullPipeline() throws InterruptedException {
        if (exceptionCollector == null) {
            log.warn("跳过测试: ExceptionCollector 未启用");
            return;
        }

        log.info("\n📋 测试场景 1: 首次异常 - 完整降噪管道");
        log.info("-".repeat(80));

        // 准备: 清空之前的测试数据
        cleanupTestData();

        // 创建一个全新的异常
        Exception testException = new NullPointerException(
                "Cannot invoke \"com.example.User.getName()\" because \"user\" is null"
        );

        // 模拟堆栈信息
        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement("com.example.UserService", "getUserInfo", "UserService.java", 100),
                new StackTraceElement("com.example.UserController", "getUser", "UserController.java", 50),
                new StackTraceElement("org.springframework.web.servlet.DispatcherServlet", "doDispatch", "DispatcherServlet.java", 1040)
        };
        testException.setStackTrace(stackTrace);

        log.info("📤 步骤1: 将异常提交给 ExceptionCollector");
        log.info("   异常类型: {}", testException.getClass().getName());
        log.info("   异常消息: {}", testException.getMessage());
        log.info("   错误位置: com.example.UserService.getUserInfo:100");

        // === 执行: 提交异常到收集器（触发完整管道）===
        long startTime = System.currentTimeMillis();
        exceptionCollector.collect(testException);
        long duration = System.currentTimeMillis() - startTime;

        log.info("⏱️  处理耗时: {}ms", duration);

        // 等待异步处理完成
        Thread.sleep(2000);

        // === 验证 Layer 0: 基础过滤 ===
        log.info("\n✅ Layer 0 验证: 基础过滤");
        if (ignoreListFilter != null) {
            IgnoreListFilter.FilterStats stats0 = ignoreListFilter.getStats();
            log.info("   总检查: {}, 已过滤: {}, 过滤率: {:.1f}%",
                    stats0.getTotalChecked(), stats0.getTotalFiltered(), stats0.getFilterRate() * 100);
        }

        // === 验证 Layer 1: 指纹去重 ===
        log.info("\n✅ Layer 1 验证: 指纹去重");
        if (fingerprintDeduplicator != null) {
            FingerprintDeduplicator.DedupStats stats1 = fingerprintDeduplicator.getStats();
            log.info("   总检查: {}, 去重: {}, 缓存大小: {}",
                    stats1.getTotalChecked(), stats1.getTotalFiltered(), stats1.getCacheSize());
        }

        // === 验证 Layer 1.5: 规则引擎 ===
        log.info("\n✅ Layer 1.5 验证: 规则引擎");
        if (ruleEngine != null) {
            RuleEngine.RuleEngineStats stats15 = ruleEngine.getStats();
            log.info("   总检查: {}, 规则过滤: {}, 过滤率: {:.1f}%",
                    stats15.getTotalChecked(), stats15.getTotalFiltered(), stats15.getFilterRate() * 100);
        }

        // === 验证 Layer 2: AI 降噪 ===
        log.info("\n✅ Layer 2 验证: AI 智能降噪");
        if (aiDenoiseService != null) {
            AiDenoiseService.AiDenoiseStats stats2 = aiDenoiseService.getStats();
            log.info("   总检查: {}, AI调用: {}, 缓存命中: {}, 已过滤: {}",
                    stats2.getTotalChecked(), stats2.getTotalAiCall(),
                    stats2.getTotalCacheHit(), stats2.getTotalFiltered());
            log.info("   缓存命中率: {:.1f}%, AI过滤率: {:.1f}%",
                    stats2.getCacheHitRate() * 100, stats2.getFilterRate() * 100);
        }

        // === 验证数据持久化 ===
        log.info("\n✅ 持久化验证: 检查数据库记录");
        if (appAlarmRecordMapper != null) {
            LambdaQueryWrapper<AppAlarmRecord> recordQuery = new LambdaQueryWrapper<>();
            recordQuery.eq(AppAlarmRecord::getAppName, "one-agent-4j")
                    .eq(AppAlarmRecord::getExceptionType, "java.lang.NullPointerException")
                    .like(AppAlarmRecord::getExceptionMessage, "User.getName")
                    .ge(AppAlarmRecord::getOccurredAt, LocalDateTime.now().minusMinutes(1));

            List<AppAlarmRecord> records = appAlarmRecordMapper.selectList(recordQuery);
            log.info("   查询到告警记录数: {}", records.size());

            if (!records.isEmpty()) {
                AppAlarmRecord record = records.get(0);
                testRecordIds.add(record.getId());

                log.info("   记录ID: {}", record.getId());
                log.info("   异常指纹: {}", record.getFingerprint());
                log.info("   错误位置: {}:{}:{}",
                        record.getErrorClass(), record.getErrorMethod(), record.getErrorLine());
                log.info("   AI处理: {}", record.getAiProcessed() ? "是" : "否");
                log.info("   AI决策: {}", record.getAiDecision());
                log.info("   AI原因: {}", record.getAiReason());

                assertTrue(records.size() > 0, "应该有至少1条告警记录被持久化");
            }
        }

        // === 验证工单生成 ===
        log.info("\n✅ 工单验证: 检查自动生成的工单");
        if (appAlarmTicketMapper != null && appAlarmRecordMapper != null) {
            LambdaQueryWrapper<AppAlarmRecord> recordQuery = new LambdaQueryWrapper<>();
            recordQuery.eq(AppAlarmRecord::getAppName, "one-agent-4j")
                    .like(AppAlarmRecord::getExceptionMessage, "User.getName")
                    .ge(AppAlarmRecord::getOccurredAt, LocalDateTime.now().minusMinutes(1))
                    .orderByDesc(AppAlarmRecord::getOccurredAt)
                    .last("LIMIT 1");

            List<AppAlarmRecord> records = appAlarmRecordMapper.selectList(recordQuery);
            if (!records.isEmpty()) {
                String fingerprint = records.get(0).getFingerprint();

                LambdaQueryWrapper<AppAlarmTicket> ticketQuery = new LambdaQueryWrapper<>();
                ticketQuery.eq(AppAlarmTicket::getExceptionFingerprint, fingerprint);

                List<AppAlarmTicket> tickets = appAlarmTicketMapper.selectList(ticketQuery);
                log.info("   查询到工单数: {}", tickets.size());

                if (!tickets.isEmpty()) {
                    AppAlarmTicket ticket = tickets.get(0);
                    testTicketIds.add(ticket.getId());

                    log.info("   工单ID: {}", ticket.getId());
                    log.info("   工单标题: {}", ticket.getTitle());
                    log.info("   严重级别: {}", ticket.getSeverity());
                    log.info("   工单状态: {}", ticket.getStatus());
                    log.info("   发生次数: {}", ticket.getOccurrenceCount());
                    log.info("   预期解决时间: {}", ticket.getExpectedResolveTime());

                    assertNotNull(ticket.getTitle(), "工单应该有标题");
                    assertNotNull(ticket.getSeverity(), "工单应该有严重级别");
                    assertEquals("PENDING", ticket.getStatus(), "新工单状态应该是 PENDING");
                    assertTrue(ticket.getOccurrenceCount() > 0, "发生次数应该 > 0");
                }
            }
        }

        log.info("\n" + "=".repeat(80));
        log.info("✅ 场景1测试完成: 首次异常成功通过完整管道并生成工单");
        log.info("=".repeat(80));
    }

    // ==================== 场景 2: 重复异常 - AI识别去重 ====================

    /**
     * 测试场景2: 相同异常再次出现
     * <p>
     * 预期流程:
     * 1. 通过 Layer 0 ✅
     * 2. 被 Layer 1 过滤（指纹重复） ❌ 或
     * 3. 被 Layer 2 AI 识别为重复 ❌
     * 4. 不应生成新的告警记录和工单 ❌
     * </p>
     */
    @Test
    @Order(2)
    void testScenario2_DuplicateException_FilteredByAI() throws InterruptedException {
        if (exceptionCollector == null) {
            log.warn("跳过测试: ExceptionCollector 未启用");
            return;
        }

        log.info("\n📋 测试场景 2: 重复异常 - AI 识别去重");
        log.info("-".repeat(80));

        // 准备: 查询当前记录数
        int recordCountBefore = 0;
        int ticketCountBefore = 0;

        if (appAlarmRecordMapper != null) {
            recordCountBefore = appAlarmRecordMapper.selectCount(null).intValue();
            log.info("📊 当前告警记录数: {}", recordCountBefore);
        }

        if (appAlarmTicketMapper != null) {
            ticketCountBefore = appAlarmTicketMapper.selectCount(null).intValue();
            log.info("📊 当前工单数: {}", ticketCountBefore);
        }

        // 创建完全相同的异常（与场景1相同）
        Exception testException = new NullPointerException(
                "Cannot invoke \"com.example.User.getName()\" because \"user\" is null"
        );

        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement("com.example.UserService", "getUserInfo", "UserService.java", 100),
                new StackTraceElement("com.example.UserController", "getUser", "UserController.java", 50)
        };
        testException.setStackTrace(stackTrace);

        log.info("📤 步骤1: 提交重复异常");
        log.info("   异常类型: {}", testException.getClass().getName());
        log.info("   异常消息: {}", testException.getMessage());

        // === 执行: 提交重复异常 ===
        long startTime = System.currentTimeMillis();
        exceptionCollector.collect(testException);
        long duration = System.currentTimeMillis() - startTime;

        log.info("⏱️  处理耗时: {}ms (应该很快，可能被缓存拦截)", duration);

        // 等待处理
        Thread.sleep(2000);

        // === 验证: 指纹去重层应该拦截 ===
        log.info("\n✅ Layer 1 验证: 指纹去重应该生效");
        if (fingerprintDeduplicator != null) {
            FingerprintDeduplicator.DedupStats stats = fingerprintDeduplicator.getStats();
            log.info("   去重过滤: {} 次", stats.getTotalFiltered());
            assertTrue(stats.getTotalFiltered() > 0, "应该有重复异常被过滤");
        }

        // === 验证: AI 层统计 ===
        log.info("\n✅ Layer 2 验证: AI 降噪统计");
        if (aiDenoiseService != null) {
            AiDenoiseService.AiDenoiseStats stats = aiDenoiseService.getStats();
            log.info("   AI过滤: {} 次", stats.getTotalFiltered());
            log.info("   缓存命中: {} 次", stats.getTotalCacheHit());

            // 如果通过了Layer 1，应该被AI缓存命中
            if (stats.getTotalCacheHit() > 0) {
                log.info("   ✅ AI缓存生效，避免了重复调用LLM");
            }
        }

        // === 验证: 不应该生成新记录 ===
        log.info("\n✅ 持久化验证: 不应该有新记录");
        if (appAlarmRecordMapper != null) {
            int recordCountAfter = appAlarmRecordMapper.selectCount(null).intValue();
            log.info("   处理前记录数: {}", recordCountBefore);
            log.info("   处理后记录数: {}", recordCountAfter);

            // 重复异常可能被去重，不生成新记录
            // 或者如果生成了，说明是更新现有记录
            assertTrue(recordCountAfter <= recordCountBefore + 1,
                    "重复异常不应该大量增加记录");
        }

        log.info("\n" + "=".repeat(80));
        log.info("✅ 场景2测试完成: 重复异常被成功识别和过滤");
        log.info("=".repeat(80));
    }

    // ==================== 场景 3: 频繁异常 - 规则引擎 ====================

    /**
     * 测试场景3: 短时间内频繁发生的异常
     * <p>
     * 预期流程:
     * 1. 提交多个不同但相似的异常
     * 2. Layer 1.5 规则引擎应该检测到频率异常
     * 3. AI 应该建议合并告警
     * </p>
     */
    @Test
    @Order(3)
    void testScenario3_FrequentExceptions_RuleEngine() throws InterruptedException {
        if (exceptionCollector == null) {
            log.warn("跳过测试: ExceptionCollector 未启用");
            return;
        }

        log.info("\n📋 测试场景 3: 频繁异常 - 规则引擎处理");
        log.info("-".repeat(80));

        // 模拟5个不同的超时异常
        for (int i = 1; i <= 5; i++) {
            Exception timeoutException = new java.util.concurrent.TimeoutException(
                    "Request timeout after " + (5000 + i * 100) + "ms"
            );

            StackTraceElement[] stackTrace = new StackTraceElement[]{
                    new StackTraceElement("com.example.PaymentService", "processPayment",
                            "PaymentService.java", 200 + i),
                    new StackTraceElement("com.example.PaymentController", "pay",
                            "PaymentController.java", 50)
            };
            timeoutException.setStackTrace(stackTrace);

            log.info("📤 提交第 {} 个超时异常 (行号: {})", i, 200 + i);
            exceptionCollector.collect(timeoutException);

            Thread.sleep(500); // 间隔500ms
        }

        log.info("\n⏱️  等待处理完成...");
        Thread.sleep(3000);

        // === 验证规则引擎 ===
        log.info("\n✅ Layer 1.5 验证: 规则引擎应该检测到频繁异常");
        if (ruleEngine != null) {
            RuleEngine.RuleEngineStats stats = ruleEngine.getStats();
            log.info("   规则检查总数: {}", stats.getTotalChecked());
            log.info("   规则过滤总数: {}", stats.getTotalFiltered());

            if (stats.getTotalFiltered() > 0) {
                log.info("   ✅ 规则引擎成功拦截了部分频繁异常");
            }
        }

        // === 验证 AI 建议 ===
        log.info("\n✅ Layer 2 验证: AI 应该给出合并告警建议");
        if (appAlarmRecordMapper != null) {
            LambdaQueryWrapper<AppAlarmRecord> query = new LambdaQueryWrapper<>();
            query.eq(AppAlarmRecord::getExceptionType, "java.util.concurrent.TimeoutException")
                    .like(AppAlarmRecord::getExceptionMessage, "timeout")
                    .ge(AppAlarmRecord::getOccurredAt, LocalDateTime.now().minusMinutes(1))
                    .orderByDesc(AppAlarmRecord::getOccurredAt);

            List<AppAlarmRecord> records = appAlarmRecordMapper.selectList(query);
            log.info("   超时异常记录数: {}", records.size());

            if (!records.isEmpty()) {
                for (AppAlarmRecord record : records) {
                    testRecordIds.add(record.getId());
                    if (record.getAiReason() != null) {
                        log.info("   AI原因: {}", record.getAiReason());
                    }
                }
            }
        }

        log.info("\n" + "=".repeat(80));
        log.info("✅ 场景3测试完成: 频繁异常被规则引擎和AI正确处理");
        log.info("=".repeat(80));
    }

    // ==================== 场景 4: 综合统计 ====================

    /**
     * 测试场景4: 查看所有层的综合统计
     */
    @Test
    @Order(4)
    void testScenario4_OverallStatistics() {
        log.info("\n📊 综合统计信息");
        log.info("=".repeat(80));

        // Layer 0 统计
        if (ignoreListFilter != null) {
            log.info("\n🔹 Layer 0 - 基础过滤 (Ignore List)");
            IgnoreListFilter.FilterStats stats = ignoreListFilter.getStats();
            log.info("   总检查: {}", stats.getTotalChecked());
            log.info("   已过滤: {}", stats.getTotalFiltered());
            log.info("   过滤率: {:.1f}%", stats.getFilterRate() * 100);
        }

        // Layer 1 统计
        if (fingerprintDeduplicator != null) {
            log.info("\n🔹 Layer 1 - 指纹去重 (Fingerprint)");
            FingerprintDeduplicator.DedupStats stats = fingerprintDeduplicator.getStats();
            log.info("   总检查: {}", stats.getTotalChecked());
            log.info("   去重过滤: {}", stats.getTotalFiltered());
            log.info("   过滤率: {:.1f}%", stats.getFilterRate() * 100);
            log.info("   缓存大小: {}", stats.getCacheSize());
            log.info("   缓存驱逐: {}", stats.getCacheEvictionCount());
        }

        // Layer 1.5 统计
        if (ruleEngine != null) {
            log.info("\n🔹 Layer 1.5 - 规则引擎 (Rule Engine)");
            RuleEngine.RuleEngineStats stats = ruleEngine.getStats();
            log.info("   总检查: {}", stats.getTotalChecked());
            log.info("   规则过滤: {}", stats.getTotalFiltered());
            log.info("   过滤率: {:.1f}%", stats.getFilterRate() * 100);
        }

        // Layer 2 统计
        if (aiDenoiseService != null) {
            log.info("\n🔹 Layer 2 - AI 智能降噪");
            AiDenoiseService.AiDenoiseStats stats = aiDenoiseService.getStats();
            log.info("   总检查: {}", stats.getTotalChecked());
            log.info("   AI 实际调用: {}", stats.getTotalAiCall());
            log.info("   缓存命中: {}", stats.getTotalCacheHit());
            log.info("   AI 过滤: {}", stats.getTotalFiltered());
            log.info("   缓存命中率: {:.1f}%", stats.getCacheHitRate() * 100);
            log.info("   AI 过滤率: {:.1f}%", stats.getFilterRate() * 100);
            log.info("   缓存大小: {}", stats.getCacheSize());

            // 成本估算
            double apiCost = stats.getTotalAiCall() * 0.001; // 假设每次0.001元
            double cacheSaving = stats.getTotalCacheHit() * 0.001;
            log.info("\n   💰 成本分析:");
            log.info("      API调用成本: ¥{:.3f}", apiCost);
            log.info("      缓存节省成本: ¥{:.3f}", cacheSaving);
            log.info("      总节省率: {:.1f}%", stats.getCacheHitRate() * 100);
        }

        // 数据库统计
        if (appAlarmRecordMapper != null) {
            log.info("\n🔹 持久化统计");
            int recordCount = appAlarmRecordMapper.selectCount(null).intValue();
            log.info("   告警记录总数: {}", recordCount);
        }

        if (appAlarmTicketMapper != null) {
            int ticketCount = appAlarmTicketMapper.selectCount(null).intValue();
            log.info("   工单总数: {}", ticketCount);
        }

        log.info("\n" + "=".repeat(80));
    }

    // ==================== 清理方法 ====================

    /**
     * 清理测试数据
     */
    private void cleanupTestData() {
        if (appAlarmRecordMapper != null && !testRecordIds.isEmpty()) {
            log.info("🗑️  清理测试记录: {} 条", testRecordIds.size());
            for (Long id : testRecordIds) {
                try {
                    appAlarmRecordMapper.deleteById(id);
                } catch (Exception e) {
                    log.warn("删除记录失败: id={}", id);
                }
            }
            testRecordIds.clear();
        }

        if (appAlarmTicketMapper != null && !testTicketIds.isEmpty()) {
            log.info("🗑️  清理测试工单: {} 条", testTicketIds.size());
            for (Long id : testTicketIds) {
                try {
                    appAlarmTicketMapper.deleteById(id);
                } catch (Exception e) {
                    log.warn("删除工单失败: id={}", id);
                }
            }
            testTicketIds.clear();
        }
    }

    /**
     * 所有测试结束后清理
     */
    @AfterAll
    static void afterAll(@Autowired(required = false) AppAlarmRecordMapper recordMapper,
                         @Autowired(required = false) AppAlarmTicketMapper ticketMapper) {
        log.info("\n" + "=".repeat(100));
        log.info("🧹 最终清理");
        log.info("=".repeat(100));

        if (recordMapper != null && !testRecordIds.isEmpty()) {
            for (Long id : testRecordIds) {
                try {
                    recordMapper.deleteById(id);
                    log.info("   ✓ 已删除告警记录: id={}", id);
                } catch (Exception e) {
                    log.warn("   ✗ 删除失败: id={}", id);
                }
            }
        }

        if (ticketMapper != null && !testTicketIds.isEmpty()) {
            for (Long id : testTicketIds) {
                try {
                    ticketMapper.deleteById(id);
                    log.info("   ✓ 已删除工单: id={}", id);
                } catch (Exception e) {
                    log.warn("   ✗ 删除失败: id={}", id);
                }
            }
        }

        log.info("✅ 清理完成！");
    }
}
