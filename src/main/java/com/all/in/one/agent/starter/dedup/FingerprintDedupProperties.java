package com.all.in.one.agent.starter.dedup;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 第 1 层：指纹去重配置
 * <p>
 * 时间窗口内相同指纹的异常只处理一次，这是最核心的降噪层
 * </p>
 *
 * @author One Agent 4J
 */
@Data
@Component
@ConfigurationProperties(prefix = "one-agent.dedup")
public class FingerprintDedupProperties {

    /**
     * 是否启用指纹去重（默认 true）
     */
    private boolean enabled = true;

    /**
     * 时间窗口（分钟）
     * 在此时间窗口内，相同指纹的异常只处理一次
     * 默认: 2 分钟
     */
    private int timeWindowMinutes = 2;

    /**
     * 缓存最大数量
     * 防止内存溢出
     * 默认: 10000
     */
    private int maxCacheSize = 10000;

    /**
     * 存储方式
     * memory: 内存缓存（单机）
     * redis: Redis（多实例共享，待实现）
     * 默认: memory
     */
    private String storage = "memory";
}
