package com.all.in.one.agent.ai.service;

import com.all.in.one.agent.ai.model.DenoiseDecision;
import com.all.in.one.agent.ai.prompt.DenoisePrompt;
import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.dao.entity.ExceptionRecord;
import com.all.in.one.agent.dao.mapper.ExceptionRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * AI 智能去噪服务
 * <p>
 * 使用大模型判断异常是否需要报警
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "one-agent.ai-denoise", name = "enabled", havingValue = "true")
public class AiDenoiseService {

    private final ExceptionRecordMapper exceptionRecordMapper;
    private final DenoiseAiService denoiseAiService;
    private final ObjectMapper objectMapper;

    // 可配置参数
    private final int lookbackMinutes = 2;  // 查看最近 N 分钟的历史
    private final int maxHistoryRecords = 20; // 最多查询多少条历史记录

    public AiDenoiseService(ExceptionRecordMapper exceptionRecordMapper,
                            DenoiseAiService denoiseAiService,
                            ObjectMapper objectMapper) {
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.denoiseAiService = denoiseAiService;
        this.objectMapper = objectMapper;
        log.info("AI 智能去噪服务已启动 - lookbackMinutes={}, maxHistoryRecords={}",
                lookbackMinutes, maxHistoryRecords);
    }

    /**
     * 判断异常是否需要报警
     *
     * @param exceptionInfo 新发生的异常
     * @return 去噪判断结果
     */
    public DenoiseDecision shouldAlert(ExceptionInfo exceptionInfo) {
        try {
            log.debug("开始 AI 去噪判断 - exceptionType={}, location={}",
                    exceptionInfo.getExceptionType(), exceptionInfo.getErrorLocation());

            // 1. 查询最近的历史异常
            List<ExceptionRecord> recentExceptions = queryRecentExceptions(exceptionInfo);
            log.debug("查询到最近 {} 分钟内的历史异常: {} 条",
                    lookbackMinutes, recentExceptions.size());

            // 2. 构建提示词
            String prompt = DenoisePrompt.buildPrompt(exceptionInfo, recentExceptions);
            log.debug("提示词已构建，长度: {} 字符", prompt.length());

            // 3. 调用 AI 服务
            String aiResponse = denoiseAiService.analyzeException(prompt);
            log.debug("大模型响应: {}", aiResponse);

            // 4. 解析结果
            DenoiseDecision decision = parseAiResponse(aiResponse);
            log.info("AI 去噪判断完成 - shouldAlert={}, isDuplicate={}, similarityScore={}, reason={}",
                    decision.isShouldAlert(), decision.isDuplicate(),
                    decision.getSimilarityScore(), decision.getReason());

            return decision;

        } catch (Exception e) {
            log.error("AI 去噪判断失败，默认允许报警 - error={}", e.getMessage(), e);
            // 如果 AI 判断失败，默认允许报警，避免漏报
            return DenoiseDecision.builder()
                    .shouldAlert(true)
                    .isDuplicate(false)
                    .similarityScore(0.0)
                    .suggestedSeverity("P3")
                    .reason("AI 判断失败，默认报警: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 查询最近的历史异常
     */
    private List<ExceptionRecord> queryRecentExceptions(ExceptionInfo exceptionInfo) {
        LocalDateTime startTime = LocalDateTime.ofInstant(
                exceptionInfo.getOccurredAt().minusSeconds(lookbackMinutes * 60L),
                ZoneId.systemDefault()
        );

        return exceptionRecordMapper.findRecentExceptions(
                exceptionInfo.getAppName(),
                startTime,
                maxHistoryRecords
        );
    }

    /**
     * 解析 AI 响应
     */
    private DenoiseDecision parseAiResponse(String aiResponse) {
        try {
            // 尝试提取 JSON（AI 可能会返回带有 markdown 标记的 JSON）
            String json = extractJson(aiResponse);
            return objectMapper.readValue(json, DenoiseDecision.class);
        } catch (Exception e) {
            log.error("解析 AI 响应失败 - response={}, error={}", aiResponse, e.getMessage());
            // 解析失败时的降级策略
            return DenoiseDecision.builder()
                    .shouldAlert(true)
                    .isDuplicate(false)
                    .similarityScore(0.0)
                    .suggestedSeverity("P3")
                    .reason("AI 响应解析失败，默认报警")
                    .build();
        }
    }

    /**
     * 从 AI 响应中提取 JSON
     * AI 可能会返回: ```json\n{...}\n```
     */
    private String extractJson(String response) {
        if (response == null) {
            return "{}";
        }

        // 去除 markdown 代码块标记
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        // 找到第一个 { 和最后一个 }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned.trim();
    }
}
