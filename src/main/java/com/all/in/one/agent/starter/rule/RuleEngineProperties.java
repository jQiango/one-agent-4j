package com.all.in.one.agent.starter.rule;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则引擎配置
 * <p>
 * 基于可配置规则的业务逻辑过滤
 * </p>
 *
 * @author One Agent 4J
 */
@Data
@Component
@ConfigurationProperties(prefix = "one-agent.rule-engine")
public class RuleEngineProperties {

    /**
     * 是否启用规则引擎（默认 true）
     */
    private boolean enabled = true;

    /**
     * 频率限制规则配置
     */
    private FrequencyLimitConfig frequencyLimit = new FrequencyLimitConfig();

    /**
     * 时间窗口规则配置
     */
    private TimeWindowConfig timeWindow = new TimeWindowConfig();

    /**
     * 环境规则配置
     */
    private EnvironmentConfig environment = new EnvironmentConfig();

    /**
     * 频率限制配置
     */
    @Data
    public static class FrequencyLimitConfig {
        /**
         * 是否启用（默认 true）
         */
        private boolean enabled = true;

        /**
         * 时间窗口（分钟）
         * 默认: 5 分钟
         */
        private int windowMinutes = 5;

        /**
         * 窗口内最大允许次数
         * 超过此次数的异常将被过滤
         * 默认: 10
         */
        private int maxCount = 10;
    }

    /**
     * 时间窗口配置
     */
    @Data
    public static class TimeWindowConfig {
        /**
         * 是否启用（默认 false）
         */
        private boolean enabled = false;

        /**
         * 静默时段（24小时制，格式：HH-HH）
         * 例如：2-6 表示凌晨 2 点到 6 点
         * 默认: 空（不静默）
         */
        private String quietHours = "";

        /**
         * 静默时段允许的严重级别
         * 只有这些级别的异常才会在静默时段告警
         * 默认: P0（只有 P0 级别在静默时段告警）
         */
        private List<String> allowedSeverities = new ArrayList<>(List.of("P0"));
    }

    /**
     * 环境规则配置
     */
    @Data
    public static class EnvironmentConfig {
        /**
         * 是否启用（默认 false）
         */
        private boolean enabled = false;

        /**
         * 测试环境过滤的严重级别
         * 测试环境中这些级别的异常将被过滤
         * 默认: P3, P4（测试环境不告警低优先级异常）
         */
        private List<String> testFilterSeverities = new ArrayList<>(List.of("P3", "P4"));

        /**
         * 生产环境过滤的严重级别
         * 默认: 空（生产环境不过滤）
         */
        private List<String> prodFilterSeverities = new ArrayList<>();
    }
}
