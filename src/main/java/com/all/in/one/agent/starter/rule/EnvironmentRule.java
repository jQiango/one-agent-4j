package com.all.in.one.agent.starter.rule;

import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.service.TicketGenerationService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 环境规则
 * <p>
 * 不同环境采用不同的过滤策略
 * 例如：测试环境的低优先级异常不告警
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Component
public class EnvironmentRule implements DenoiseRule {

    private final RuleEngineProperties properties;
    private final TicketGenerationService ticketGenerationService;

    // 统计信息
    private long totalChecked = 0;
    private long totalFiltered = 0;

    public EnvironmentRule(RuleEngineProperties properties,
                          TicketGenerationService ticketGenerationService) {
        this.properties = properties;
        this.ticketGenerationService = ticketGenerationService;

        log.info("环境规则已初始化 - enabled={}, testFilterSeverities={}, prodFilterSeverities={}",
                properties.getEnvironment().isEnabled(),
                properties.getEnvironment().getTestFilterSeverities(),
                properties.getEnvironment().getProdFilterSeverities());
    }

    @Override
    public boolean shouldFilter(ExceptionInfo exceptionInfo) {
        if (!isEnabled()) {
            return false;
        }

        totalChecked++;

        String environment = exceptionInfo.getEnvironment();
        if (environment == null || environment.isEmpty()) {
            return false; // 未知环境，不过滤
        }

        // 计算严重级别
        String severity = ticketGenerationService.calculateSeverity(exceptionInfo);

        // 判断是否应该过滤
        boolean shouldFilter = false;
        List<String> filterSeverities = null;

        if (isTestEnvironment(environment)) {
            // 测试环境
            filterSeverities = properties.getEnvironment().getTestFilterSeverities();
            shouldFilter = filterSeverities.contains(severity);
            if (shouldFilter) {
                log.debug("测试环境过滤低优先级异常 - env={}, severity={}", environment, severity);
            }
        } else if (isProdEnvironment(environment)) {
            // 生产环境
            filterSeverities = properties.getEnvironment().getProdFilterSeverities();
            shouldFilter = filterSeverities.contains(severity);
            if (shouldFilter) {
                log.debug("生产环境过滤指定级别异常 - env={}, severity={}", environment, severity);
            }
        }

        if (shouldFilter) {
            totalFiltered++;
        }

        return shouldFilter;
    }

    /**
     * 判断是否是测试环境
     */
    private boolean isTestEnvironment(String environment) {
        String env = environment.toLowerCase();
        return env.contains("test") || env.contains("dev") || env.contains("local");
    }

    /**
     * 判断是否是生产环境
     */
    private boolean isProdEnvironment(String environment) {
        String env = environment.toLowerCase();
        return env.contains("prod") || env.contains("production");
    }

    @Override
    public String getRuleName() {
        return "EnvironmentRule";
    }

    @Override
    public String getReason() {
        return "环境差异化过滤（测试/生产环境不同策略）";
    }

    @Override
    public int getPriority() {
        return 30; // 较低优先级
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled() && properties.getEnvironment().isEnabled();
    }

    /**
     * 获取统计信息
     */
    public EnvironmentStats getStats() {
        return EnvironmentStats.builder()
                .totalChecked(totalChecked)
                .totalFiltered(totalFiltered)
                .filterRate(totalChecked > 0 ? (double) totalFiltered / totalChecked : 0.0)
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
    public static class EnvironmentStats {
        private long totalChecked;
        private long totalFiltered;
        private double filterRate;
    }
}
