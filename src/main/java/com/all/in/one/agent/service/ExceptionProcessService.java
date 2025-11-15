package com.all.in.one.agent.service;

import com.all.in.one.agent.ai.model.DenoiseDecision;
import com.all.in.one.agent.ai.service.AiDenoiseService;
import com.all.in.one.agent.common.config.AgentProperties;
import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import com.all.in.one.agent.dao.mapper.AppAlarmRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 异常处理服务
 * <p>
 * 负责协调异常持久化和工单生成
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "one-agent.storage-strategy", name = "enable-local-persistence", havingValue = "true", matchIfMissing = true)
public class ExceptionProcessService {

    private final AgentProperties properties;
    private final ExceptionPersistenceService persistenceService;
    private final TicketGenerationService ticketGenerationService;
    private final AppAlarmRecordMapper appAlarmRecordMapper;

    @Autowired(required = false)
    private AiDenoiseService aiDenoiseService;

    public ExceptionProcessService(AgentProperties properties,
                                    ExceptionPersistenceService persistenceService,
                                    TicketGenerationService ticketGenerationService,
                                    AppAlarmRecordMapper appAlarmRecordMapper) {
        this.properties = properties;
        this.persistenceService = persistenceService;
        this.ticketGenerationService = ticketGenerationService;
        this.appAlarmRecordMapper = appAlarmRecordMapper;
        log.info("ExceptionProcessService 初始化完成 - enableLocalPersistence={}, enableTicketGeneration={}",
                properties.getStorageStrategy().isEnableLocalPersistence(),
                properties.getStorageStrategy().isEnableTicketGeneration());
    }

    /**
     * 处理异常信息
     * <p>
     * 1. AI 智能去噪判断（可选）
     * 2. 持久化异常记录（可选）
     * 3. 生成工单（可选）
     * </p>
     *
     * @param exceptionInfo 异常信息
     */
    public void processException(ExceptionInfo exceptionInfo) {
        try {
            // 检查是否启用本地持久化
            if (!properties.getStorageStrategy().isEnableLocalPersistence()) {
                log.debug("本地持久化已禁用，跳过异常记录保存 - fingerprint={}", exceptionInfo.getFingerprint());
                return;
            }

            // 0. AI 智能去噪判断（如果启用）
            DenoiseDecision denoiseDecision = null;
            if (aiDenoiseService != null) {
                denoiseDecision = aiDenoiseService.shouldAlert(exceptionInfo);
                log.info("AI 去噪判断结果 - shouldAlert={}, isDuplicate={}, reason={}",
                        denoiseDecision.isShouldAlert(), denoiseDecision.isDuplicate(), denoiseDecision.getReason());

                // 如果 AI 判断不需要报警，则跳过后续处理
                if (!denoiseDecision.isShouldAlert()) {
                    log.info("AI 判断此异常不需要报警，跳过持久化和工单生成 - fingerprint={}, reason={}",
                            exceptionInfo.getFingerprint(), denoiseDecision.getReason());
                    return;
                }
            }

            // 1. 持久化异常记录
            Long exceptionRecordId = persistenceService.saveException(exceptionInfo);
            if (exceptionRecordId == null) {
                log.error("异常记录保存失败，跳过工单生成 - fingerprint={}", exceptionInfo.getFingerprint());
                return;
            }

            // 检查是否启用工单生成
            if (!properties.getStorageStrategy().isEnableTicketGeneration()) {
                log.debug("工单生成已禁用，跳过工单生成 - fingerprint={}", exceptionInfo.getFingerprint());
                log.info("异常记录已保存 - exceptionRecordId={}, fingerprint={}",
                        exceptionRecordId, exceptionInfo.getFingerprint());
                return;
            }

            // 2. 查询告警记录
            AppAlarmRecord appAlarmRecord = appAlarmRecordMapper.selectById(exceptionRecordId);
            if (appAlarmRecord == null) {
                log.error("查询告警记录失败 - id={}", exceptionRecordId);
                return;
            }

            // 3. 生成工单（可以使用 AI 建议的严重级别）
            Long ticketId = ticketGenerationService.generateTicket(appAlarmRecord, denoiseDecision);
            if (ticketId != null) {
                log.info("异常处理完成 - exceptionRecordId={}, ticketId={}, fingerprint={}, aiSuggestion={}",
                        exceptionRecordId, ticketId, exceptionInfo.getFingerprint(),
                        denoiseDecision != null ? denoiseDecision.getSuggestion() : "N/A");
            }

        } catch (Exception e) {
            log.error("处理异常信息失败 - fingerprint={}, error={}",
                    exceptionInfo.getFingerprint(), e.getMessage(), e);
        }
    }
}
