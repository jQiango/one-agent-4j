package com.all.in.one.agent.service;

import com.all.in.one.agent.dao.entity.AlarmTrendStat;
import com.all.in.one.agent.dao.mapper.AlarmTrendStatMapper;
import com.all.in.one.agent.dao.mapper.AlarmTrendAlertMapper;
import com.all.in.one.agent.model.TrendReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 趋势分析服务测试
 *
 * @author One Agent 4J
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("趋势分析服务测试")
class TrendAnalysisServiceTest {

    @Mock
    private AlarmTrendStatMapper trendStatMapper;

    @Mock
    private AlarmTrendAlertMapper trendAlertMapper;

    @InjectMocks
    private TrendAnalysisService trendAnalysisService;

    @Test
    @DisplayName("测试趋势分析 - 上升趋势")
    void testAnalyzeTrend_Increasing() {
        // Given: 准备7天的递增数据
        String serviceName = "payment-service";
        List<AlarmTrendStat> stats = createIncreasingStats(serviceName, 7);

        when(trendStatMapper.selectByDateRange(any(), any(), eq(serviceName)))
                .thenReturn(stats);
        when(trendStatMapper.selectHourlyStats(any(), eq(serviceName)))
                .thenReturn(new ArrayList<>());

        // When: 执行趋势分析
        TrendReport report = trendAnalysisService.analyzeTrend(serviceName, 7);

        // Then: 验证结果
        assertNotNull(report);
        assertEquals(serviceName, report.getServiceName());
        assertEquals("INCREASING", report.getTrendType());
        assertTrue(report.getChangeRate().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(7, report.getDays());
        assertNotNull(report.getHistoricalData());
        assertNotNull(report.getPrediction());

        // 验证是否创建了预警
        verify(trendAlertMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("测试趋势分析 - 平稳趋势")
    void testAnalyzeTrend_Stable() {
        // Given: 准备7天的平稳数据
        String serviceName = "order-service";
        List<AlarmTrendStat> stats = createStableStats(serviceName, 7);

        when(trendStatMapper.selectByDateRange(any(), any(), eq(serviceName)))
                .thenReturn(stats);
        when(trendStatMapper.selectHourlyStats(any(), eq(serviceName)))
                .thenReturn(new ArrayList<>());

        // When: 执行趋势分析
        TrendReport report = trendAnalysisService.analyzeTrend(serviceName, 7);

        // Then: 验证结果
        assertNotNull(report);
        assertEquals("STABLE", report.getTrendType());
        assertTrue(report.getChangeRate().abs().compareTo(BigDecimal.valueOf(20)) < 0);

        // 验证不应创建预警
        verify(trendAlertMapper, never()).insert(any());
    }

    @Test
    @DisplayName("测试趋势分析 - 无数据")
    void testAnalyzeTrend_NoData() {
        // Given: 无数据
        String serviceName = "unknown-service";

        when(trendStatMapper.selectByDateRange(any(), any(), eq(serviceName)))
                .thenReturn(new ArrayList<>());

        // When: 执行趋势分析
        TrendReport report = trendAnalysisService.analyzeTrend(serviceName, 7);

        // Then: 验证返回空报告
        assertNotNull(report);
        assertEquals(serviceName, report.getServiceName());
        assertEquals("STABLE", report.getTrendType());
        assertEquals(BigDecimal.ZERO, report.getChangeRate());
    }

    @Test
    @DisplayName("测试趋势分析 - 预测功能")
    void testAnalyzeTrend_Prediction() {
        // Given: 准备递增数据
        String serviceName = "payment-service";
        List<AlarmTrendStat> stats = createIncreasingStats(serviceName, 7);

        when(trendStatMapper.selectByDateRange(any(), any(), eq(serviceName)))
                .thenReturn(stats);
        when(trendStatMapper.selectHourlyStats(any(), eq(serviceName)))
                .thenReturn(new ArrayList<>());

        // When: 执行趋势分析
        TrendReport report = trendAnalysisService.analyzeTrend(serviceName, 7);

        // Then: 验证预测数据
        assertNotNull(report.getPrediction());
        assertEquals(7, report.getPrediction().size());

        // 验证预测日期连续
        LocalDate lastHistoricalDate = stats.get(stats.size() - 1).getStatDate();
        LocalDate firstPredictionDate = report.getPrediction().keySet().iterator().next();
        assertEquals(lastHistoricalDate.plusDays(1), firstPredictionDate);
    }

    /**
     * 创建递增的统计数据
     */
    private List<AlarmTrendStat> createIncreasingStats(String serviceName, int days) {
        List<AlarmTrendStat> stats = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(days - 1);

        for (int i = 0; i < days; i++) {
            AlarmTrendStat stat = new AlarmTrendStat();
            stat.setId((long) (i + 1));
            stat.setStatDate(startDate.plusDays(i));
            stat.setServiceName(serviceName);
            stat.setExceptionType("NullPointerException");
            stat.setEnvironment("prod");
            stat.setExceptionCount(50 + i * 10);  // 递增：50, 60, 70, ...
            stat.setUniqueFingerprintCount(5 + i);
            stat.setP0Count(1);
            stat.setP1Count(5 + i);
            stat.setP2Count(44 + i * 10);
            stat.setCreatedAt(LocalDateTime.now());
            stats.add(stat);
        }

        return stats;
    }

    /**
     * 创建平稳的统计数据
     */
    private List<AlarmTrendStat> createStableStats(String serviceName, int days) {
        List<AlarmTrendStat> stats = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(days - 1);

        for (int i = 0; i < days; i++) {
            AlarmTrendStat stat = new AlarmTrendStat();
            stat.setId((long) (i + 1));
            stat.setStatDate(startDate.plusDays(i));
            stat.setServiceName(serviceName);
            stat.setExceptionType("SQLException");
            stat.setEnvironment("prod");
            stat.setExceptionCount(30 + (i % 3 - 1));  // 波动：29, 30, 31, 29, 30, 31, ...
            stat.setUniqueFingerprintCount(3);
            stat.setP1Count(5);
            stat.setP2Count(25);
            stat.setCreatedAt(LocalDateTime.now());
            stats.add(stat);
        }

        return stats;
    }
}
