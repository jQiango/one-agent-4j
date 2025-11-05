package com.all.in.one.agent.api.controller;

import com.all.in.one.agent.ai.service.AiDenoiseService;
import com.all.in.one.agent.starter.dedup.FingerprintDeduplicator;
import com.all.in.one.agent.starter.filter.IgnoreListFilter;
import com.all.in.one.agent.starter.rule.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * 漏斗降噪统计 API
 * <p>
 * 提供 3 层漏斗模型的统计信息查询
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/denoise")
@ConditionalOnProperty(prefix = "one-agent.api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DenoiseStatsController {

    @Autowired(required = false)
    private IgnoreListFilter ignoreListFilter;

    @Autowired(required = false)
    private FingerprintDeduplicator fingerprintDeduplicator;

    @Autowired(required = false)
    private RuleEngine ruleEngine;

    @Autowired(required = false)
    private FrequencyLimitRule frequencyLimitRule;

    @Autowired(required = false)
    private TimeWindowRule timeWindowRule;

    @Autowired(required = false)
    private EnvironmentRule environmentRule;

    @Autowired(required = false)
    private AiDenoiseService aiDenoiseService;

    /**
     * 获取完整漏斗统计
     */
    @GetMapping("/stats")
    public FunnelStatsResponse getStats() {
        FunnelStatsResponse response = FunnelStatsResponse.builder()
                .layer0Stats(ignoreListFilter != null ? ignoreListFilter.getStats() : null)
                .layer1Stats(fingerprintDeduplicator != null ? fingerprintDeduplicator.getStats() : null)
                .layer15Stats(ruleEngine != null ? ruleEngine.getStats() : null)
                .frequencyLimitStats(frequencyLimitRule != null ? frequencyLimitRule.getStats() : null)
                .timeWindowStats(timeWindowRule != null ? timeWindowRule.getStats() : null)
                .environmentStats(environmentRule != null ? environmentRule.getStats() : null)
                .layer2Stats(aiDenoiseService != null ? aiDenoiseService.getStats() : null)
                .build();

        log.info("查询漏斗统计信息 - layer0={}, layer1={}, layer1.5={}, layer2={}",
                response.getLayer0Stats() != null,
                response.getLayer1Stats() != null,
                response.getLayer15Stats() != null,
                response.getLayer2Stats() != null);

        return response;
    }

    /**
     * 获取规则引擎统计（第 1.5 层）
     */
    @GetMapping("/stats/layer15")
    public RuleEngineStatsResponse getLayer15Stats() {
        if (ruleEngine == null) {
            return RuleEngineStatsResponse.builder()
                    .ruleEngineStats(null)
                    .frequencyLimitStats(null)
                    .timeWindowStats(null)
                    .environmentStats(null)
                    .build();
        }
        return RuleEngineStatsResponse.builder()
                .ruleEngineStats(ruleEngine.getStats())
                .frequencyLimitStats(frequencyLimitRule != null ? frequencyLimitRule.getStats() : null)
                .timeWindowStats(timeWindowRule != null ? timeWindowRule.getStats() : null)
                .environmentStats(environmentRule != null ? environmentRule.getStats() : null)
                .build();
    }

    /**
     * 获取第 0 层统计（基础过滤）
     */
    @GetMapping("/stats/layer0")
    public IgnoreListFilter.FilterStats getLayer0Stats() {
        if (ignoreListFilter == null) {
            return IgnoreListFilter.FilterStats.builder()
                    .totalChecked(0)
                    .totalFiltered(0)
                    .filterRate(0.0)
                    .build();
        }
        return ignoreListFilter.getStats();
    }

    /**
     * 获取第 1 层统计（指纹去重）
     */
    @GetMapping("/stats/layer1")
    public FingerprintDeduplicator.DedupStats getLayer1Stats() {
        if (fingerprintDeduplicator == null) {
            return FingerprintDeduplicator.DedupStats.builder()
                    .totalChecked(0)
                    .totalFiltered(0)
                    .filterRate(0.0)
                    .cacheSize(0)
                    .cacheHitRate(0.0)
                    .cacheEvictionCount(0)
                    .build();
        }
        return fingerprintDeduplicator.getStats();
    }

    /**
     * 获取第 2 层统计（AI 智能去噪）
     */
    @GetMapping("/stats/layer2")
    public AiDenoiseService.AiDenoiseStats getLayer2Stats() {
        if (aiDenoiseService == null) {
            return AiDenoiseService.AiDenoiseStats.builder()
                    .totalChecked(0)
                    .totalCacheHit(0)
                    .totalAiCall(0)
                    .totalFiltered(0)
                    .cacheHitRate(0.0)
                    .filterRate(0.0)
                    .cacheSize(0)
                    .cacheEvictionCount(0)
                    .build();
        }
        return aiDenoiseService.getStats();
    }

    /**
     * 重置所有统计信息
     */
    @PostMapping("/stats/reset")
    public ResetResponse resetStats() {
        if (ignoreListFilter != null) {
            ignoreListFilter.resetStats();
        }
        if (fingerprintDeduplicator != null) {
            fingerprintDeduplicator.resetStats();
        }
        if (ruleEngine != null) {
            ruleEngine.resetStats();
        }
        if (frequencyLimitRule != null) {
            frequencyLimitRule.resetStats();
        }
        if (timeWindowRule != null) {
            timeWindowRule.resetStats();
        }
        if (environmentRule != null) {
            environmentRule.resetStats();
        }
        if (aiDenoiseService != null) {
            aiDenoiseService.resetStats();
        }

        log.info("已重置所有漏斗统计信息");
        return new ResetResponse("统计信息已重置");
    }

    /**
     * 清空所有缓存
     */
    @PostMapping("/cache/clear")
    public ResetResponse clearCache() {
        if (fingerprintDeduplicator != null) {
            fingerprintDeduplicator.clearCache();
        }
        if (aiDenoiseService != null) {
            aiDenoiseService.clearCache();
        }

        log.info("已清空所有漏斗缓存");
        return new ResetResponse("缓存已清空");
    }

    /**
     * 完整漏斗统计响应
     */
    @Data
    @Builder
    public static class FunnelStatsResponse {
        /**
         * 第 0 层：基础过滤统计
         */
        private IgnoreListFilter.FilterStats layer0Stats;

        /**
         * 第 1 层：指纹去重统计
         */
        private FingerprintDeduplicator.DedupStats layer1Stats;

        /**
         * 第 1.5 层：规则引擎统计
         */
        private RuleEngine.RuleEngineStats layer15Stats;

        /**
         * 频率限制规则统计
         */
        private FrequencyLimitRule.FrequencyLimitStats frequencyLimitStats;

        /**
         * 时间窗口规则统计
         */
        private TimeWindowRule.TimeWindowStats timeWindowStats;

        /**
         * 环境规则统计
         */
        private EnvironmentRule.EnvironmentStats environmentStats;

        /**
         * 第 2 层：AI 智能去噪统计
         */
        private AiDenoiseService.AiDenoiseStats layer2Stats;
    }

    /**
     * 规则引擎统计响应
     */
    @Data
    @Builder
    public static class RuleEngineStatsResponse {
        private RuleEngine.RuleEngineStats ruleEngineStats;
        private FrequencyLimitRule.FrequencyLimitStats frequencyLimitStats;
        private TimeWindowRule.TimeWindowStats timeWindowStats;
        private EnvironmentRule.EnvironmentStats environmentStats;
    }

    /**
     * 重置响应
     */
    @Data
    @AllArgsConstructor
    public static class ResetResponse {
        private String message;
    }
}
