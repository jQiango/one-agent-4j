package com.all.in.one.agent.service;

import com.all.in.one.agent.ai.model.DenoiseDecision;
import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import com.all.in.one.agent.dao.entity.AppAlarmTicket;
import com.all.in.one.agent.dao.mapper.AppAlarmTicketMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工单生成服务
 *
 * @author One Agent 4J
 */
@Slf4j
@Service
public class TicketGenerationService {

    private final AppAlarmTicketMapper appAlarmTicketMapper;
    private final ResponsibleOwnerService ownerService;
    private final com.all.in.one.agent.notification.manager.NotificationManager notificationManager;
    private static final AtomicLong ticketSequence = new AtomicLong(0);
    private static final DateTimeFormatter TICKET_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public TicketGenerationService(
            AppAlarmTicketMapper appAlarmTicketMapper,
            ResponsibleOwnerService ownerService,
            com.all.in.one.agent.notification.manager.NotificationManager notificationManager) {
        this.appAlarmTicketMapper = appAlarmTicketMapper;
        this.ownerService = ownerService;
        this.notificationManager = notificationManager;
        log.info("TicketGenerationService 初始化完成");
    }

    /**
     * 根据告警记录生成工单
     *
     * @param appAlarmRecord 告警记录
     * @return 工单ID，如果工单已存在则返回null
     */
    public Long generateTicket(AppAlarmRecord appAlarmRecord) {
        return generateTicket(appAlarmRecord, null);
    }

