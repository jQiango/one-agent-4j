package com.all.in.one.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警责任人配置实体
 * <p>
 * 用于配置每个服务的主责任人和备份责任人，支持自动工单分派。
 * </p>
 *
 * @author One Agent 4J
 */
@Data
@TableName("alarm_owner_config")
public class AlarmOwnerConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 服务名称（唯一）
     * <p>
     * 示例：payment-service, order-service
     * </p>
     */
    private String serviceName;

    /**
     * 服务描述
     */
    private String serviceDescription;

    /**
     * 主责任人工号
     * <p>
     * 通常是邮箱前缀或员工工号
     * </p>
     */
    private String ownerId;

    /**
     * 主责任人姓名
     */
    private String ownerName;

    /**
     * 主责任人邮箱
     */
    private String ownerEmail;

    /**
     * 主责任人手机号
     * <p>
     * 用于紧急联系（如P0告警）
     * </p>
     */
    private String ownerMobile;

    /**
     * 备份责任人工号
     * <p>
     * 主责任人不在时的接手人
     * </p>
     */
    private String backupOwnerId;

    /**
     * 备份责任人姓名
     */
    private String backupOwnerName;

    /**
     * 备份责任人邮箱
     */
    private String backupOwnerEmail;

    /**
     * 所属团队
     */
    private String teamName;

    /**
     * 团队负责人
     */
    private String teamLeader;

    /**
     * 飞书用户 OpenID
     * <p>
     * 用于飞书通知时@提醒责任人
     * </p>
     */
    private String feishuOpenId;

    /**
     * 备份责任人飞书 OpenID
     */
    private String backupFeishuOpenId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 配置创建人
     */
    private String createdBy;
}
