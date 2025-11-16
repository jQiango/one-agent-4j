package com.all.in.one.agent.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 飞书通知响应模型
 *
 * @author One Agent 4J
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeishuNotificationResponse {

    /**
     * 是否发送成功
     */
    private Boolean success;

    /**
     * 飞书 API 响应码
     */
    private Integer code;

    /**
     * 飞书 API 响应消息
     */
    private String message;

    /**
     * 飞书消息 ID
     */
    private String messageId;

    /**
     * 发送时间戳
     */
    private Long timestamp;

    /**
     * 创建成功响应
     */
    public static FeishuNotificationResponse success(String messageId) {
        return FeishuNotificationResponse.builder()
                .success(true)
                .code(0)
                .message("发送成功")
                .messageId(messageId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建失败响应
     */
    public static FeishuNotificationResponse failure(String message) {
        return FeishuNotificationResponse.builder()
                .success(false)
                .code(-1)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
