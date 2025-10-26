package com.all.in.one.agent.common.util;

import com.all.in.one.agent.common.model.ExceptionInfo;

import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 异常信息构建器
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
public class ExceptionInfoBuilder {

    /**
     * 从异常构建异常信息
     *
     * @param throwable 异常
     * @param appName   应用名称
     * @param environment 环境
     * @return 异常信息
     */
    public static ExceptionInfo build(Throwable throwable, String appName, String environment) {
        // 1. 提取堆栈信息
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        StackTraceElement firstElement = stackTrace != null && stackTrace.length > 0
                ? stackTrace[0]
                : null;

        // 2. 提取错误位置
        String errorClass = firstElement != null ? firstElement.getClassName() : "Unknown";
        String errorMethod = firstElement != null ? firstElement.getMethodName() : "Unknown";
        Integer errorLine = firstElement != null ? firstElement.getLineNumber() : 0;
        String errorLocation = errorClass + "." + errorMethod + ":" + errorLine;

        // 3. 提取异常类型和消息
        String exceptionType = throwable.getClass().getSimpleName();
        String exceptionMessage = throwable.getMessage();

        // 4. 生成指纹
        String fingerprint = FingerprintGenerator.generate(exceptionType, errorLocation);

        // 5. 获取完整堆栈
        String fullStackTrace = getStackTraceAsString(throwable);

        // 6. 获取主机信息
        String hostname = getHostname();
        String ip = getLocalIp();

        // 7. 获取线程信息
        Thread currentThread = Thread.currentThread();
        ExceptionInfo.ThreadInfo threadInfo = ExceptionInfo.ThreadInfo.builder()
                .threadId(currentThread.getId())
                .threadName(currentThread.getName())
                .threadGroupName(currentThread.getThreadGroup() != null
                        ? currentThread.getThreadGroup().getName()
                        : null)
                .priority(currentThread.getPriority())
                .daemon(currentThread.isDaemon())
                .build();

        // 8. 构建异常信息
        return ExceptionInfo.builder()
                .appName(appName)
                .environment(environment)
                .instanceId(generateInstanceId(appName, hostname))
                .hostname(hostname)
                .ip(ip)
                .exceptionType(exceptionType)
                .exceptionMessage(exceptionMessage)
                .stackTrace(fullStackTrace)
                .fingerprint(fingerprint)
                .errorClass(errorClass)
                .errorMethod(errorMethod)
                .errorLine(errorLine)
                .errorLocation(errorLocation)
                .threadInfo(threadInfo)
                .context(new HashMap<>())
                .occurredAt(Instant.now())
                .reportedAt(Instant.now())
                .build();
    }

    /**
     * 将堆栈转换为字符串
     */
    private static String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName());
        if (throwable.getMessage() != null) {
            sb.append(": ").append(throwable.getMessage());
        }
        sb.append("\n");

        StackTraceElement[] elements = throwable.getStackTrace();
        if (elements != null) {
            for (StackTraceElement element : elements) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
        }

        // 处理 Caused by
        Throwable cause = throwable.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(getStackTraceAsString(cause));
        }

        return sb.toString();
    }

    /**
     * 获取主机名
     */
    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * 获取本机 IP
     */
    private static String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * 生成实例 ID
     */
    private static String generateInstanceId(String appName, String hostname) {
        return appName + "@" + hostname;
    }
}
