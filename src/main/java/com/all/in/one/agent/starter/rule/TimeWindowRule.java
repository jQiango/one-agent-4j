package com.all.in.one.agent.starter.rule;

import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.service.TicketGenerationService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * 时间窗口规则
 * <p>
 * 非工作时间（如凌晨）只告警高优先级异常，低优先级延迟到白天处理
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Component
public class TimeWindowRule implements DenoiseRule {

    private final RuleEngineProperties properties;
    private final TicketGenerationService ticketGenerationService;

    // 统计信息
    private long totalChecked = 0;
    private long totalFiltered = 0;

    public TimeWindowRule(RuleEngineProperties properties,
                         TicketGenerationService ticketGenerationService) {
        this.properties = properties;
        this.ticketGenerationService = ticketGenerationService;

        log.info("时间窗口规则已初始化 - enabled={}, quietHours={}, allowedSeverities={}",
                properties.getTimeWindow().isEnabled(),
                properties.getTimeWindow().getQuietHours(),
                properties.getTimeWindow().getAllowedSeverities());
    }

    @Override
    public boolean shouldFilter(ExceptionInfo exceptionInfo) {
        if (!isEnabled()) {
            return false;
        }

        totalChecked++;

        // 解析静默时段
        String quietHours = properties.getTimeWindow().getQuietHours();
        if (quietHours == null || quietHours.isEmpty()) {
            return false; // 未配置静默时段
        }

        // 判断当前是否在静默时段
        if (!isInQuietHours(quietHours)) {
            return false; // 不在静默时段
        }

        // 计算严重级别
        String severity = ticketGenerationService.calculateSeverity(exceptionInfo);

        // 检查是否是允许的严重级别
        List<String> allowedSeverities = properties.getTimeWindow().getAllowedSeverities();
        if (allowedSeverities.contains(severity)) {
            log.debug("静默时段但允许告警 - severity={}, time={}", severity, LocalTime.now());
            return false; // 允许告警
        }

        // 过滤低优先级异常
        totalFiltered++;
        log.debug("静默时段过滤低优先级异常 - severity={}, time={}", severity, LocalTime.now());
        return true;
    }

    /**
     * 判断当前时间是否在静默时段
     *
     * @param quietHours 格式：HH-HH，例如 "2-6" 表示 2:00-6:00
     */
    private boolean isInQuietHours(String quietHours) {
        try {
            String[] parts = quietHours.split("-");
            if (parts.length != 2) {
                log.warn("静默时段格式错误: {}, 应为 HH-HH 格式", quietHours);
                return false;
            }

            int startHour = Integer.parseInt(parts[0].trim());
            int endHour = Integer.parseInt(parts[1].trim());
            int currentHour = LocalTime.now().getHour();

            // 跨天情况：如 22-6 (晚上10点到早上6点)
            if (startHour > endHour) {
                return currentHour >= startHour || currentHour < endHour;
            } else {
                return currentHour >= startHour && currentHour < endHour;
            }
        } catch (Exception e) {
            log.error("解析静默时段失败: {}", quietHours, e);
            return false;
        }
    }

    @Override
    public String getRuleName() {
        return "TimeWindowRule";
    }

    @Override
    public String getReason() {
        return String.format("非工作时间（%s）的低优先级异常",
                properties.getTimeWindow().getQuietHours());
    }

    @Override
    public int getPriority() {
        return 20; // 中等优先级
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled() && properties.getTimeWindow().isEnabled();
    }

    /**
     * 获取统计信息
     */
    public TimeWindowStats getStats() {
        return TimeWindowStats.builder()
                .totalChecked(totalChecked)
                .totalFiltered(totalFiltered)
                .filterRate(totalChecked > 0 ? (double) totalFiltered / totalChecked : 0.0)
                .currentlyInQuietHours(isInQuietHours(properties.getTimeWindow().getQuietHours()))
                .build();
    }

    /**
     * 重置统计
     */
    public void resetStats() {
        totalChecked = 0;
        totalFiltered = 0;
    }

    /**
     * 统计信息
     */
    @Data
    @lombok.Builder
    public static class TimeWindowStats {
        private long totalChecked;
        private long totalFiltered;
        private double filterRate;
        private boolean currentlyInQuietHours;
    }
}
