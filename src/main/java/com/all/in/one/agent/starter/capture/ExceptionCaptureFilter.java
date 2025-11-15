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
        } catch (ServletException servletException) {
            // ServletException 通常是包装异常,原始异常已被 ControllerAdvice 处理
            // 只记录真正的 Filter 层异常(非 ServletException 包装的异常)
            Throwable rootCause = getRootCause(servletException);

            // 只有当根因异常是 Filter 链中产生的(不是来自 Controller)时才记录
            if (!isControllerException(rootCause)) {
                log.warn("Filter 捕获到异常 - uri={}, error={}",
                        getRequestUri(request),
                        servletException.getMessage());
                collector.collect(rootCause);
            } else {
                log.debug("Filter 检测到 Controller 异常,已由 ControllerAdvice 处理,跳过记录 - uri={}",
                        getRequestUri(request));
            }

            throw servletException;
        } catch (IOException ioException) {
            // IO 异常(如连接断开)应该记录
            log.warn("Filter 捕获到 IO 异常 - uri={}, error={}",
                    getRequestUri(request),
                    ioException.getMessage());
            collector.collect(ioException);
            throw ioException;
        }
    }

    /**
     * 获取根因异常
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    /**
     * 判断是否是 Controller 层的异常
     * Controller 层异常的堆栈中会包含 DispatcherServlet
     */
    private boolean isControllerException(Throwable throwable) {
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        if (stackTrace == null || stackTrace.length == 0) {
            return false;
        }

        // 检查堆栈中是否包含 DispatcherServlet.doDispatch
        // 这表示异常来自 Controller 处理过程
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.contains("DispatcherServlet") &&
                element.getMethodName().equals("doDispatch")) {
                return true;
            }
        }
        return false;
    }

    private String getRequestUri(ServletRequest request) {
        if (request instanceof HttpServletRequest) {
            return ((HttpServletRequest) request).getRequestURI();
        }
        return "unknown";
    }
}