    /**
     * 根据告警记录生成工单（支持 AI 建议）
     *
     * @param appAlarmRecord 告警记录
     * @param aiDecision AI 去噪判断结果（可选）
     * @return 工单ID，如果工单已存在则返回null
     */
    public Long generateTicket(AppAlarmRecord appAlarmRecord, DenoiseDecision aiDecision) {
        try {
            // 检查该异常指纹是否已经有未关闭的工单
            LambdaQueryWrapper<AppAlarmTicket> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(AppAlarmTicket::getExceptionFingerprint, appAlarmRecord.getFingerprint())
                    .notIn(AppAlarmTicket::getStatus, "CLOSED")
                    .orderByDesc(AppAlarmTicket::getCreatedAt)
                    .last("LIMIT 1");

            AppAlarmTicket existingTicket = appAlarmTicketMapper.selectOne(queryWrapper);

            if (existingTicket != null) {
                // 工单已存在，更新发生次数和最后发生时间
                existingTicket.setOccurrenceCount(existingTicket.getOccurrenceCount() + 1);
                existingTicket.setLastOccurredAt(appAlarmRecord.getOccurredAt());
                appAlarmTicketMapper.updateById(existingTicket);
                log.info("更新已有工单 - ticketNo={}, occurrenceCount={}",
                        existingTicket.getTicketNo(), existingTicket.getOccurrenceCount());
                return existingTicket.getId();
            }

            // 创建新工单（使用 AI 建议）
            AppAlarmTicket ticket = buildTicket(appAlarmRecord, aiDecision);
            appAlarmTicketMapper.insert(ticket);
            log.info("新工单已生成并自动分派 - ticketNo={}, exceptionType={}, severity={}, assignee={}, aiSuggested={}",
                    ticket.getTicketNo(), ticket.getExceptionType(), ticket.getSeverity(),
                    ticket.getAssignee(),
                    aiDecision != null ? aiDecision.getSuggestedSeverity() : "N/A");

            // 发送飞书通知
            try {
                notificationManager.sendAlarmNotification(ticket, appAlarmRecord);
                log.debug("飞书通知已发送 - ticketNo={}", ticket.getTicketNo());
            } catch (Exception notificationException) {
                // 通知失败不影响工单生成
                log.error("发送飞书通知失败 - ticketNo={}, 错误: {}",
                        ticket.getTicketNo(), notificationException.getMessage());
            }

            return ticket.getId();

        } catch (Exception e) {
            log.error("生成工单失败 - fingerprint={}, error={}",
                    appAlarmRecord.getFingerprint(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建工单对象
     */
    private AppAlarmTicket buildTicket(AppAlarmRecord appAlarmRecord, DenoiseDecision aiDecision) {
        AppAlarmTicket ticket = new AppAlarmTicket();

        // 工单编号
        ticket.setTicketNo(generateTicketNo());

        // 关联异常
        ticket.setExceptionRecordId(appAlarmRecord.getId());
        ticket.setExceptionFingerprint(appAlarmRecord.getFingerprint());

        // 服务信息
        ticket.setServiceName(appAlarmRecord.getAppName());
        ticket.setEnvironment(appAlarmRecord.getEnvironment());

        // 问题信息
        ticket.setTitle(generateTitle(appAlarmRecord));
        ticket.setProblemType(determineProblemType(appAlarmRecord));
        ticket.setProblemCategory("EXCEPTION");

        // 使用 AI 建议的严重级别，如果没有则使用自动计算的
        String severity = (aiDecision != null && aiDecision.getSuggestedSeverity() != null)
                ? aiDecision.getSuggestedSeverity()
                : calculateSeverity(appAlarmRecord);
        ticket.setSeverity(severity);

        // 异常内容
        ticket.setExceptionType(appAlarmRecord.getExceptionType());
        ticket.setExceptionMessage(appAlarmRecord.getExceptionMessage());
        ticket.setStackTrace(appAlarmRecord.getStackTrace());
        ticket.setErrorLocation(appAlarmRecord.getErrorLocation());
        ticket.setOccurrenceCount(1);
        ticket.setFirstOccurredAt(appAlarmRecord.getOccurredAt());
        ticket.setLastOccurredAt(appAlarmRecord.getOccurredAt());

        // 自动分派责任人
        String serviceName = appAlarmRecord.getAppName();
        String owner = ownerService.findResponsibleOwner(serviceName);
        ticket.setServiceOwner(owner);
        ticket.setAssignee(owner);
        ticket.setReporter("AI-Agent");

        log.debug("工单自动分派 - 服务: {}, 责任人: {}", serviceName, owner);

        // 处理状态
        ticket.setStatus("PENDING");
        ticket.setProgress(0);

        // SLA（根据严重级别计算期望解决时间）
        ticket.setExpectedResolveTime(calculateExpectedResolveTime(severity));
        ticket.setSlaBreached(false);

        // 备注（添加 AI 建议）
        if (aiDecision != null && aiDecision.getSuggestion() != null) {
            ticket.setRemark("AI 建议: " + aiDecision.getSuggestion());
        }

        // 审计字段
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());

        return ticket;
    }

    /**
     * 生成工单编号: TK + 时间戳 + 序列号
     */
    private String generateTicketNo() {
        String timestamp = LocalDateTime.now().format(TICKET_NO_FORMATTER);
        long sequence = ticketSequence.incrementAndGet() % 10000;
        return String.format("TK%s%04d", timestamp, sequence);
    }

    /**
     * 生成工单标题
     */
    private String generateTitle(AppAlarmRecord appAlarmRecord) {
        String exceptionType = appAlarmRecord.getExceptionType();
        String location = appAlarmRecord.getErrorLocation();
        if (location != null && location.length() > 50) {
            location = location.substring(0, 50) + "...";
        }
        return String.format("[%s] %s 异常", exceptionType, location != null ? location : "未知位置");
    }

    /**
     * 判断问题类型
     */
    private String determineProblemType(AppAlarmRecord appAlarmRecord) {
        String exceptionType = appAlarmRecord.getExceptionType();
        if (exceptionType.contains("NullPointer")) {
            return "空指针异常";
        } else if (exceptionType.contains("SQLException") || exceptionType.contains("DataAccess")) {
            return "数据库异常";
        } else if (exceptionType.contains("Timeout") || exceptionType.contains("TimeoutException")) {
            return "超时异常";
        } else if (exceptionType.contains("IOException") || exceptionType.contains("Network")) {
            return "IO/网络异常";
        } else if (exceptionType.contains("OutOfMemory")) {
            return "内存溢出";
        } else if (exceptionType.contains("ClassCast") || exceptionType.contains("IllegalArgument")) {
            return "参数异常";
        } else {
            return "运行时异常";
        }
    }

    /**
     * 计算严重级别
     * P0: 致命 - 内存溢出、系统崩溃
     * P1: 严重 - 数据库异常、关键业务异常
     * P2: 一般 - 超时、网络异常
     * P3: 较低 - 空指针、参数异常
     * P4: 轻微 - 其他运行时异常
     */
    private String calculateSeverity(AppAlarmRecord appAlarmRecord) {
        String exceptionType = appAlarmRecord.getExceptionType();
        String environment = appAlarmRecord.getEnvironment();

        // 生产环境提升一个级别
        boolean isProduction = "prod".equalsIgnoreCase(environment);

        if (exceptionType.contains("OutOfMemory") || exceptionType.contains("StackOverflow")) {
            return "P0"; // 致命
        } else if (exceptionType.contains("SQLException") || exceptionType.contains("DataAccess")) {
            return isProduction ? "P0" : "P1"; // 数据库异常
        } else if (exceptionType.contains("Timeout")) {
            return isProduction ? "P1" : "P2"; // 超时
        } else if (exceptionType.contains("NullPointer") || exceptionType.contains("IllegalArgument")) {
            return isProduction ? "P2" : "P3"; // 空指针/参数异常
        } else {
            return isProduction ? "P3" : "P4"; // 其他异常
        }
    }

    /**
     * 基于 ExceptionInfo 计算严重程度（供规则引擎使用）
     */
    public String calculateSeverity(com.all.in.one.agent.common.model.ExceptionInfo exceptionInfo) {
        String exceptionType = exceptionInfo.getExceptionType();
        String environment = exceptionInfo.getEnvironment();

        // 生产环境提升一个级别
        boolean isProduction = "prod".equalsIgnoreCase(environment);

        if (exceptionType.contains("OutOfMemory") || exceptionType.contains("StackOverflow")) {
            return "P0"; // 致命
        } else if (exceptionType.contains("SQLException") || exceptionType.contains("DataAccess")) {
            return isProduction ? "P0" : "P1"; // 数据库异常
        } else if (exceptionType.contains("Timeout")) {
            return isProduction ? "P1" : "P2"; // 超时
        } else if (exceptionType.contains("NullPointer") || exceptionType.contains("IllegalArgument")) {
            return isProduction ? "P2" : "P3"; // 空指针/参数异常
        } else {
            return isProduction ? "P3" : "P4"; // 其他异常
        }
    }

    /**
     * 计算期望解决时间
     * P0: 30分钟
     * P1: 2小时
     * P2: 24小时
     * P3: 3天
     * P4: 7天
     */
    private LocalDateTime calculateExpectedResolveTime(String severity) {
        LocalDateTime now = LocalDateTime.now();
        return switch (severity) {
            case "P0" -> now.plusMinutes(30);
            case "P1" -> now.plusHours(2);
            case "P2" -> now.plusHours(24);
            case "P3" -> now.plusDays(3);
            case "P4" -> now.plusDays(7);
            default -> now.plusDays(1);
        };
    }
}
