package com.all.in.one.agent.ai.prompt;

import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.dao.entity.ExceptionRecord;

import java.util.List;

/**
 * AI 去噪提示词模板
 *
 * @author One Agent 4J
 */
public class DenoisePrompt {

    /**
     * 构建去噪判断的提示词
     *
     * @param newException     新发生的异常
     * @param recentExceptions 最近 N 分钟内的历史异常
     * @return 提示词
     */
    public static String buildPrompt(ExceptionInfo newException, List<ExceptionRecord> recentExceptions) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 任务说明\n");
        prompt.append("你是一个异常监控系统的智能去噪助手。你的任务是判断新发生的异常是否需要报警。\n\n");

        prompt.append("# 判断标准\n");
        prompt.append("1. **重复异常**: 如果新异常与最近的历史异常高度相似（相同类型、相同位置、相同原因），应该判定为重复，不需要重复报警\n");
        prompt.append("2. **频繁异常**: 如果短时间内发生了大量相同或相似的异常，可能是系统性问题，建议合并报警\n");
        prompt.append("3. **新异常**: 如果是新类型的异常或在新位置发生的异常，应该报警\n");
        prompt.append("4. **严重程度变化**: 如果异常的影响范围或严重程度发生变化，应该重新报警\n\n");

        prompt.append("# 新异常信息\n");
        prompt.append("```\n");
        prompt.append("应用名称: ").append(newException.getAppName()).append("\n");
        prompt.append("环境: ").append(newException.getEnvironment()).append("\n");
        prompt.append("异常类型: ").append(newException.getExceptionType()).append("\n");
        prompt.append("异常消息: ").append(newException.getExceptionMessage()).append("\n");
        prompt.append("错误位置: ").append(newException.getErrorLocation()).append("\n");
        prompt.append("发生时间: ").append(newException.getOccurredAt()).append("\n");
        if (newException.getRequestInfo() != null) {
            prompt.append("请求URI: ").append(newException.getRequestInfo().getUri()).append("\n");
        }
        prompt.append("堆栈摘要:\n");
        prompt.append(truncateStackTrace(newException.getStackTrace(), 10)).append("\n");
        prompt.append("```\n\n");

        prompt.append("# 最近2分钟内的历史异常记录\n");
        if (recentExceptions.isEmpty()) {
            prompt.append("（无历史记录，这是首次发生的异常）\n\n");
        } else {
            prompt.append("共 ").append(recentExceptions.size()).append(" 条历史记录:\n\n");
            for (int i = 0; i < recentExceptions.size() && i < 10; i++) {
                ExceptionRecord record = recentExceptions.get(i);
                prompt.append("## 历史异常 #").append(i + 1).append("\n");
                prompt.append("```\n");
                prompt.append("ID: ").append(record.getId()).append("\n");
                prompt.append("异常类型: ").append(record.getExceptionType()).append("\n");
                prompt.append("异常消息: ").append(record.getExceptionMessage()).append("\n");
                prompt.append("错误位置: ").append(record.getErrorLocation()).append("\n");
                prompt.append("发生时间: ").append(record.getOccurredAt()).append("\n");
                prompt.append("指纹: ").append(record.getFingerprint()).append("\n");
                prompt.append("```\n\n");
            }
        }

        prompt.append("# 输出要求\n");
        prompt.append("请以 JSON 格式返回你的判断结果，格式如下:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"shouldAlert\": true/false,           // 是否应该报警\n");
        prompt.append("  \"isDuplicate\": true/false,          // 是否是重复异常\n");
        prompt.append("  \"similarityScore\": 0.0-1.0,         // 与历史异常的相似度\n");
        prompt.append("  \"suggestedSeverity\": \"P0/P1/P2/P3/P4\", // 建议的严重级别\n");
        prompt.append("  \"reason\": \"判断原因的简短说明\",\n");
        prompt.append("  \"relatedExceptionIds\": [1, 2, 3],   // 相关的历史异常ID列表\n");
        prompt.append("  \"suggestion\": \"给运维人员的建议\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("请只返回 JSON，不要包含其他内容。\n");

        return prompt.toString();
    }

    /**
     * 截断堆栈信息，只保留前 N 行
     */
    private static String truncateStackTrace(String stackTrace, int maxLines) {
        if (stackTrace == null) {
            return "";
        }
        String[] lines = stackTrace.split("\n");
        if (lines.length <= maxLines) {
            return stackTrace;
        }
        return String.join("\n", java.util.Arrays.copyOf(lines, maxLines)) + "\n... (堆栈已截断)";
    }
}
