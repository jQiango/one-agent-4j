package com.all.in.one.agent.starter.rule;

import com.all.in.one.agent.common.model.ExceptionInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 频率限制规则
 * <p>
 * 防止异常风暴：同一指纹在时间窗口内超过指定次数则过滤
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Component
public class FrequencyLimitRule implements DenoiseRule {

    private final RuleEngineProperties properties;
    private final Cache<String, FrequencyRecord> frequencyCache;

    // 统计信息
    private long totalChecked = 0;
    private long totalFiltered = 0;

    public FrequencyLimitRule(RuleEngineProperties properties) {
        this.properties = properties;

        // 初始化频率缓存
        this.frequencyCache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getFrequencyLimit().getWindowMinutes(), TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();

        log.info("频率限制规则已初始化 - enabled={}, window={}分钟, maxCount={}",
                properties.getFrequencyLimit().isEnabled(),
                properties.getFrequencyLimit().getWindowMinutes(),
                properties.getFrequencyLimit().getMaxCount());
    }

    @Override
    public boolean shouldFilter(ExceptionInfo exceptionInfo) {
        if (!isEnabled()) {
            return false;
        }

        totalChecked++;
        String fingerprint = exceptionInfo.getFingerprint();
        int maxCount = properties.getFrequencyLimit().getMaxCount();

        // 获取或创建频率记录
        FrequencyRecord record = frequencyCache.get(fingerprint, key -> {
            log.debug("创建新的频率记录 - fingerprint={}", fingerprint);
            return new FrequencyRecord(System.currentTimeMillis(), new AtomicInteger(1));
        });

        // 增加计数
        int currentCount = record.getCount().incrementAndGet();

        // 判断是否超过频率限制
        if (currentCount > maxCount) {
            totalFiltered++;
            log.debug("频率限制触发 - fingerprint={}, count={}/{}, age={}ms",
                    fingerprint,
                    currentCount,
                    maxCount,
                    System.currentTimeMillis() - record.getFirstSeenTime());
            return true;
        }

        return false;
    }

    @Override
    public String getRuleName() {
        return "FrequencyLimitRule";
    }

    @Override
    public String getReason() {
        return String.format("超过频率限制（%d分钟内超过%d次）",
                properties.getFrequencyLimit().getWindowMinutes(),
                properties.getFrequencyLimit().getMaxCount());
    }

    @Override
    public int getPriority() {
        return 10; // 高优先级，尽早过滤异常风暴
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled() && properties.getFrequencyLimit().isEnabled();
    }

    /**
     * 获取统计信息
     */
    public FrequencyLimitStats getStats() {
        return FrequencyLimitStats.builder()
                .totalChecked(totalChecked)
                .totalFiltered(totalFiltered)
                .filterRate(totalChecked > 0 ? (double) totalFiltered / totalChecked : 0.0)
                .cacheSize(frequencyCache.estimatedSize())
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
     * 频率记录
     */
    @Data
    @AllArgsConstructor
    private static class FrequencyRecord {
        private long firstSeenTime;
        private AtomicInteger count;
    }

    /**
     * 统计信息
     */
    @Data
    @lombok.Builder
    public static class FrequencyLimitStats {
        private long totalChecked;
        private long totalFiltered;
        private double filterRate;
        private long cacheSize;
    }
}
