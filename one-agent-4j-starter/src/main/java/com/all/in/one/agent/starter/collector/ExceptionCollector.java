package com.all.in.one.agent.starter.collector;

import com.all.in.one.agent.common.config.AgentProperties;
import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.common.util.ExceptionInfoBuilder;
import com.all.in.one.agent.starter.reporter.ExceptionReporter;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

/**
 * 异常收集器
 * <p>
 * 负责收集异常信息并委托给上报器处理
 * </p>
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Slf4j
public class ExceptionCollector {

    private final AgentProperties properties;
    private final ExceptionReporter reporter;
    private final Random random = new Random();

    public ExceptionCollector(AgentProperties properties, ExceptionReporter reporter) {
        this.properties = properties;
        this.reporter = reporter;
        log.info("ExceptionCollector 初始化完成 - appName={}, environment={}, samplingRate={}",
                properties.getAppName(),
                properties.getEnvironment(),
                properties.getSamplingRate());
    }

    /**
     * 收集异常
     *
     * @param throwable 异常
     */
    public void collect(Throwable throwable) {
        if (throwable == null) {
            return;
        }

        try {
            // 1. 检查采样率
            if (!shouldSample()) {
                log.debug("异常被采样过滤 - exceptionType={}", throwable.getClass().getSimpleName());
                return;
            }

            // 2. 检查是否忽略该异常
            if (shouldIgnore(throwable)) {
                log.debug("异常被忽略 - exceptionType={}", throwable.getClass().getSimpleName());
                return;
            }

            // 3. 构建异常信息
            ExceptionInfo exceptionInfo = ExceptionInfoBuilder.build(
                    throwable,
                    properties.getAppName(),
                    properties.getEnvironment()
            );

            log.info("收集到异常 - fingerprint={}, type={}, location={}",
                    exceptionInfo.getFingerprint(),
                    exceptionInfo.getExceptionType(),
                    exceptionInfo.getErrorLocation());

            // 4. 委托给上报器
            reporter.report(exceptionInfo);

        } catch (Exception e) {
            log.error("收集异常信息失败", e);
        }
    }

    /**
     * 是否应该采样
     */
    private boolean shouldSample() {
        double samplingRate = properties.getSamplingRate();
        if (samplingRate >= 1.0) {
            return true;
        }
        if (samplingRate <= 0.0) {
            return false;
        }
        return random.nextDouble() < samplingRate;
    }

    /**
     * 是否应该忽略该异常
     */
    private boolean shouldIgnore(Throwable throwable) {
        String exceptionClassName = throwable.getClass().getName();

        // 检查忽略的异常类型
        for (String ignoredException : properties.getCaptureConfig().getIgnoredExceptions()) {
            if (exceptionClassName.equals(ignoredException) ||
                    exceptionClassName.endsWith("." + ignoredException)) {
                return true;
            }
        }

        // 检查忽略的包路径
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            String firstClassName = stackTrace[0].getClassName();
            for (String ignoredPackage : properties.getCaptureConfig().getIgnoredPackages()) {
                if (firstClassName.startsWith(ignoredPackage)) {
                    return true;
                }
            }
        }

        return false;
    }
}
