package com.all.in.one.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单实体
 *
 * @author One Agent 4J
 */
@Data
@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 工单编号
    private String ticketNo;

    // 关联异常
    private Long exceptionRecordId;
    private String exceptionFingerprint;

    // 服务信息
    private String serviceName;
    private String environment;

    // 问题信息
    private String title;
    private String problemType;
    private String problemCategory;
    private String severity;

    // 异常内容
    private String exceptionType;
    private String exceptionMessage;
    private String stackTrace;
    private String errorLocation;
    private Integer occurrenceCount;
    private LocalDateTime firstOccurredAt;
    private LocalDateTime lastOccurredAt;

    // 责任人
    private String serviceOwner;
    private String assignee;
    private String reporter;

    // 处理状态
    private String status;
    private Integer progress;

    // 处理时间
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    // 处理方案
    private String solution;
    private String solutionType;
    private String rootCause;

    // SLA
    private LocalDateTime expectedResolveTime;
    private Integer actualResolveDuration;
    private Boolean slaBreached;

    // 备注
    private String remark;

    // 审计字段
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
