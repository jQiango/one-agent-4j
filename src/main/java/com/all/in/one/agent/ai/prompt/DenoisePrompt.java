package com.all.in.one.agent.ai.prompt;

import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.dao.entity.ExceptionRecord;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 去噪提示词模板
 *
 * @author One Agent 4J
 */
@Slf4j
public class DenoisePrompt {

    private static final String TEMPLATE_PATH = "/prompts/denoise-prompt-template.txt";
    private static String templateCache = null;

    /**
     * 构建去噪判断的提示词
     *
     * @param newException     新发生的异常
     * @param recentExceptions 最近 N 分钟内的历史异常
     * @return 提示词
     */
    public static String buildPrompt(ExceptionInfo newException, List<ExceptionRecord> recentExceptions) {
        String template = loadTemplate();

        // 构建请求信息部分
        String requestInfo = "";
        if (newException.getRequestInfo() != null) {
            requestInfo = "请求URI: " + newException.getRequestInfo().getUri() + "\n";
        }

        // 构建历史异常部分
        String historySection = buildHistorySection(recentExceptions);

        // 替换模板变量
        return template
                .replace("{{appName}}", nullSafe(newException.getAppName()))
                .replace("{{environment}}", nullSafe(newException.getEnvironment()))
                .replace("{{exceptionType}}", nullSafe(newException.getExceptionType()))
                .replace("{{exceptionMessage}}", nullSafe(newException.getExceptionMessage()))
                .replace("{{errorLocation}}", nullSafe(newException.getErrorLocation()))
                .replace("{{occurredAt}}", nullSafe(newException.getOccurredAt()))
                .replace("{{requestInfo}}", requestInfo)
                .replace("{{stackTrace}}", truncateStackTrace(newException.getStackTrace(), 10))
                .replace("{{historySection}}", historySection);
    }

    /**
     * 加载模板文件
     */
    private static String loadTemplate() {
        if (templateCache != null) {
            return templateCache;
        }

        try (InputStream is = DenoisePrompt.class.getResourceAsStream(TEMPLATE_PATH)) {
            if (is == null) {
                log.error("无法找到提示词模板文件: {}", TEMPLATE_PATH);
                throw new RuntimeException("提示词模板文件不存在: " + TEMPLATE_PATH);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                templateCache = reader.lines().collect(Collectors.joining("\n"));
                log.info("成功加载提示词模板，长度: {} 字符", templateCache.length());
                return templateCache;
            }
        } catch (IOException e) {
            log.error("读取提示词模板文件失败: {}", TEMPLATE_PATH, e);
            throw new RuntimeException("读取提示词模板文件失败", e);
        }
    }

    /**
     * 构建历史异常部分
     */
    private static String buildHistorySection(List<ExceptionRecord> recentExceptions) {
        if (recentExceptions.isEmpty()) {
            return "（无历史记录，这是首次发生的异常）\n";
        }

        StringBuilder history = new StringBuilder();
        history.append("共 ").append(recentExceptions.size()).append(" 条历史记录:\n\n");

        for (int i = 0; i < recentExceptions.size() && i < 10; i++) {
            ExceptionRecord record = recentExceptions.get(i);
            history.append("## 历史异常 #").append(i + 1).append("\n");
            history.append("```\n");
            history.append("ID: ").append(record.getId()).append("\n");
            history.append("异常类型: ").append(record.getExceptionType()).append("\n");
            history.append("异常消息: ").append(nullSafe(record.getExceptionMessage())).append("\n");
            history.append("错误位置: ").append(nullSafe(record.getErrorLocation())).append("\n");
            history.append("发生时间: ").append(record.getOccurredAt()).append("\n");
            history.append("指纹: ").append(record.getFingerprint()).append("\n");
            history.append("```\n\n");
        }

        return history.toString();
    }

    /**
     * 空值安全转换
     */
    private static String nullSafe(Object obj) {
        return obj == null ? "" : obj.toString();
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
