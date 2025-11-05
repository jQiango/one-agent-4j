package com.all.in.one.agent.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 第 2 层：AI 智能去噪配置
 * <p>
 * 使用大模型进行深度分析，判断异常是否需要告警
 * </p>
 *
 * @author One Agent 4J
 */
@Data
@Component
@ConfigurationProperties(prefix = "one-agent.ai-denoise")
public class AiDenoiseProperties {

    /**
     * 是否启用 AI 去噪（默认 false）
     */
    private boolean enabled = false;

    /**
     * 历史回看时间窗口（分钟）
     * 查询最近 N 分钟的异常作为上下文
     * 默认: 2 分钟
     */
    private int lookbackMinutes = 2;

    /**
     * 最大历史记录数
     * 避免上下文过长导致 token 过多
     * 默认: 20
     */
    private int maxHistoryRecords = 20;

    /**
     * 是否启用结果缓存（默认 true）
     * 相同指纹的 AI 决策结果缓存一段时间，避免重复调用
     */
    private boolean cacheEnabled = true;

    /**
     * 缓存过期时间（分钟）
     * 默认: 5 分钟
     */
    private int cacheTtlMinutes = 5;

    /**
     * 缓存最大数量
     * 防止内存溢出
     * 默认: 10000
     */
    private int maxCacheSize = 10000;
}
