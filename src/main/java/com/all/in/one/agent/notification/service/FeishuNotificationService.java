package com.all.in.one.agent.notification.service;

import com.all.in.one.agent.notification.model.FeishuNotificationRequest;
import com.all.in.one.agent.notification.model.FeishuNotificationResponse;

/**
 * 飞书通知服务接口
 * <p>
 * 该接口定义了飞书通知的核心功能，支持三种卡片类型：
 * <ul>
 *     <li>标准卡片（P0/P1/P2）：完整的告警信息展示</li>
 *     <li>简化卡片（P3/P4）：精简的告警信息展示</li>
 *     <li>汇总卡片（批量）：每日/每周汇总报告</li>
 * </ul>
 * <p>
 * 所有消息统一使用 Markdown 交互卡片格式（msg_type: "interactive"），
 * 文本字段使用 lark_md 格式支持富文本展示。
 *
 * @author One Agent 4J
 */
public interface FeishuNotificationService {

    /**
     * 发送告警通知到飞书
     * <p>
     * 根据告警优先级自动选择卡片类型：
     * <ul>
     *     <li>P0/P1/P2: 标准卡片，包含完整信息和多个操作按钮</li>
     *     <li>P3/P4: 简化卡片，只包含关键信息和单个按钮</li>
     * </ul>
     *
     * @param request 飞书通知请求对象
     * @return 飞书通知响应对象
     */
    FeishuNotificationResponse sendAlarmNotification(FeishuNotificationRequest request);

    /**
     * 发送标准告警卡片（P0/P1/P2）
     * <p>
     * 标准卡片包含：
     * <ul>
     *     <li>完整的告警信息（服务、环境、异常类型、错误位置、异常消息、发生时间）</li>
     *     <li>负责人 @ 提醒（可选）</li>
     *     <li>AI 建议（可选）</li>
     *     <li>多个操作按钮（查看详情、立即处理、忽略）</li>
     * </ul>
     *
     * @param request 飞书通知请求对象
     * @return 飞书通知响应对象
     */
    FeishuNotificationResponse sendStandardCard(FeishuNotificationRequest request);

    /**
     * 发送简化告警卡片（P3/P4）
     * <p>
     * 简化卡片包含：
     * <ul>
     *     <li>基础信息（服务、工单号）</li>
     *     <li>异常摘要（异常类型 + 错误位置）</li>
     *     <li>单个操作按钮（查看详情）</li>
     *     <li>不 @ 用户（避免打扰）</li>
     * </ul>
     *
     * @param request 飞书通知请求对象
     * @return 飞书通知响应对象
     */
    FeishuNotificationResponse sendSimplifiedCard(FeishuNotificationRequest request);

    /**
     * 发送汇总报告卡片（批量）
     * <p>
     * 汇总卡片包含：
     * <ul>
     *     <li>总体统计（总告警数、已处理数、处理率）</li>
     *     <li>高频异常 TOP5</li>
     *     <li>处理排行榜</li>
     *     <li>查看详细报告按钮</li>
     * </ul>
     * <p>
     * 使用 Markdown 格式化展示，使用 hr 分隔符区分不同区域。
     *
     * @param reportData 汇总报告数据（JSON格式）
     * @return 飞书通知响应对象
     */
    FeishuNotificationResponse sendSummaryCard(String reportData);

    /**
     * 测试飞书连接
     * <p>
     * 发送一条测试消息到飞书群聊，验证配置是否正确。
     *
     * @return 是否连接成功
     */
    boolean testConnection();
}
