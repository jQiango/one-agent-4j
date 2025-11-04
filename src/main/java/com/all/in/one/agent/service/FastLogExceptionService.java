package com.all.in.one.agent.service;

import com.all.in.one.agent.ai.model.DenoiseDecision;
import com.all.in.one.agent.ai.service.AiDenoiseService;
import com.all.in.one.agent.api.model.FastLogExceptionRequest;
import com.all.in.one.agent.api.model.FastLogExceptionResponse;
import com.all.in.one.agent.common.config.AgentProperties;
import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.dao.entity.ExceptionRecord;
import com.all.in.one.agent.dao.mapper.ExceptionRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * FAST 日志平台异常处理服务
 * <p>
 * 接收 FAST 平台上报的异常，进行 AI 去噪和工单生成
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Service
public class FastLogExceptionService {

    private final AgentProperties properties;
    private final ExceptionPersistenceService persistenceService;
    private final TicketGenerationService ticketGenerationService;
    private final ExceptionRecordMapper exceptionRecordMapper;
    private final com.all.in.one.agent.dao.mapper.TicketMapper ticketMapper;

    @Autowired(required = false)
    private AiDenoiseService aiDenoiseService;

    public FastLogExceptionService(AgentProperties properties,
                                     ExceptionPersistenceService persistenceService,
                                     TicketGenerationService ticketGenerationService,
                                     ExceptionRecordMapper exceptionRecordMapper,
                                     com.all.in.one.agent.dao.mapper.TicketMapper ticketMapper) {
        this.properties = properties;
        this.persistenceService = persistenceService;
        this.ticketGenerationService = ticketGenerationService;
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.ticketMapper = ticketMapper;
        log.info("FastLogExceptionService 初始化完成 - aiDenoiseEnabled={}",
                aiDenoiseService != null);
    }

    /**
     * 处理 FAST 日志平台上报的异常
     *
     * @param request FAST 异常请求
     * @return 处理响应
     */
    public FastLogExceptionResponse processException(FastLogExceptionRequest request) {
        try {
            log.info("接收 FAST 日志平台异常 - appName={}, exceptionType={}, environment={}",
                    request.getAppName(), request.getExceptionType(), request.getEnvironment());

            // 1. 转换为 ExceptionInfo
            ExceptionInfo exceptionInfo = convertToExceptionInfo(request);

            // 2. AI 去噪判断（如果启用）
            DenoiseDecision denoiseDecision = null;
            FastLogExceptionResponse.AiDecisionInfo aiDecisionInfo = null;

            if (aiDenoiseService != null) {
                denoiseDecision = aiDenoiseService.shouldAlert(exceptionInfo);
                aiDecisionInfo = buildAiDecisionInfo(denoiseDecision);

                log.info("FAST 异常 AI 去噪判断 - fingerprint={}, shouldAlert={}, reason={}",
                        exceptionInfo.getFingerprint(),
                        denoiseDecision.isShouldAlert(),
                        denoiseDecision.getReason());

                // 如果 AI 判断不需要报警，则返回过滤结果
                if (!denoiseDecision.isShouldAlert()) {
                    return FastLogExceptionResponse.filtered(
                            exceptionInfo.getFingerprint(),
                            aiDecisionInfo
                    );
                }
            }

            // 3. 持久化异常记录
            Long exceptionRecordId = persistenceService.saveException(exceptionInfo);
            if (exceptionRecordId == null) {
                log.error("FAST 异常记录保存失败 - fingerprint={}", exceptionInfo.getFingerprint());
                return FastLogExceptionResponse.error(500, "异常记录保存失败");
            }

            // 4. 生成工单（如果启用）
            String ticketNo = null;
            Long ticketId = null;

            if (properties.getStorageStrategy().isEnableTicketGeneration()) {
                ExceptionRecord exceptionRecord = exceptionRecordMapper.selectById(exceptionRecordId);
                if (exceptionRecord != null) {
                    ticketId = ticketGenerationService.generateTicket(exceptionRecord, denoiseDecision);
                    if (ticketId != null) {
                        // 查询工单获取工单编号
                        var ticket = ticketMapper.selectById(ticketId);
                        if (ticket != null) {
                            ticketNo = ticket.getTicketNo();
                        }
                    }
                }
            }

            // 5. 返回成功响应
            return FastLogExceptionResponse.success(
                    exceptionInfo.getFingerprint(),
                    exceptionRecordId,
                    ticketNo,
                    ticketId,
                    aiDecisionInfo
            );

        } catch (Exception e) {
            log.error("处理 FAST 日志平台异常失败", e);
            return FastLogExceptionResponse.error(500, "系统内部错误: " + e.getMessage());
        }
    }

    /**
     * 转换 FAST 请求为 ExceptionInfo
     */
    private ExceptionInfo convertToExceptionInfo(FastLogExceptionRequest request) {
        ExceptionInfo info = new ExceptionInfo();

        // 应用信息
        info.setAppName(request.getAppName());
        info.setEnvironment(request.getEnvironment());
        info.setInstanceId(request.getInstanceId());
        info.setHostname(request.getHostname());
        info.setIp(request.getIp());

        // 异常信息
        info.setExceptionType(request.getExceptionType());
        info.setExceptionMessage(request.getExceptionMessage());
        info.setStackTrace(request.getStackTrace());

        // 错误位置
        info.setErrorClass(request.getErrorClass());
        info.setErrorMethod(request.getErrorMethod());
        info.setErrorLine(request.getErrorLine());
        info.setErrorLocation(request.getErrorLocation());

        // 线程信息
        ExceptionInfo.ThreadInfo threadInfo = new ExceptionInfo.ThreadInfo();
        threadInfo.setThreadId(request.getThreadId());
        threadInfo.setThreadName(request.getThreadName());
        info.setThreadInfo(threadInfo);

        // 请求信息
        if (request.getRequestUri() != null) {
            ExceptionInfo.RequestInfo requestInfo = new ExceptionInfo.RequestInfo();
            requestInfo.setMethod(request.getRequestMethod());
            requestInfo.setUri(request.getRequestUri());
            requestInfo.setParams(request.getRequestParams());
            requestInfo.setClientIp(request.getClientIp());
            info.setRequestInfo(requestInfo);
        }

        // 链路追踪
        ExceptionInfo.TraceInfo traceInfo = new ExceptionInfo.TraceInfo();
        traceInfo.setTraceId(request.getTraceId());
        traceInfo.setSpanId(request.getSpanId());
        info.setTraceInfo(traceInfo);

        // 时间信息
        if (request.getOccurredAt() != null) {
            info.setOccurredAt(Instant.ofEpochMilli(request.getOccurredAt()));
        } else {
            info.setOccurredAt(Instant.now());
        }
        info.setReportedAt(Instant.now());

        // 生成指纹
        info.generateFingerprint();

        return info;
    }

    /**
     * 构建 AI 决策信息
     */
    private FastLogExceptionResponse.AiDecisionInfo buildAiDecisionInfo(DenoiseDecision decision) {
        if (decision == null) {
            return null;
        }

        return FastLogExceptionResponse.AiDecisionInfo.builder()
                .shouldAlert(decision.isShouldAlert())
                .isDuplicate(decision.isDuplicate())
                .similarityScore(decision.getSimilarityScore())
                .suggestedSeverity(decision.getSuggestedSeverity())
                .reason(decision.getReason())
                .suggestion(decision.getSuggestion())
                .build();
    }

}
