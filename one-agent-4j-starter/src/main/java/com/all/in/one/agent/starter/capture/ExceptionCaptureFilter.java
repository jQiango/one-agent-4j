package com.all.in.one.agent.starter.capture;

import com.all.in.one.agent.starter.collector.ExceptionCollector;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 异常捕获 Filter
 * <p>
 * 在 Servlet Filter 层面捕获异常
 * </p>
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Slf4j
public class ExceptionCaptureFilter implements Filter {

    private final ExceptionCollector collector;

    public ExceptionCaptureFilter(ExceptionCollector collector) {
        this.collector = collector;
        log.info("ExceptionCaptureFilter 初始化完成");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            chain.doFilter(request, response);
        } catch (Throwable throwable) {
            log.warn("Filter 捕获到异常 - uri={}, error={}",
                    getRequestUri(request),
                    throwable.getMessage());

            // 收集异常
            collector.collect(throwable);

            // 继续抛出，让其他异常处理器处理
            if (throwable instanceof IOException) {
                throw (IOException) throwable;
            } else if (throwable instanceof ServletException) {
                throw (ServletException) throwable;
            } else {
                throw new ServletException(throwable);
            }
        }
    }

    private String getRequestUri(ServletRequest request) {
        if (request instanceof HttpServletRequest) {
            return ((HttpServletRequest) request).getRequestURI();
        }
        return "unknown";
    }
}
