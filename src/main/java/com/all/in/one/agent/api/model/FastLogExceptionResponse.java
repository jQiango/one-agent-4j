package com.all.in.one.agent.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FAST 日志平台异常上报响应
 *
 * @author One Agent 4J
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FastLogExceptionResponse {

    /**
     * 响应码: 200=成功, 400=参数错误, 500=系统错误
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 异常指纹
     */
    private String fingerprint;

    /**
     * 异常记录 ID
     */
    private Long exceptionRecordId;

    /**
     * 工单编号（如果生成了工单）
     */
    private String ticketNo;

    /**
     * 工单 ID（如果生成了工单）
     */
    private Long ticketId;

    /**
     * AI 判断结果
     */
    private AiDecisionInfo aiDecision;

    /**
     * 处理时间戳
     */
    private Long timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiDecisionInfo {
        /**
         * 是否需要报警
         */
        private Boolean shouldAlert;

        /**
         * 是否重复异常
         */
        private Boolean isDuplicate;

        /**
         * 相似度
         */
        private Double similarityScore;

        /**
         * 建议严重级别
         */
        private String suggestedSeverity;

        /**
         * 判断原因
         */
        private String reason;

        /**
         * 建议
         */
        private String suggestion;
    }

    public static FastLogExceptionResponse success(String fingerprint, Long exceptionRecordId,
                                                     String ticketNo, Long ticketId,
                                                     AiDecisionInfo aiDecision) {
        return FastLogExceptionResponse.builder()
                .code(200)
                .message("异常处理成功")
                .fingerprint(fingerprint)
                .exceptionRecordId(exceptionRecordId)
                .ticketNo(ticketNo)
                .ticketId(ticketId)
                .aiDecision(aiDecision)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static FastLogExceptionResponse filtered(String fingerprint, AiDecisionInfo aiDecision) {
        return FastLogExceptionResponse.builder()
                .code(200)
                .message("异常已被 AI 过滤，不生成工单")
                .fingerprint(fingerprint)
                .aiDecision(aiDecision)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static FastLogExceptionResponse error(Integer code, String message) {
        return FastLogExceptionResponse.builder()
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
