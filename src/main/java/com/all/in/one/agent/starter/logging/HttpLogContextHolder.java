package com.all.in.one.agent.starter.logging;

/**
 * HTTP 日志上下文持有者
 * <p>
 * 使用 ThreadLocal 存储当前请求的上下文信息
 * 供异常捕获等其他组件使用
 * </p>
 *
 * @author One Agent 4J
 */
public class HttpLogContextHolder {

    private static final ThreadLocal<HttpLogFilter.HttpLogContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前请求上下文
     */
    public static void setContext(HttpLogFilter.HttpLogContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 获取当前请求上下文
     */
    public static HttpLogFilter.HttpLogContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除当前请求上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 判断当前线程是否在 HTTP 请求上下文中
     */
    public static boolean hasContext() {
        return CONTEXT_HOLDER.get() != null;
    }
}
