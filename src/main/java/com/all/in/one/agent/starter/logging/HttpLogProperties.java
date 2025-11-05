package com.all.in.one.agent.starter.logging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * HTTP 请求日志配置
 * <p>
 * 用于配置 HTTP 请求/响应的日志打印行为
 * </p>
 *
 * @author One Agent 4J
 */
@Data
@Component
@ConfigurationProperties(prefix = "one-agent.http-log")
public class HttpLogProperties {

    /**
     * 是否启用 HTTP 日志（默认 true）
     */
    private boolean enabled = true;

    /**
     * 是否打印请求参数（默认 true）
     */
    private boolean logRequest = true;

    /**
     * 是否打印请求 Body（默认 true）
     */
    private boolean logRequestBody = true;

    /**
     * 是否打印响应结果（默认 true）
     */
    private boolean logResponse = true;

    /**
     * 请求 Body 最大打印长度（字节）
     * -1: 无限制
     * 0: 不打印
     * 默认: 4096 (4KB)
     */
    private int requestBodyLimit = 4096;

    /**
     * 响应 Body 最大打印长度（字节）
     * -1: 无限制
     * 0: 不打印
     * 默认: 4096 (4KB)
     */
    private int responseBodyLimit = 4096;

    /**
     * 排除的 URI 前缀
     * 例如: /actuator, /swagger, /favicon.ico
     */
    private Set<String> excludeUriPrefixes = new HashSet<>();

    /**
     * 排除的具体 URI
     * 精确匹配
     */
    private Set<String> excludeUris = new HashSet<>();

    /**
     * 是否打印请求头（默认 false）
     */
    private boolean logHeaders = false;

    /**
     * 需要打印的请求头名称
     * 为空则打印所有（如果 logHeaders=true）
     */
    private Set<String> includeHeaders = new HashSet<>();

    /**
     * 是否在 MDC 中记录请求信息（默认 true）
     * 用于日志系统（Logback/Log4j2）的上下文传递
     */
    private boolean enableMdc = true;

    /**
     * 慢请求阈值（毫秒）
     * 超过此阈值的请求会打印 WARN 级别日志
     * 默认: 3000ms (3秒)
     */
    private long slowRequestThreshold = 3000;

    /**
     * 是否启用彩色日志（默认 false）
     * 仅在控制台环境下有效
     */
    private boolean coloredLog = false;

    static {
        // 默认排除的 URI
    }

    /**
     * 初始化默认排除 URI
     */
    public void addDefaultExcludes() {
        excludeUriPrefixes.add("/actuator");
        excludeUriPrefixes.add("/swagger");
        excludeUriPrefixes.add("/v2/api-docs");
        excludeUriPrefixes.add("/v3/api-docs");
        excludeUris.add("/favicon.ico");
        excludeUris.add("/health");
        excludeUris.add("/ping");
    }

    /**
     * 判断 URI 是否应该被排除
     */
    public boolean shouldExclude(String uri) {
        if (uri == null) {
            return false;
        }

        // 精确匹配
        if (excludeUris.contains(uri)) {
            return true;
        }

        // 前缀匹配
        for (String prefix : excludeUriPrefixes) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}
