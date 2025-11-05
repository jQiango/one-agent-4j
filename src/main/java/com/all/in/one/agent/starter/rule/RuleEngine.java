package com.all.in.one.agent.starter.rule;

import com.all.in.one.agent.common.model.ExceptionInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 规则引擎
 * <p>
 * 按优先级执行所有规则，任何一个规则匹配则过滤
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Component
public class RuleEngine {

    private final List<DenoiseRule> rules;
    private final RuleEngineProperties properties;

    // 统计信息
    private long totalChecked = 0;
    private long totalFiltered = 0;

    public RuleEngine(List<DenoiseRule> rules, RuleEngineProperties properties) {
        this.rules = rules;
        this.properties = properties;

        // 按优先级排序
        this.rules.sort(Comparator.comparingInt(DenoiseRule::getPriority));

        log.info("规则引擎已初始化 - enabled={}, ruleCount={}",
                properties.isEnabled(), rules.size());
        rules.forEach(rule -> log.info("  - {} (priority={}, enabled={})",
                rule.getRuleName(), rule.getPriority(), rule.isEnabled()));
    }

    /**
     * 评估异常是否应该被过滤
     *
     * @param exceptionInfo 异常信息
     * @return 过滤结果
     */
    public FilterResult evaluate(ExceptionInfo exceptionInfo) {
        if (!properties.isEnabled()) {
            return FilterResult.pass();
        }

        totalChecked++;

        // 遍历所有规则（按优先级）
        for (DenoiseRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }

            try {
                if (rule.shouldFilter(exceptionInfo)) {
                    // 规则匹配，过滤此异常
                    totalFiltered++;
                    log.info("规则引擎过滤异常 - fingerprint={}, rule={}, reason={}",
                            exceptionInfo.getFingerprint(),
                            rule.getRuleName(),
                            rule.getReason());
                    return FilterResult.filtered(rule.getRuleName(), rule.getReason());
                }
            } catch (Exception e) {
                log.error("规则执行失败 - rule={}, error={}",
                        rule.getRuleName(), e.getMessage(), e);
                // 规则执行失败，继续下一个规则
            }
        }

        return FilterResult.pass();
    }

    /**
     * 获取统计信息
     */
    public RuleEngineStats getStats() {
        return RuleEngineStats.builder()
                .totalChecked(totalChecked)
                .totalFiltered(totalFiltered)
                .filterRate(totalChecked > 0 ? (double) totalFiltered / totalChecked : 0.0)
                .enabledRuleCount(rules.stream().filter(DenoiseRule::isEnabled).count())
                .build();
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalChecked = 0;
        totalFiltered = 0;
    }

    /**
     * 过滤结果
     */
    @Data
    @AllArgsConstructor
    public static class FilterResult {
        /**
         * 是否被过滤
         */
        private boolean filtered;

        /**
         * 匹配的规则名称
         */
        private String ruleName;

        /**
         * 过滤原因
         */
        private String reason;

        /**
         * 创建通过结果（不过滤）
         */
        public static FilterResult pass() {
            return new FilterResult(false, null, null);
        }

        /**
         * 创建过滤结果
         */
        public static FilterResult filtered(String ruleName, String reason) {
            return new FilterResult(true, ruleName, reason);
        }
    }

    /**
     * 规则引擎统计信息
     */
    @Data
    @Builder
    public static class RuleEngineStats {
        private long totalChecked;
        private long totalFiltered;
        private double filterRate;
        private long enabledRuleCount;
    }
}
