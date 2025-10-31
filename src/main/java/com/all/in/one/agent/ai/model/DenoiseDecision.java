package com.all.in.one.agent.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 去噪判断结果
 *
 * @author One Agent 4J
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DenoiseDecision {

    /**
     * 是否应该报警/持久化
     */
    private boolean shouldAlert;

    /**
     * 是否是重复异常
     */
    private boolean isDuplicate;

    /**
     * 相似度分数 (0.0-1.0)
     */
    private double similarityScore;

    /**
     * 建议的严重级别
     */
    private String suggestedSeverity;

    /**
     * 判断原因/解释
     */
    private String reason;

    /**
     * 相关的历史异常ID列表
     */
    private java.util.List<Long> relatedExceptionIds;

    /**
     * AI 的额外建议
     */
    private String suggestion;
}
