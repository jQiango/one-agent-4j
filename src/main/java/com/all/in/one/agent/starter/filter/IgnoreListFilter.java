package com.all.in.one.agent.starter.filter;

import com.all.in.one.agent.common.model.ExceptionInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 第 0 层：基础过滤器
 * <p>
 * 基于静态黑名单快速过滤明确不需要处理的异常
 * 预期过滤率: ~10%
 * 性能: < 1ms
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Component
public class IgnoreListFilter {

    private final IgnoreListProperties properties;

    // 统计信息
    private long totalChecked = 0;
    private long totalFiltered = 0;

    public IgnoreListFilter(IgnoreListProperties properties) {
        this.properties = properties;
        logConfiguration();
    }

    /**
     * 判断异常是否应该被忽略
     *
     * @param exceptionInfo 异常信息
     * @return true=应该忽略（过滤掉），false=不应该忽略（继续处理）
     */
    public boolean shouldIgnore(ExceptionInfo exceptionInfo) {
        if (!properties.isEnabled()) {
            return false;
        }

        totalChecked++;

        // 1. 检查应用名称
        if (shouldIgnoreByAppName(exceptionInfo.getAppName())) {
            totalFiltered++;
            log.debug("基础过滤: 应用名称匹配黑名单, app={}", exceptionInfo.getAppName());
            return true;
        }

        // 2. 检查环境
        if (shouldIgnoreByEnvironment(exceptionInfo.getEnvironment())) {
            totalFiltered++;
            log.debug("基础过滤: 环境匹配黑名单, env={}", exceptionInfo.getEnvironment());
            return true;
        }

        // 3. 检查异常类型（完整类名）
        if (shouldIgnoreByExceptionType(exceptionInfo.getExceptionType())) {
            totalFiltered++;
            log.debug("基础过滤: 异常类型匹配黑名单, type={}", exceptionInfo.getExceptionType());
            return true;
        }

        // 4. 检查包前缀
        if (shouldIgnoreByPackage(exceptionInfo.getErrorLocation())) {
            totalFiltered++;
            log.debug("基础过滤: 包前缀匹配黑名单, location={}", exceptionInfo.getErrorLocation());
            return true;
        }

        // 5. 检查错误位置
        if (shouldIgnoreByErrorLocation(exceptionInfo.getErrorLocation())) {
            totalFiltered++;
            log.debug("基础过滤: 错误位置匹配黑名单, location={}", exceptionInfo.getErrorLocation());
            return true;
        }

        // 6. 检查异常消息关键词
        if (shouldIgnoreByMessage(exceptionInfo.getExceptionMessage())) {
            totalFiltered++;
            log.debug("基础过滤: 异常消息包含黑名单关键词, message={}",
                exceptionInfo.getExceptionMessage());
            return true;
        }

        // 7. 检查 HTTP 状态码（如果有）
        if (shouldIgnoreByHttpStatus(exceptionInfo)) {
            totalFiltered++;
            log.debug("基础过滤: HTTP 状态码匹配黑名单");
            return true;
        }

        return false;
    }

    /**
     * 检查应用名称
     */
    private boolean shouldIgnoreByAppName(String appName) {
        if (appName == null || properties.getAppNames().isEmpty()) {
            return false;
        }
        return properties.getAppNames().contains(appName);
    }

    /**
     * 检查环境
     */
    private boolean shouldIgnoreByEnvironment(String environment) {
        if (environment == null || properties.getEnvironments().isEmpty()) {
            return false;
        }
        return properties.getEnvironments().contains(environment);
    }

    /**
     * 检查异常类型
     * 支持完整类名和简单类名匹配
     */
    private boolean shouldIgnoreByExceptionType(String exceptionType) {
        if (exceptionType == null || properties.getExceptionTypes().isEmpty()) {
            return false;
        }

        // 完全匹配
        if (properties.getExceptionTypes().contains(exceptionType)) {
            return true;
        }

        // 简单类名匹配
        String simpleClassName = exceptionType.contains(".")
            ? exceptionType.substring(exceptionType.lastIndexOf('.') + 1)
            : exceptionType;

        return properties.getExceptionTypes().contains(simpleClassName);
    }

