package com.all.in.one.agent.starter.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HTTP 请求日志过滤器
 * <p>
 * 功能：
 * 1. 打印所有 HTTP 请求的入参（URI、Method、Params、Body、Headers）
 * 2. 打印所有 HTTP 响应的出参（Status、Body、Latency）
 * 3. 使用 MDC 记录请求上下文信息
 * 4. 识别慢请求并打印 WARN 日志
 * 5. 支持配置化排除特定 URI
 * </p>
 *
 * <p>
 * 升级点（相比旧版 vega HttpLogRequestFilter）：
 * - ✅ 更灵活的配置（可配置打印内容、长度限制等）
 * - ✅ 慢请求识别和告警
 * - ✅ 更完整的请求上下文信息
 * - ✅ 支持请求头打印
 * - ✅ 更好的异常处理
 * - ✅ 集成到异常监控系统
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
public class HttpLogFilter implements Filter {

    private final HttpLogProperties properties;
    private final ThreadLocal<HttpLogContext> contextHolder = new ThreadLocal<>();

    public HttpLogFilter(HttpLogProperties properties) {
        this.properties = properties;
        log.info("HttpLogFilter 初始化完成 - enabled={}", properties.isEnabled());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!properties.isEnabled() ||
            !(request instanceof HttpServletRequest) ||
            !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String uri = httpRequest.getRequestURI();

        // 排除特定 URI
        if (properties.shouldExclude(uri)) {
            chain.doFilter(request, response);
            return;
        }

        // 包装 Request 和 Response 以支持多次读取
        ContentCachingRequestWrapper requestWrapper = wrapRequest(httpRequest);
        ContentCachingResponseWrapper responseWrapper = wrapResponse(httpResponse);

        // 创建请求上下文
        HttpLogContext context = createContext(requestWrapper);
        contextHolder.set(context);
        HttpLogContextHolder.setContext(context);  // 存储到全局 ThreadLocal

        try {
            // 记录请求开始
            logRequestStart(context, requestWrapper);

            // 执行请求
            chain.doFilter(requestWrapper, responseWrapper);

        } catch (Exception e) {
            // 记录异常（不影响异常传播）
            context.setException(e);
            throw e;
        } finally {
            // 记录请求结束
            logRequestEnd(context, responseWrapper);

            // 复制响应内容到原始 Response
            responseWrapper.copyBodyToResponse();

            // 清理上下文
            contextHolder.remove();
            HttpLogContextHolder.clear();  // 清理全局 ThreadLocal
            if (properties.isEnableMdc()) {
                MDC.clear();
            }
        }
    }

