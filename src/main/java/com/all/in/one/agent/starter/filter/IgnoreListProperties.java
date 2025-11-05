package com.all.in.one.agent.starter.filter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 第 0 层：基础过滤配置
 * <p>
 * 基于静态黑名单过滤异常，预期过滤率 ~10%
 * </p>
 *
 * @author One Agent 4J
 */
@Data
@Component
@ConfigurationProperties(prefix = "one-agent.ignore-list")
public class IgnoreListProperties {

    /**
     * 是否启用基础过滤（默认 true）
     */
    private boolean enabled = true;

    /**
     * 忽略的异常类型（完整类名或简单类名）
     * 例如: ["org.springframework.security.access.AccessDeniedException", "NotFoundException"]
     */
    private Set<String> exceptionTypes = new HashSet<>();

    /**
     * 忽略的包前缀
     * 例如: ["org.springframework.security", "com.example.test"]
     */
    private Set<String> packagePrefixes = new HashSet<>();

    /**
     * 忽略的错误位置（类名.方法名）
     * 例如: ["com.example.HealthCheckController.health", "*.heartbeat"]
     */
    private Set<String> errorLocations = new HashSet<>();

    /**
     * 忽略的应用名称
     * 例如: ["test-app", "mock-service"]
     */
    private Set<String> appNames = new HashSet<>();

    /**
     * 忽略的环境
     * 例如: ["local", "dev"]
     */
    private Set<String> environments = new HashSet<>();

    /**
     * 忽略包含特定关键词的异常消息
     * 例如: ["health check", "heartbeat", "test"]
     */
    private Set<String> messageKeywords = new HashSet<>();

    /**
     * 忽略特定 HTTP 状态码的异常（仅适用于 Web 异常）
     * 例如: [404, 401, 403]
     */
    private Set<Integer> httpStatusCodes = new HashSet<>();

    static {
        // 这里可以添加默认的忽略规则
    }

    /**
     * 添加默认的忽略异常类型
     */
    public void addDefaultExceptionTypes() {
        // 常见的可忽略异常
        exceptionTypes.add("org.springframework.security.access.AccessDeniedException");
        exceptionTypes.add("org.springframework.web.HttpRequestMethodNotSupportedException");
        exceptionTypes.add("NoHandlerFoundException");
    }

    /**
     * 添加默认的忽略包
     */
    public void addDefaultPackages() {
        packagePrefixes.add("org.springframework.boot.actuate");
        packagePrefixes.add("springfox.documentation");
    }

    /**
     * 添加默认的忽略消息关键词
     */
    public void addDefaultMessageKeywords() {
        messageKeywords.add("health check");
        messageKeywords.add("heartbeat");
        messageKeywords.add("ping");
        messageKeywords.add("actuator");
    }
}
