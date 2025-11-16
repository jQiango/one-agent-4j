package com.all.in.one.agent.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书通知配置属性
 *
 * @author One Agent 4J
 */
@Data
@Component
@ConfigurationProperties(prefix = "one-agent.notification.feishu")
public class FeishuNotificationProperties {

    /**
     * 是否启用飞书通知
     */
    private Boolean enabled = false;

    /**
     * 飞书机器人 Webhook 地址（必填）
     * 格式：https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxxxx
     */
    private String webhook;

    /**
     * 签名密钥（可选，启用签名校验时必填）
     */
    private String secret;

    /**
     * @ 用户的 OpenID 列表（多个用逗号分隔）
     * 例如：ou_xxx,ou_yyy
     */
    private String atUserIds;

    /**
     * 是否 @ 所有人（慎用，仅用于 P0 告警）
     */
    private Boolean atAll = false;

    /**
     * 同一告警最小间隔（秒）- 避免消息刷屏
     */
    private Integer minIntervalSeconds = 60;

    /**
     * 每小时最大消息数
     */
    private Integer maxMessagesPerHour = 50;

    /**
     * 连接超时时间（毫秒）
     */
    private Integer connectTimeout = 5000;

    /**
     * 读取超时时间（毫秒）
     */
    private Integer readTimeout = 10000;

    /**
     * 是否启用重试机制
     */
    private Boolean retryEnabled = true;

    /**
     * 最大重试次数
     */
    private Integer maxRetries = 3;
}
