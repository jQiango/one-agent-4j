package com.all.in.one.agent.notification.model;

import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import com.all.in.one.agent.dao.entity.AppAlarmTicket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 飞书通知请求模型
 *
 * @author One Agent 4J
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeishuNotificationRequest {

    /**
     * 告警工单信息
     */
    private AppAlarmTicket ticket;

    /**
     * 告警记录详情
     */
    private AppAlarmRecord alarmRecord;

    /**
     * 卡片类型：standard（标准）、simplified（简化）、summary（汇总）
     */
    private String cardType;

    /**
     * 是否 @ 所有人
     */
    private Boolean atAll;

    /**
     * @ 用户的 OpenID 列表（多个用户用逗号分隔）
     */
    private String atUserIds;

    /**
     * 通知渠道优先级：P0/P1/P2/P3/P4
     */
    private String priority;
}
