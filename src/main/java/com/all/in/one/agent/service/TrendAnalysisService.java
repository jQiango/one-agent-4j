package com.all.in.one.agent.service;

import com.all.in.one.agent.config.ResponsibilityProperties;
import com.all.in.one.agent.dao.entity.AlarmTrendStat;
import com.all.in.one.agent.dao.mapper.AlarmTrendStatMapper;
import com.all.in.one.agent.model.TrendReport;
import com.all.in.one.agent.notification.FeishuNotificationService;
import com.all.in.one.agent.notification.model.FeishuNotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 异常趋势分析服务
 * <p>
 * 提供异常趋势分析、预警检测和未来预测功能
 * 优化版：移除数据库告警表，改为飞书直发
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Service
public class TrendAnalysisService {

    private final AlarmTrendStatMapper trendStatMapper;

    @Autowired(required = false)
    private FeishuNotificationService feishuService;

    @Autowired(required = false)
    private ResponsibilityProperties responsibilityProps;

    /**
     * 预警阈值：变化率超过此值将触发预警
     */
    private static final double ALERT_THRESHOLD_RATE = 50.0;

    /**
     * 趋势判断阈值：斜率超过此值认为是上升/下降趋势
     */
    private static final double TREND_SLOPE_THRESHOLD = 5.0;

    public TrendAnalysisService(AlarmTrendStatMapper trendStatMapper) {
        this.trendStatMapper = trendStatMapper;
        log.info("TrendAnalysisService 初始化完成");
    }

    /**
     * 分析指定服务的异常趋势
     *
     * @param serviceName 服务名称
     * @param days 统计天数
     * @return 趋势报告
     */
    public TrendReport analyzeTrend(String serviceName, int days) {
        log.info("开始分析异常趋势 - 服务: {}, 天数: {}", serviceName, days);

        // 1. 查询历史数据
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<AlarmTrendStat> stats = trendStatMapper.selectByDateRange(
                startDate, endDate, serviceName);

        if (stats.isEmpty()) {
            log.warn("未找到趋势数据 - 服务: {}", serviceName);
            return TrendReport.empty(serviceName);
        }

        // 2. 计算趋势类型
        String trendType = detectTrendType(stats);

        // 3. 计算变化率
        BigDecimal changeRate = calculateChangeRate(stats);

        // 4. 预测未来7天
        Map<LocalDate, Integer> prediction = predictFuture(stats, 7);

        // 5. 分析高峰时段
        Map<Integer, Long> peakHours = analyzePeakHours(serviceName, days);

        // 6. 构建趋势报告
        TrendReport report = TrendReport.builder()
                .serviceName(serviceName)
                .days(days)
                .trendType(trendType)
                .changeRate(changeRate)
                .historicalData(convertToMap(stats))
                .prediction(prediction)
                .peakHours(peakHours)
                .build();

        // 7. 发送告警（如果需要）
        if (shouldAlert(changeRate, trendType)) {
            sendTrendAlert(report);
        }

        log.info("趋势分析完成 - 服务: {}, 趋势: {}, 变化率: {}%",
                serviceName, trendType, changeRate);

        return report;
    }

    /**
     * 检测趋势类型：上升/下降/平稳
     */
    private String detectTrendType(List<AlarmTrendStat> stats) {
        if (stats.size() < 2) {
            return "STABLE";
        }

        // 使用线性回归检测趋势
        double slope = calculateSlope(stats);

        if (slope > TREND_SLOPE_THRESHOLD) {
            return "INCREASING";  // 上升
        } else if (slope < -TREND_SLOPE_THRESHOLD) {
            return "DECREASING";  // 下降
        } else {
            return "STABLE";      // 平稳
        }
    }

    /**
     * 计算斜率（线性回归）
     */
    private double calculateSlope(List<AlarmTrendStat> stats) {
        int n = stats.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = stats.get(i).getExceptionCount() != null ?
                    stats.get(i).getExceptionCount() : 0;
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        // 斜率 = (n*Σxy - Σx*Σy) / (n*Σx² - (Σx)²)
        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 0.0001) {
            return 0;
        }

