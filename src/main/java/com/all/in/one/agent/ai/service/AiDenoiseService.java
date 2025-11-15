package com.all.in.one.agent.ai.service;

import com.all.in.one.agent.ai.config.AiDenoiseProperties;
import com.all.in.one.agent.ai.model.DenoiseDecision;
import com.all.in.one.agent.ai.prompt.DenoisePrompt;
import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import com.all.in.one.agent.dao.mapper.AppAlarmRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 第 2 层：AI 智能去噪服务
 * <p>
 * 使用大模型判断异常是否需要报警
 * 预期过滤率: 70-80%（在通过前两层后）
 * 性能: 1-3s（有缓存时 <1ms）
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "one-agent.ai-denoise", name = "enabled", havingValue = "true")
public class AiDenoiseService {

    private final AppAlarmRecordMapper appAlarmRecordMapper;
    private final DenoiseAiService denoiseAiService;
    private final ObjectMapper objectMapper;
    private final AiDenoiseProperties properties;

    // AI 决策结果缓存
    private final Cache<String, DenoiseDecision> decisionCache;

    // 统计信息
    private long totalChecked = 0;
    private long totalCacheHit = 0;
    private long totalAiCall = 0;
    private long totalFiltered = 0;

    public AiDenoiseService(AppAlarmRecordMapper appAlarmRecordMapper,
                            DenoiseAiService denoiseAiService,
                            ObjectMapper objectMapper,
                            AiDenoiseProperties properties) {
        this.appAlarmRecordMapper = appAlarmRecordMapper;
        this.denoiseAiService = denoiseAiService;
        this.objectMapper = objectMapper;
        this.properties = properties;

        // 初始化缓存
        if (properties.isCacheEnabled()) {
            this.decisionCache = Caffeine.newBuilder()
                    .expireAfterWrite(properties.getCacheTtlMinutes(), TimeUnit.MINUTES)
                    .maximumSize(properties.getMaxCacheSize())
                    .recordStats()
                    .build();
            log.info("AI 决策缓存已启用 - ttl={}分钟, maxSize={}",
                    properties.getCacheTtlMinutes(), properties.getMaxCacheSize());
        } else {
            this.decisionCache = null;
            log.info("AI 决策缓存已禁用");
        }

        log.info("AI 智能去噪服务已启动 - look back Minutes={}, maxHistoryRecords={}, cacheEnabled={}",
                properties.getLookbackMinutes(),
                properties.getMaxHistoryRecords(),
                properties.isCacheEnabled());
    }

