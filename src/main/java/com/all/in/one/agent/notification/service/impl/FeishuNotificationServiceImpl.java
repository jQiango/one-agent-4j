package com.all.in.one.agent.notification.service.impl;

import com.all.in.one.agent.notification.model.FeishuNotificationRequest;
import com.all.in.one.agent.notification.model.FeishuNotificationResponse;
import com.all.in.one.agent.notification.service.FeishuNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 飞书通知服务空实现
 * <p>
 * 该实现为预留接口的空实现，后续可以通过以下方式接入实际的飞书通知功能：
 * <ul>
 *     <li>方式1：替换此实现类，注入外部 jar 包的实现</li>
 *     <li>方式2：通过 Spring 的 @Primary 注解覆盖此实现</li>
 *     <li>方式3：通过配置项 one-agent.notification.feishu.enabled 控制是否启用</li>
 * </ul>
 * <p>
 * 当配置项 one-agent.notification.feishu.enabled=false 时，此实现不会被加载。
 *
 * @author One Agent 4J
 */
@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "one-agent.notification.feishu",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class FeishuNotificationServiceImpl implements FeishuNotificationService {

    public FeishuNotificationServiceImpl() {
        log.info("飞书通知服务（空实现）已初始化，当前未启用实际功能");
        log.info("如需启用飞书通知，请设置 one-agent.notification.feishu.enabled=true 并提供实际实现");
    }

    @Override
    public FeishuNotificationResponse sendAlarmNotification(FeishuNotificationRequest request) {
        log.debug("飞书通知（空实现）- sendAlarmNotification 被调用，但未执行实际发送");
        log.debug("工单ID: {}, 优先级: {}, 卡片类型: {}",
                request.getTicket() != null ? request.getTicket().getId() : null,
                request.getPriority(),
                request.getCardType());

        // 返回模拟成功响应
        return FeishuNotificationResponse.builder()
                .success(true)
                .code(0)
                .message("飞书通知服务未启用（空实现）")
                .messageId("mock-" + System.currentTimeMillis())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Override
    public FeishuNotificationResponse sendStandardCard(FeishuNotificationRequest request) {
        log.debug("飞书通知（空实现）- sendStandardCard 被调用，但未执行实际发送");
        return sendAlarmNotification(request);
    }

    @Override
    public FeishuNotificationResponse sendSimplifiedCard(FeishuNotificationRequest request) {
        log.debug("飞书通知（空实现）- sendSimplifiedCard 被调用，但未执行实际发送");
        return sendAlarmNotification(request);
    }

    @Override
    public FeishuNotificationResponse sendSummaryCard(String reportData) {
        log.debug("飞书通知（空实现）- sendSummaryCard 被调用，但未执行实际发送");
        log.debug("汇总报告数据长度: {}", reportData != null ? reportData.length() : 0);

        return FeishuNotificationResponse.builder()
                .success(true)
                .code(0)
                .message("飞书通知服务未启用（空实现）")
                .messageId("mock-summary-" + System.currentTimeMillis())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Override
    public boolean testConnection() {
        log.info("飞书通知（空实现）- testConnection 被调用");
        log.warn("当前使用空实现，实际飞书连接测试未执行");
        return false;
    }
}
