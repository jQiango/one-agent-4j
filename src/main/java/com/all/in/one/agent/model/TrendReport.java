package com.all.in.one.agent.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 趋势分析报告
 * <p>
 * 包含历史趋势、变化率、未来预测和高峰时段分析
 * </p>
 *
 * @author One Agent 4J
 */
@Data
@Builder
public class TrendReport {

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 统计天数
     */
    private int days;

    /**
     * 趋势类型: INCREASING/DECREASING/STABLE
     */
    private String trendType;

    /**
     * 变化率(%)
     */
    private BigDecimal changeRate;

    /**
     * 历史数据 (日期 -> 数量)
     */
    private Map<LocalDate, Integer> historicalData;

    /**
     * 未来预测 (日期 -> 预测数量)
     */
    private Map<LocalDate, Integer> prediction;

    /**
     * 高峰时段 (小时 -> 数量)
     */
    private Map<Integer, Long> peakHours;

    /**
     * 创建空报告
     *
     * @param serviceName 服务名称
     * @return 空报告
     */
    public static TrendReport empty(String serviceName) {
        return TrendReport.builder()
                .serviceName(serviceName)
                .trendType("STABLE")
                .changeRate(BigDecimal.ZERO)
                .days(0)
                .build();
    }

    /**
     * 判断趋势是否上升
     *
     * @return true-上升趋势
     */
    public boolean isIncreasing() {
        return "INCREASING".equals(trendType);
    }

    /**
     * 判断趋势是否下降
     *
     * @return true-下降趋势
     */
    public boolean isDecreasing() {
        return "DECREASING".equals(trendType);
    }
}