    /**
     * 检查包前缀
     */
    private boolean shouldIgnoreByPackage(String errorLocation) {
        if (errorLocation == null || properties.getPackagePrefixes().isEmpty()) {
            return false;
        }

        // 提取类名部分（去掉方法名和行号）
        String className = errorLocation.contains(".")
            ? errorLocation.substring(0, errorLocation.lastIndexOf('.'))
            : errorLocation;

        // 去掉方法名后的内容
        if (className.contains(":")) {
            className = className.substring(0, className.indexOf(':'));
        }

        // 检查是否匹配任何包前缀
        for (String prefix : properties.getPackagePrefixes()) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查错误位置
     * 支持精确匹配和通配符匹配
     * 例如: "com.example.Controller.health" 或 "*.health"
     */
    private boolean shouldIgnoreByErrorLocation(String errorLocation) {
        if (errorLocation == null || properties.getErrorLocations().isEmpty()) {
            return false;
        }

        for (String pattern : properties.getErrorLocations()) {
            if (matchesLocationPattern(errorLocation, pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 匹配位置模式
     */
    private boolean matchesLocationPattern(String location, String pattern) {
        // 精确匹配
        if (location.equals(pattern)) {
            return true;
        }

        // 通配符匹配: *.methodName
        if (pattern.startsWith("*.")) {
            String methodName = pattern.substring(2);
            // 提取 location 中的方法名
            String locMethodName = extractMethodName(location);
            return methodName.equals(locMethodName);
        }

        // 通配符匹配: ClassName.*
        if (pattern.endsWith(".*")) {
            String className = pattern.substring(0, pattern.length() - 2);
            return location.startsWith(className + ".");
        }

        return false;
    }

    /**
     * 从错误位置中提取方法名
     * 例如: "com.example.Controller.health:123" → "health"
     */
    private String extractMethodName(String location) {
        if (location == null) {
            return "";
        }

        // 去掉行号
        String withoutLine = location.contains(":")
            ? location.substring(0, location.indexOf(':'))
            : location;

        // 提取最后一个点之后的内容
        return withoutLine.contains(".")
            ? withoutLine.substring(withoutLine.lastIndexOf('.') + 1)
            : withoutLine;
    }

    /**
     * 检查异常消息关键词
     */
    private boolean shouldIgnoreByMessage(String message) {
        if (message == null || properties.getMessageKeywords().isEmpty()) {
            return false;
        }

        String lowerMessage = message.toLowerCase();
        for (String keyword : properties.getMessageKeywords()) {
            if (lowerMessage.contains(keyword.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查 HTTP 状态码
     * 从 ExceptionInfo 的扩展字段中提取
     */
    private boolean shouldIgnoreByHttpStatus(ExceptionInfo exceptionInfo) {
        if (properties.getHttpStatusCodes().isEmpty()) {
            return false;
        }

        // 尝试从异常消息中提取状态码（如果是 HTTP 异常）
        // 例如: "404 Not Found" 或 "HTTP 404"
        String message = exceptionInfo.getExceptionMessage();
        if (message != null) {
            for (Integer statusCode : properties.getHttpStatusCodes()) {
                if (message.contains(statusCode.toString()) ||
                    message.contains("HTTP " + statusCode)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 获取过滤统计
     */
    public FilterStats getStats() {
        return FilterStats.builder()
            .totalChecked(totalChecked)
            .totalFiltered(totalFiltered)
            .filterRate(totalChecked > 0 ? (double) totalFiltered / totalChecked : 0.0)
            .build();
    }

    /**
     * 重置统计
     */
    public void resetStats() {
        totalChecked = 0;
        totalFiltered = 0;
    }

    /**
     * 打印配置信息
     */
    private void logConfiguration() {
        if (!properties.isEnabled()) {
            log.info("第 0 层基础过滤已禁用");
            return;
        }

        log.info("========== 第 0 层：基础过滤配置 ==========");
        log.info("忽略异常类型: {}", properties.getExceptionTypes().isEmpty()
            ? "无" : properties.getExceptionTypes());
        log.info("忽略包前缀: {}", properties.getPackagePrefixes().isEmpty()
            ? "无" : properties.getPackagePrefixes());
        log.info("忽略错误位置: {}", properties.getErrorLocations().isEmpty()
            ? "无" : properties.getErrorLocations());
        log.info("忽略应用: {}", properties.getAppNames().isEmpty()
            ? "无" : properties.getAppNames());
        log.info("忽略环境: {}", properties.getEnvironments().isEmpty()
            ? "无" : properties.getEnvironments());
        log.info("忽略消息关键词: {}", properties.getMessageKeywords().isEmpty()
            ? "无" : properties.getMessageKeywords());
        log.info("忽略 HTTP 状态码: {}", properties.getHttpStatusCodes().isEmpty()
            ? "无" : properties.getHttpStatusCodes());
        log.info("=========================================");
    }

    /**
     * 过滤统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class FilterStats {
        private long totalChecked;
        private long totalFiltered;
        private double filterRate;
    }
}
