package com.all.in.one.agent.notification.manager;

import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import com.all.in.one.agent.dao.entity.AppAlarmTicket;
import com.all.in.one.agent.notification.config.FeishuNotificationProperties;
import com.all.in.one.agent.notification.model.FeishuNotificationRequest;
import com.all.in.one.agent.notification.model.FeishuNotificationResponse;
import com.all.in.one.agent.notification.service.FeishuNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通知管理器
 * <p>
 * 负责根据告警优先级和配置，自动选择合适的通知渠道和卡片类型。
 * <p>
 * 通知策略：
 * <ul>
 *     <li>P0: 飞书标准卡片 + @所有人 + 短信（可选）</li>
 *     <li>P1: 飞书标准卡片 + @责任人 + 邮件（可选）</li>
 *     <li>P2: 飞书标准卡片（不@用户）</li>
 *     <li>P3: 飞书简化卡片（不@用户）</li>
 *     <li>P4: 加入每日汇总（飞书汇总卡片）</li>
 * </ul>
 *
 * @author One Agent 4J
 */
@Slf4j
@Component
public class NotificationManager {

    private final FeishuNotificationService feishuNotificationService;
    private final FeishuNotificationProperties feishuProperties;

    public NotificationManager(
            FeishuNotificationService feishuNotificationService,
            FeishuNotificationProperties feishuProperties) {
        this.feishuNotificationService = feishuNotificationService;
        this.feishuProperties = feishuProperties;
        log.info("通知管理器初始化完成，飞书通知启用状态: {}", feishuProperties.getEnabled());
    }

    /**
     * 发送告警通知
     * <p>
     * 根据工单的严重级别自动选择通知策略和卡片类型。
     *
     * @param ticket      告警工单
     * @param alarmRecord 告警记录
     */
    public void sendAlarmNotification(AppAlarmTicket ticket, AppAlarmRecord alarmRecord) {
        if (!feishuProperties.getEnabled()) {
            log.debug("飞书通知未启用，跳过通知发送");
            return;
        }

        try {
            // 构建通知请求
            FeishuNotificationRequest request = buildNotificationRequest(ticket, alarmRecord);

            // 发送通知
            FeishuNotificationResponse response = feishuNotificationService.sendAlarmNotification(request);

            // 记录结果
            if (response.getSuccess()) {
                log.info("飞书通知发送成功，工单ID: {}, 消息ID: {}", ticket.getId(), response.getMessageId());
            } else {
                log.error("飞书通知发送失败，工单ID: {}, 错误: {}", ticket.getId(), response.getMessage());
            }

        } catch (Exception e) {
            log.error("发送飞书通知时发生异常，工单ID: {}", ticket.getId(), e);
        }
    }

    /**
     * 构建通知请求
     */
    private FeishuNotificationRequest buildNotificationRequest(AppAlarmTicket ticket, AppAlarmRecord alarmRecord) {
        String severity = ticket.getSeverity();
        String cardType;
        Boolean atAll = false;
        String atUserIds = null;

        // 根据优先级确定卡片类型和@策略
        switch (severity) {
            case "P0":
                cardType = "standard";
                atAll = true;  // P0 @所有人
                break;
            case "P1":
                cardType = "standard";
                atUserIds = feishuProperties.getAtUserIds();  // P1 @责任人
                break;
            case "P2":
                cardType = "standard";
                // P2 不@用户
                break;
            case "P3":
            case "P4":
                cardType = "simplified";
                // P3/P4 使用简化卡片，不@用户
                break;
            default:
                cardType = "standard";
                break;
        }

        return FeishuNotificationRequest.builder()
                .ticket(ticket)
                .alarmRecord(alarmRecord)
                .cardType(cardType)
                .atAll(atAll)
                .atUserIds(atUserIds)
                .priority(severity)
                .build();
    }

    /**
     * 测试飞书连接
     */
    public boolean testFeishuConnection() {
        if (!feishuProperties.getEnabled()) {
            log.warn("飞书通知未启用，无法测试连接");
            return false;
        }
        return feishuNotificationService.testConnection();
    }
}
