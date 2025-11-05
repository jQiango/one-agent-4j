package com.all.in.one.agent.starter.dedup;

import com.all.in.one.agent.common.model.ExceptionInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 第 1 层：指纹去重器
 * <p>
 * 核心降噪层，在时间窗口内对相同指纹的异常进行去重
 * 预期过滤率: 50-60%
 * 性能: < 1ms
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Component
public class FingerprintDeduplicator {

    private final Cache<String, FingerprintRecord> fingerprintCache;
    private final FingerprintDedupProperties properties;

    // 统计信息
    private long totalChecked = 0;
    private long totalFiltered = 0;

    public FingerprintDeduplicator(FingerprintDedupProperties properties) {
        this.properties = properties;
        this.fingerprintCache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getTimeWindowMinutes(), TimeUnit.MINUTES)
                .maximumSize(properties.getMaxCacheSize())
                .recordStats()
                .build();

        log.info("指纹去重器初始化完成 - timeWindow={}分钟, maxCacheSize={}",
                properties.getTimeWindowMinutes(), properties.getMaxCacheSize());
    }

    /**
     * 检查指纹是否重复
     *
     * @param exceptionInfo 异常信息
     * @return true=重复（应该过滤），false=首次出现（应该处理）
     */
    public boolean isDuplicate(ExceptionInfo exceptionInfo) {
        if (!properties.isEnabled()) {
            return false;
        }

        totalChecked++;

        String fingerprint = exceptionInfo.getFingerprint();
        if (fingerprint == null || fingerprint.isEmpty()) {
            log.warn("异常指纹为空，无法去重 - exceptionType={}", exceptionInfo.getExceptionType());
            return false;
        }

        // 尝试获取或创建记录
        FingerprintRecord record = fingerprintCache.get(fingerprint, key -> {
            // 首次出现，创建新记录
            log.debug("首次出现指纹: fingerprint={}, type={}, location={}",
                    fingerprint,
                    exceptionInfo.getExceptionType(),
                    exceptionInfo.getErrorLocation());
            return new FingerprintRecord(
                    System.currentTimeMillis(),
                    exceptionInfo.getAppName(),
                    exceptionInfo.getExceptionType(),
                    exceptionInfo.getErrorLocation(),
                    1
            );
        });

        long now = System.currentTimeMillis();
        long timeSinceFirst = now - record.getFirstSeenTime();

        // 如果刚创建（时间差 < 100ms），说明是首次出现
        if (timeSinceFirst < 100) {
            return false;
        }

        // 重复异常
        record.incrementCount();
        totalFiltered++;

        log.debug("重复异常已过滤: fingerprint={}, 距首次={}ms, 累计次数={}",
                fingerprint, timeSinceFirst, record.getOccurrenceCount());

        return true;
    }

    /**
     * 获取去重统计
     */
    public DedupStats getStats() {
        CacheStats cacheStats = fingerprintCache.stats();
        return DedupStats.builder()
                .totalChecked(totalChecked)
                .totalFiltered(totalFiltered)
                .filterRate(totalChecked > 0 ? (double) totalFiltered / totalChecked : 0.0)
                .cacheSize(fingerprintCache.estimatedSize())
                .cacheHitRate(cacheStats.hitRate())
                .cacheEvictionCount(cacheStats.evictionCount())
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
     * 清空缓存
     */
    public void clearCache() {
        fingerprintCache.invalidateAll();
        log.info("指纹去重缓存已清空");
    }

    /**
     * 指纹记录
     */
    @Data
    @AllArgsConstructor
    public static class FingerprintRecord {
        /**
         * 首次出现时间
         */
        private long firstSeenTime;

        /**
         * 应用名称
         */
        private String appName;

        /**
         * 异常类型
         */
        private String exceptionType;

        /**
         * 错误位置
         */
        private String errorLocation;

        /**
         * 出现次数
         */
        private int occurrenceCount;

        public void incrementCount() {
            this.occurrenceCount++;
        }
    }

    /**
     * 去重统计信息
     */
    @Data
    @lombok.Builder
    public static class DedupStats {
        private long totalChecked;
        private long totalFiltered;
        private double filterRate;
        private long cacheSize;
        private double cacheHitRate;
        private long cacheEvictionCount;
    }
}
