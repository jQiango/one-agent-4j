package com.all.in.one.agent.service;

import com.all.in.one.agent.ai.model.DenoiseDecision;
import com.all.in.one.agent.dao.entity.ExceptionRecord;
import com.all.in.one.agent.dao.entity.Ticket;
import com.all.in.one.agent.dao.mapper.TicketMapper;
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

    private final TicketMapper ticketMapper;
    private static final AtomicLong ticketSequence = new AtomicLong(0);
    private static final DateTimeFormatter TICKET_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public TicketGenerationService(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
        log.info("TicketGenerationService 初始化完成");
    }

    /**
     * 根据异常记录生成工单
     *
     * @param exceptionRecord 异常记录
     * @return 工单ID，如果工单已存在则返回null
     */
    public Long generateTicket(ExceptionRecord exceptionRecord) {
        return generateTicket(exceptionRecord, null);
    }

    /**
     * 根据异常记录生成工单（支持 AI 建议）
     *
     * @param exceptionRecord 异常记录
     * @param aiDecision AI 去噪判断结果（可选）
     * @return 工单ID，如果工单已存在则返回null
     */
    public Long generateTicket(ExceptionRecord exceptionRecord, DenoiseDecision aiDecision) {
        try {
            // 检查该异常指纹是否已经有未关闭的工单
            LambdaQueryWrapper<Ticket> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Ticket::getExceptionFingerprint, exceptionRecord.getFingerprint())
                    .notIn(Ticket::getStatus, "CLOSED")
                    .orderByDesc(Ticket::getCreatedAt)
                    .last("LIMIT 1");

            Ticket existingTicket = ticketMapper.selectOne(queryWrapper);

            if (existingTicket != null) {
                // 工单已存在，更新发生次数和最后发生时间
                existingTicket.setOccurrenceCount(existingTicket.getOccurrenceCount() + 1);
                existingTicket.setLastOccurredAt(exceptionRecord.getOccurredAt());
                ticketMapper.updateById(existingTicket);
                log.info("更新已有工单 - ticketNo={}, occurrenceCount={}",
                        existingTicket.getTicketNo(), existingTicket.getOccurrenceCount());
                return existingTicket.getId();
            }

            // 创建新工单（使用 AI 建议）
            Ticket ticket = buildTicket(exceptionRecord, aiDecision);
            ticketMapper.insert(ticket);
            log.info("新工单已生成 - ticketNo={}, exceptionType={}, severity={}, aiSuggested={}",
                    ticket.getTicketNo(), ticket.getExceptionType(), ticket.getSeverity(),
                    aiDecision != null ? aiDecision.getSuggestedSeverity() : "N/A");
            return ticket.getId();

        } catch (Exception e) {
            log.error("生成工单失败 - fingerprint={}, error={}",
                    exceptionRecord.getFingerprint(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建工单对象
     */
    private Ticket buildTicket(ExceptionRecord exceptionRecord, DenoiseDecision aiDecision) {
        Ticket ticket = new Ticket();

        // 工单编号
        ticket.setTicketNo(generateTicketNo());

        // 关联异常
        ticket.setExceptionRecordId(exceptionRecord.getId());
        ticket.setExceptionFingerprint(exceptionRecord.getFingerprint());

        // 服务信息
        ticket.setServiceName(exceptionRecord.getAppName());
        ticket.setEnvironment(exceptionRecord.getEnvironment());

        // 问题信息
        ticket.setTitle(generateTitle(exceptionRecord));
        ticket.setProblemType(determineProblemType(exceptionRecord));
        ticket.setProblemCategory("EXCEPTION");

        // 使用 AI 建议的严重级别，如果没有则使用自动计算的
        String severity = (aiDecision != null && aiDecision.getSuggestedSeverity() != null)
                ? aiDecision.getSuggestedSeverity()
                : calculateSeverity(exceptionRecord);
        ticket.setSeverity(severity);

        // 异常内容
        ticket.setExceptionType(exceptionRecord.getExceptionType());
        ticket.setExceptionMessage(exceptionRecord.getExceptionMessage());
        ticket.setStackTrace(exceptionRecord.getStackTrace());
        ticket.setErrorLocation(exceptionRecord.getErrorLocation());
        ticket.setOccurrenceCount(1);
        ticket.setFirstOccurredAt(exceptionRecord.getOccurredAt());
        ticket.setLastOccurredAt(exceptionRecord.getOccurredAt());

        // 责任人（后续可以根据服务配置自动分配）
        ticket.setServiceOwner(null);
        ticket.setAssignee(null);
        ticket.setReporter("AI-Agent");

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
    private String generateTitle(ExceptionRecord exceptionRecord) {
        String exceptionType = exceptionRecord.getExceptionType();
        String location = exceptionRecord.getErrorLocation();
        if (location != null && location.length() > 50) {
            location = location.substring(0, 50) + "...";
        }
        return String.format("[%s] %s 异常", exceptionType, location != null ? location : "未知位置");
    }

    /**
     * 判断问题类型
     */
    private String determineProblemType(ExceptionRecord exceptionRecord) {
        String exceptionType = exceptionRecord.getExceptionType();
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
    private String calculateSeverity(ExceptionRecord exceptionRecord) {
        String exceptionType = exceptionRecord.getExceptionType();
        String environment = exceptionRecord.getEnvironment();

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
