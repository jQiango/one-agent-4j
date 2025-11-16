package com.all.in.one.agent.service;

import com.all.in.one.agent.dao.entity.AlarmSolutionKb;
import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 解决方案知识库服务测试
 *
 * @author One Agent 4J
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("解决方案知识库服务测试")
class SolutionKnowledgeBaseServiceTest {

    @Autowired
    private SolutionKnowledgeBaseService kbService;

    @Test
    @DisplayName("测试精确匹配查找解决方案")
    void testFindRelatedSolutions_ExactMatch() {
        // Given: 创建告警记录，使用已存在的指纹
        AppAlarmRecord alarm = new AppAlarmRecord();
        alarm.setFingerprint("abc123def456");  // 初始化脚本中的示例指纹
        alarm.setAppName("payment-service");
        alarm.setExceptionType("java.lang.NullPointerException");

        // When: 查找相关解决方案
        List<AlarmSolutionKb> solutions = kbService.findRelatedSolutions(alarm);

        // Then: 应找到精确匹配的方案
        assertNotNull(solutions, "解决方案列表不应为空");
        assertFalse(solutions.isEmpty(), "应找到至少1个方案");
        assertEquals("abc123def456", solutions.get(0).getExceptionFingerprint(),
                "应返回精确匹配的方案");
    }

    @Test
    @DisplayName("测试模糊匹配查找解决方案")
    void testFindRelatedSolutions_FuzzyMatch() {
        // Given: 创建告警记录，指纹不匹配但服务和异常类型匹配
        AppAlarmRecord alarm = new AppAlarmRecord();
        alarm.setFingerprint("non-existing-fingerprint");
        alarm.setAppName("payment-service");
        alarm.setExceptionType("java.lang.NullPointerException");

        // When: 查找相关解决方案
        List<AlarmSolutionKb> solutions = kbService.findRelatedSolutions(alarm);

        // Then: 应找到相似方案
        assertNotNull(solutions, "解决方案列表不应为空");
        // 可能找到相似方案，也可能为空（取决于数据库数据）
    }

    @Test
    @DisplayName("测试未找到解决方案")
    void testFindRelatedSolutions_NotFound() {
        // Given: 完全不匹配的告警记录
        AppAlarmRecord alarm = new AppAlarmRecord();
        alarm.setFingerprint("completely-unknown");
        alarm.setAppName("unknown-service");
        alarm.setExceptionType("com.example.UnknownException");

        // When: 查找相关解决方案
        List<AlarmSolutionKb> solutions = kbService.findRelatedSolutions(alarm);

        // Then: 应返回空列表
        assertNotNull(solutions, "解决方案列表不应为null");
        assertTrue(solutions.isEmpty(), "应返回空列表");
    }

    @Test
    @DisplayName("测试空告警记录")
    void testFindRelatedSolutions_NullAlarm() {
        // When: 传入 null
        List<AlarmSolutionKb> solutions = kbService.findRelatedSolutions(null);

        // Then: 应返回空列表
        assertNotNull(solutions, "应返回空列表而不是null");
        assertTrue(solutions.isEmpty(), "应返回空列表");
    }

    @Test
    @DisplayName("测试保存解决方案")
    void testSaveSolution() {
        // Given: 创建新的解决方案
        AlarmSolutionKb solution = new AlarmSolutionKb();
        solution.setExceptionFingerprint("test-fingerprint-001");
        solution.setExceptionType("java.lang.IllegalArgumentException");
        solution.setServiceName("test-service");
        solution.setProblemSummary("测试异常");
        solution.setSolutionType("CODE_FIX");
        solution.setSolutionSteps("1. 检查参数\n2. 添加参数校验");
        solution.setRootCause("参数未校验");
        solution.setResolver("testuser");
        solution.setResolverName("测试用户");

        // When: 保存到知识库
        Long solutionId = kbService.saveSolution(solution);

        // Then: 应保存成功
        assertNotNull(solutionId, "方案ID不应为空");
        assertTrue(solutionId > 0, "方案ID应大于0");
    }

    @Test
    @DisplayName("测试保存空方案")
    void testSaveSolution_Null() {
        // When: 保存 null
        Long solutionId = kbService.saveSolution(null);

        // Then: 应返回 null
        assertNull(solutionId, "保存空方案应返回null");
    }

    @Test
    @DisplayName("测试更新使用次数")
    void testIncrementUsageCount() {
        // Given: 假设方案ID为1存在（初始化脚本插入的）
        Long solutionId = 1L;

        // When: 更新使用次数
        assertDoesNotThrow(() -> kbService.incrementUsageCount(solutionId),
                "更新使用次数不应抛出异常");

        // Then: 使用次数应增加（需要查询数据库验证，这里只验证不抛异常）
    }

    @Test
    @DisplayName("测试更新成功次数和评分")
    void testIncrementSuccessCount() {
        // Given: 假设方案ID为1存在
        Long solutionId = 1L;

        // When: 更新成功次数
        assertDoesNotThrow(() -> kbService.incrementSuccessCount(solutionId),
                "更新成功次数不应抛出异常");

        // Then: 成功次数应增加，评分应重新计算
    }

    @Test
    @DisplayName("测试查询高评分方案")
    void testGetTopRatedSolutions() {
        // When: 查询 TOP 5 高评分方案
        List<AlarmSolutionKb> topSolutions = kbService.getTopRatedSolutions(5);

        // Then: 应返回列表（可能为空）
        assertNotNull(topSolutions, "结果列表不应为null");
        // 如果有数据，应按评分排序
        if (topSolutions.size() > 1) {
            for (int i = 0; i < topSolutions.size() - 1; i++) {
                int currentScore = topSolutions.get(i).getEffectivenessScore();
                int nextScore = topSolutions.get(i + 1).getEffectivenessScore();
                assertTrue(currentScore >= nextScore, "应按评分降序排列");
            }
        }
    }
}