    /**
     * 包装 Request
     */
    private ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper) {
            return (ContentCachingRequestWrapper) request;
        }
        return new ContentCachingRequestWrapper(request);
    }

    /**
     * 包装 Response
     */
    private ContentCachingResponseWrapper wrapResponse(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper) {
            return (ContentCachingResponseWrapper) response;
        }
        return new ContentCachingResponseWrapper(response);
    }

    /**
     * 创建请求上下文
     */
    private HttpLogContext createContext(HttpServletRequest request) {
        HttpLogContext context = new HttpLogContext();
        context.setStartTime(System.currentTimeMillis());
        context.setRequestId(getRequestId(request));
        context.setTraceId(getTraceId(request));
        context.setMethod(request.getMethod());
        context.setUri(request.getRequestURI());
        context.setQueryString(request.getQueryString());
        context.setClientIp(getClientIp(request));
        context.setUserAgent(request.getHeader("User-Agent"));

        return context;
    }

    /**
     * 记录请求开始
     */
    private void logRequestStart(HttpLogContext context, ContentCachingRequestWrapper request) {
        if (!properties.isLogRequest()) {
            return;
        }

        // 使用 MDC 记录上下文信息
        if (properties.isEnableMdc()) {
            MDC.put("requestId", context.getRequestId());
            MDC.put("traceId", context.getTraceId());
            MDC.put("method", context.getMethod());
            MDC.put("uri", context.getUri());
            MDC.put("clientIp", context.getClientIp());
            MDC.put("bltag", "request_in");
        }

        // 构建请求日志
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("HTTP Request IN: ")
                .append(context.getMethod()).append(" ")
                .append(context.getUri());

        // Query String
        if (context.getQueryString() != null) {
            logBuilder.append("?").append(context.getQueryString());
        }

        // 客户端 IP
        logBuilder.append(" | clientIp=").append(context.getClientIp());

        // 请求头
        if (properties.isLogHeaders()) {
            Map<String, String> headers = extractHeaders(request);
            if (!headers.isEmpty()) {
                logBuilder.append(" | headers=").append(headers);
            }
        }

        // 请求参数
        String params = extractParams(request);
        if (params != null && !params.isEmpty()) {
            logBuilder.append(" | params=").append(params);
        }

        // 请求 Body
        if (properties.isLogRequestBody()) {
            String body = extractRequestBody(request);
            if (body != null && !body.isEmpty()) {
                logBuilder.append(" | body=").append(body);
            }
        }

        log.info(logBuilder.toString());
    }

    /**
     * 记录请求结束
     */
    private void logRequestEnd(HttpLogContext context, ContentCachingResponseWrapper response) {
        if (!properties.isLogResponse()) {
            return;
        }

        long endTime = System.currentTimeMillis();
        long latency = endTime - context.getStartTime();
        context.setLatency(latency);
        context.setStatus(response.getStatus());

        // 更新 MDC
        if (properties.isEnableMdc()) {
            MDC.put("latency", String.valueOf(latency));
            MDC.put("status", String.valueOf(response.getStatus()));
            MDC.put("bltag", "request_out");
        }

        // 构建响应日志
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("HTTP Response OUT: ")
                .append(context.getMethod()).append(" ")
                .append(context.getUri())
                .append(" | status=").append(context.getStatus())
                .append(" | latency=").append(latency).append("ms");

        // 响应 Body
        String responseBody = extractResponseBody(response);
        if (responseBody != null && !responseBody.isEmpty()) {
            logBuilder.append(" | response=").append(responseBody);
        }

        // 判断是否是慢请求
        if (latency > properties.getSlowRequestThreshold()) {
            log.warn("[SLOW REQUEST] " + logBuilder.toString());
        } else {
            log.info(logBuilder.toString());
        }
    }

    /**
     * 提取请求参数
     */
    private String extractParams(HttpServletRequest request) {
        Map<String, String[]> paramMap = request.getParameterMap();
        if (paramMap == null || paramMap.isEmpty()) {
            return null;
        }

        return paramMap.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    /**
     * 提取请求 Body
     */
    private String extractRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }

        String body = new String(content, StandardCharsets.UTF_8);
        return truncate(body, properties.getRequestBodyLimit());
    }

    /**
     * 提取响应 Body
     */
    private String extractResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }

        String body = new String(content, StandardCharsets.UTF_8);
        return truncate(body, properties.getResponseBodyLimit());
    }

    /**
     * 提取请求头
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            // 如果指定了包含的头，只打印指定的
            if (!properties.getIncludeHeaders().isEmpty() &&
                !properties.getIncludeHeaders().contains(headerName)) {
                continue;
            }

            headers.put(headerName, request.getHeader(headerName));
        }

        return headers;
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int limit) {
        if (limit == 0) {
            return "";
        }
        if (limit < 0 || str.length() <= limit) {
            return str;
        }
        return str.substring(0, limit) + "...(已截断)";
    }

    /**
     * 获取请求 ID
     */
    private String getRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isEmpty()) {
            requestId = request.getHeader("X-Req-Id");
        }
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    }

    /**
     * 获取 TraceId
     */
    private String getTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = request.getHeader("traceId");
        }
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index).trim();
            }
            return ip.trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * 获取当前请求上下文（供其他组件使用）
     */
    public static HttpLogContext getCurrentContext() {
        return HttpLogContextHolder.getContext();
    }

    @Override
    public void destroy() {
        log.info("HttpLogFilter 销毁");
    }

    /**
     * HTTP 请求日志上下文
     */
    @lombok.Data
    public static class HttpLogContext {
        private long startTime;
        private String requestId;
        private String traceId;
        private String method;
        private String uri;
        private String queryString;
        private String clientIp;
        private String userAgent;
        private int status;
        private long latency;
        private Exception exception;
    }
}