    /**
     * 判断异常是否需要报警（第 2 层：AI 智能去噪）
     *
     * @param exceptionInfo 新发生的异常
     * @return 去噪判断结果
     */
    public DenoiseDecision shouldAlert(ExceptionInfo exceptionInfo) {
        totalChecked++;
        String fingerprint = exceptionInfo.getFingerprint();

        try {
            // 1. 先查缓存（如果启用）
            if (decisionCache != null) {
                DenoiseDecision cached = decisionCache.getIfPresent(fingerprint);
                if (cached != null) {
                    totalCacheHit++;
                    if (!cached.isShouldAlert()) {
                        totalFiltered++;
                    }
                    log.debug("使用缓存的 AI 决策 - fingerprint={}, shouldAlert={}, cacheHitRate={:.2f}%",
                            fingerprint, cached.isShouldAlert(), getCacheHitRate() * 100);
                    return cached;
                }
            }

            log.debug("开始 AI 去噪判断 - fingerprint={}, exceptionType={}, location={}",
                    fingerprint, exceptionInfo.getExceptionType(), exceptionInfo.getErrorLocation());

            // 2. 查询最近的历史告警
            List<AppAlarmRecord> recentExceptions = queryRecentExceptions(exceptionInfo);
            log.debug("查询到最近 {} 分钟内的历史告警: {} 条",
                    properties.getLookbackMinutes(), recentExceptions.size());

            // 3. 构建提示词
            String prompt = DenoisePrompt.buildPrompt(exceptionInfo, recentExceptions);
            log.debug("提示词已构建，长度: {} 字符", prompt.length());

            // 4. 调用 AI 服务
            totalAiCall++;
            long startTime = System.currentTimeMillis();
            String aiResponse = denoiseAiService.analyzeException(prompt);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("大模型响应完成 - 耗时: {}ms", duration);

            // 5. 解析结果
            DenoiseDecision decision = parseAiResponse(aiResponse);

            // 6. 缓存结果
            if (decisionCache != null) {
                decisionCache.put(fingerprint, decision);
            }

            // 7. 统计
            if (!decision.isShouldAlert()) {
                totalFiltered++;
            }

            log.info("AI 去噪判断完成 - fingerprint={}, shouldAlert={}, isDuplicate={}, " +
                            "similarityScore={}, severity={}, aiCallCount={}, cacheHitRate={:.2f}%",
                    fingerprint,
                    decision.isShouldAlert(),
                    decision.isDuplicate(),
                    decision.getSimilarityScore(),
                    decision.getSuggestedSeverity(),
                    totalAiCall,
                    getCacheHitRate() * 100);

            return decision;

        } catch (Exception e) {
            log.error("AI 去噪判断失败，默认允许报警 - fingerprint={}, error={}",
                    fingerprint, e.getMessage(), e);
            // 如果 AI 判断失败，默认允许报警，避免漏报
            return DenoiseDecision.builder()
                    .shouldAlert(true)
                    .isDuplicate(false)
                    .similarityScore(0.0)
                    .suggestedSeverity("P3")
                    .reason("AI 判断失败，默认报警: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 查询最近的历史告警
     */
    private List<AppAlarmRecord> queryRecentExceptions(ExceptionInfo exceptionInfo) {
        LocalDateTime startTime = LocalDateTime.ofInstant(
                exceptionInfo.getOccurredAt().minusSeconds(properties.getLookbackMinutes() * 60L),
                ZoneId.systemDefault()
        );

        return appAlarmRecordMapper.findRecentExceptions(
                exceptionInfo.getAppName(),
                startTime,
                properties.getMaxHistoryRecords()
        );
    }

    /**
     * 获取统计信息
     */
    public AiDenoiseStats getStats() {
        CacheStats cacheStats = decisionCache != null ? decisionCache.stats() : null;
        return AiDenoiseStats.builder()
                .totalChecked(totalChecked)
                .totalCacheHit(totalCacheHit)
                .totalAiCall(totalAiCall)
                .totalFiltered(totalFiltered)
                .cacheHitRate(getCacheHitRate())
                .filterRate(totalChecked > 0 ? (double) totalFiltered / totalChecked : 0.0)
                .cacheSize(decisionCache != null ? decisionCache.estimatedSize() : 0)
                .cacheEvictionCount(cacheStats != null ? cacheStats.evictionCount() : 0)
                .build();
    }

    /**
     * 计算缓存命中率
     */
    private double getCacheHitRate() {
        if (totalChecked == 0) {
            return 0.0;
        }
        return (double) totalCacheHit / totalChecked;
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalChecked = 0;
        totalCacheHit = 0;
        totalAiCall = 0;
        totalFiltered = 0;
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        if (decisionCache != null) {
            decisionCache.invalidateAll();
            log.info("AI 决策缓存已清空");
        }
    }

    /**
     * 解析 AI 响应
     */
    private DenoiseDecision parseAiResponse(String aiResponse) {
        try {
            // 尝试提取 JSON（AI 可能会返回带有 markdown 标记的 JSON）
            String json = extractJson(aiResponse);
            return objectMapper.readValue(json, DenoiseDecision.class);
        } catch (Exception e) {
            log.error("解析 AI 响应失败 - response={}, error={}", aiResponse, e.getMessage());
            // 解析失败时的降级策略
            return DenoiseDecision.builder()
                    .shouldAlert(true)
                    .isDuplicate(false)
                    .similarityScore(0.0)
                    .suggestedSeverity("P3")
                    .reason("AI 响应解析失败，默认报警")
                    .build();
        }
    }

    /**
     * 从 AI 响应中提取 JSON
     * AI 可能会返回: ```json\n{...}\n```
     */
    private String extractJson(String response) {
        if (response == null) {
            return "{}";
        }

        // 去除 markdown 代码块标记
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        // 找到第一个 { 和最后一个 }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned.trim();
    }

    /**
     * AI 去噪统计信息
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class AiDenoiseStats {
        /**
         * 总检查次数
         */
        private long totalChecked;

        /**
         * 缓存命中次数
         */
        private long totalCacheHit;

        /**
         * AI 实际调用次数
         */
        private long totalAiCall;

        /**
         * 总过滤次数（AI 判断不需要告警）
         */
        private long totalFiltered;

        /**
         * 缓存命中率
         */
        private double cacheHitRate;

        /**
         * 过滤率（被 AI 过滤的比例）
         */
        private double filterRate;

        /**
         * 当前缓存大小
         */
        private long cacheSize;

        /**
         * 缓存驱逐次数
         */
        private long cacheEvictionCount;
    }
}