        return (n * sumXY - sumX * sumY) / denominator;
    }

    /**
     * 计算变化率
     */
    private BigDecimal calculateChangeRate(List<AlarmTrendStat> stats) {
        if (stats.size() < 2) {
            return BigDecimal.ZERO;
        }

        // 最早和最新的数据
        int firstCount = stats.get(0).getExceptionCount() != null ?
                stats.get(0).getExceptionCount() : 0;
        int lastCount = stats.get(stats.size() - 1).getExceptionCount() != null ?
                stats.get(stats.size() - 1).getExceptionCount() : 0;

        if (firstCount == 0) {
            return lastCount > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }

        // 变化率 = (lastCount - firstCount) / firstCount * 100
        BigDecimal rate = BigDecimal.valueOf(lastCount - firstCount)
                .divide(BigDecimal.valueOf(firstCount), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return rate.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 预测未来趋势（简单线性预测）
     */
    private Map<LocalDate, Integer> predictFuture(List<AlarmTrendStat> stats, int days) {
        Map<LocalDate, Integer> prediction = new LinkedHashMap<>();

        if (stats.isEmpty()) {
            return prediction;
        }

        double slope = calculateSlope(stats);
        int lastCount = stats.get(stats.size() - 1).getExceptionCount() != null ?
                stats.get(stats.size() - 1).getExceptionCount() : 0;
        LocalDate lastDate = stats.get(stats.size() - 1).getStatDate();

        for (int i = 1; i <= days; i++) {
            LocalDate futureDate = lastDate.plusDays(i);
            int predictedCount = (int) Math.max(0, lastCount + slope * i);
            prediction.put(futureDate, predictedCount);
        }

        return prediction;
    }

    /**
     * 分析高峰时段
     */
    private Map<Integer, Long> analyzePeakHours(String serviceName, int days) {
        LocalDate date = LocalDate.now().minusDays(1);  // 昨天的数据
        List<AlarmTrendStat> hourlyStats = trendStatMapper.selectHourlyStats(date, serviceName);

        if (hourlyStats.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return hourlyStats.stream()
                .filter(stat -> stat.getStatHour() != null)
                .collect(Collectors.groupingBy(
                        AlarmTrendStat::getStatHour,
                        LinkedHashMap::new,
                        Collectors.summingLong(stat -> stat.getExceptionCount() != null ?
                                stat.getExceptionCount() : 0)
                ));
    }

    /**
     * 判断是否需要预警
     */
    private boolean shouldAlert(BigDecimal changeRate, String trendType) {
        // 变化率超过阈值，或趋势持续上升
        return changeRate.abs().compareTo(BigDecimal.valueOf(ALERT_THRESHOLD_RATE)) > 0
                || "INCREASING".equals(trendType);
    }

    /**
     * 发送趋势告警到飞书
     * <p>
     * 优化版：直接发送飞书通知，不再入库
     * </p>
     */
    private void sendTrendAlert(TrendReport report) {
        try {
            // 如果飞书服务未配置，仅记录日志
            if (feishuService == null) {
                log.warn("[趋势告警] 飞书服务未配置 - 服务={}, 趋势={}, 变化率={}%",
                        report.getServiceName(),
                        report.getTrendType(),
                        report.getChangeRate());
                return;
            }

            // 获取责任人信息
            String owner = getOwnerForService(report.getServiceName());
            String feishuOpenId = getFeishuOpenId(owner);

            // 构建飞书通知请求
            FeishuNotificationRequest request = FeishuNotificationRequest.builder()
                    .title("⚠️ 异常趋势告警")
                    .serviceName(report.getServiceName())
                    .content(buildAlertContent(report))
                    .priority(determinePriority(report.getChangeRate()))
                    .mentionedUsers(feishuOpenId != null ? List.of(feishuOpenId) : List.of())
                    .build();

            // 发送飞书通知
            feishuService.sendNotification(request);

            log.warn("[趋势告警] 已发送飞书通知 - 服务={}, 趋势={}, 变化率={}%, 责任人={}, 预测={}",
                    report.getServiceName(),
                    report.getTrendType(),
                    report.getChangeRate(),
                    owner,
                    report.getPrediction());

        } catch (Exception e) {
            log.error("[趋势告警] 发送飞书通知失败 - service={}", report.getServiceName(), e);
        }
    }

    /**
     * 构建告警消息内容
     */
    private String buildAlertContent(TrendReport report) {
        StringBuilder content = new StringBuilder();

        content.append("**异常趋势检测**\n\n");
        content.append("服务名称: ").append(report.getServiceName()).append("\n");
        content.append("趋势类型: ").append(getTrendEmoji(report.getTrendType()))
                .append(" ").append(report.getTrendType()).append("\n");
        content.append("变化率: ").append(report.getChangeRate()).append("%\n");
        content.append("分析周期: 最近 ").append(report.getDays()).append(" 天\n\n");

        // 历史数据（只展示前3条和后3条）
        content.append("**历史趋势**:\n");
        List<Map.Entry<LocalDate, Integer>> historicalEntries = new ArrayList<>(report.getHistoricalData().entrySet());
        int size = historicalEntries.size();

        if (size <= 6) {
            // 数据少，全部展示
            historicalEntries.forEach(entry ->
                    content.append("  • ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" 次\n"));
        } else {
            // 数据多，展示首尾
            for (int i = 0; i < 3; i++) {
                Map.Entry<LocalDate, Integer> entry = historicalEntries.get(i);
                content.append("  • ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" 次\n");
            }
            content.append("  • ...\n");
            for (int i = size - 3; i < size; i++) {
                Map.Entry<LocalDate, Integer> entry = historicalEntries.get(i);
                content.append("  • ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" 次\n");
            }
        }

        // 预测数据（前3天）
        if (report.getPrediction() != null && !report.getPrediction().isEmpty()) {
            content.append("\n**未来预测** (线性回归):\n");
            report.getPrediction().entrySet().stream()
                    .limit(3)
                    .forEach(entry -> content.append("  • ")
                            .append(entry.getKey())
                            .append(": 约 ")
                            .append(entry.getValue())
                            .append(" 次\n"));
        }

        content.append("\n建议: 请及时排查异常原因");

        return content.toString();
    }

    /**
     * 获取趋势图标
     */
    private String getTrendEmoji(String trendType) {
        return switch (trendType) {
            case "INCREASING" -> "📈";
            case "DECREASING" -> "📉";
            default -> "➡️";
        };
    }

    /**
     * 确定告警优先级
     */
    private String determinePriority(BigDecimal changeRate) {
        double rate = changeRate.abs().doubleValue();
        if (rate >= 100) {
            return "CRITICAL";
        } else if (rate >= 50) {
            return "HIGH";
        } else {
            return "MEDIUM";
        }
    }

    /**
     * 获取服务的责任人
     */
    private String getOwnerForService(String serviceName) {
        if (responsibilityProps == null) {
            return "unknown";
        }
        return responsibilityProps.getOwnerForService(serviceName);
    }

    /**
     * 获取责任人的飞书 OpenID
     */
    private String getFeishuOpenId(String ownerCode) {
        if (responsibilityProps == null || !StringUtils.hasText(ownerCode)) {
            return null;
        }
        return responsibilityProps.getFeishuOpenId(ownerCode);
    }

    /**
     * 转换为Map
     */
    private Map<LocalDate, Integer> convertToMap(List<AlarmTrendStat> stats) {
        return stats.stream()
                .collect(Collectors.toMap(
                        AlarmTrendStat::getStatDate,
                        stat -> stat.getExceptionCount() != null ? stat.getExceptionCount() : 0,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }
}
