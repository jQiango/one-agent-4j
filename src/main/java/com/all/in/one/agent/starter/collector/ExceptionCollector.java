package com.all.in.one.agent.starter.collector;

import com.all.in.one.agent.common.config.AgentProperties;
import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.common.util.ExceptionInfoBuilder;
import com.all.in.one.agent.starter.dedup.FingerprintDeduplicator;
import com.all.in.one.agent.starter.filter.IgnoreListFilter;
import com.all.in.one.agent.starter.reporter.ExceptionReporter;
import com.all.in.one.agent.starter.rule.RuleEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

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
    private final IgnoreListFilter ignoreListFilter;
    private final FingerprintDeduplicator fingerprintDeduplicator;
    private final RuleEngine ruleEngine;
    private final Random random = new Random();
    private final List<Consumer<ExceptionInfo>> listeners = new ArrayList<>();

    /**
     * ThreadLocal 防止递归捕获
     * 当一个线程正在处理异常时，该线程产生的新异常不会再次被捕获
     */
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);

    public ExceptionCollector(AgentProperties properties,
                             ExceptionReporter reporter,
                             IgnoreListFilter ignoreListFilter,
                             FingerprintDeduplicator fingerprintDeduplicator,
                             RuleEngine ruleEngine) {
        this.properties = properties;
        this.reporter = reporter;
        this.ignoreListFilter = ignoreListFilter;
        this.fingerprintDeduplicator = fingerprintDeduplicator;
        this.ruleEngine = ruleEngine;
        log.info("ExceptionCollector 初始化完成 - appName={}, environment={}, samplingRate={}, ruleEngineEnabled={}",
                properties.getAppName(),
                properties.getEnvironment(),
                properties.getSamplingRate(),
                ruleEngine != null);
    }

    /**
     * 添加监听器
     *
     * @param listener 监听器
     */
    public void addListener(Consumer<ExceptionInfo> listener) {
        if (listener != null) {
            listeners.add(listener);
            log.info("异常收集器已注册监听器 - listener={}", listener.getClass().getSimpleName());
        }
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

        // 防止递归捕获：如果当前线程正在处理异常，则不再处理新异常
        if (PROCESSING.get()) {
            log.debug("检测到递归异常捕获，跳过处理 - exceptionType={}", throwable.getClass().getSimpleName());
            return;
        }

        try {
            // 标记当前线程正在处理异常
            PROCESSING.set(true);

            // 1. 第一阶段：基于 Throwable 的快速过滤（保留旧逻辑，过滤明显不需要的异常）
            if (shouldIgnoreByThrowable(throwable)) {
                log.debug("异常被快速过滤（Throwable 级别） - exceptionType={}", throwable.getClass().getSimpleName());
                return;
            }

            // 2. 构建异常信息
            ExceptionInfo exceptionInfo = ExceptionInfoBuilder.build(
                    throwable,
                    properties.getAppName(),
                    properties.getEnvironment()
            );

            // 3. 第 0 层：基础过滤（基于完整的 ExceptionInfo 进行更精细的过滤）
            if (ignoreListFilter.shouldIgnore(exceptionInfo)) {
                log.debug("异常被第 0 层过滤 - fingerprint={}, type={}, location={}",
                        exceptionInfo.getFingerprint(),
                        exceptionInfo.getExceptionType(),
                        exceptionInfo.getErrorLocation());
                return;
            }

            // 4. 第 1 层：指纹去重（时间窗口内相同指纹的异常只处理一次）
            if (fingerprintDeduplicator.isDuplicate(exceptionInfo)) {
                log.debug("异常被第 1 层过滤 (重复) - fingerprint={}, type={}, location={}",
                        exceptionInfo.getFingerprint(),
                        exceptionInfo.getExceptionType(),
                        exceptionInfo.getErrorLocation());
                return;
            }

            // 4.5. 第 1.5 层：规则引擎（基于业务规则的过滤）
            if (ruleEngine != null) {
                RuleEngine.FilterResult ruleResult = ruleEngine.evaluate(exceptionInfo);
                if (ruleResult.isFiltered()) {
                    log.info("异常被规则引擎过滤 - fingerprint={}, rule={}, reason={}",
                            exceptionInfo.getFingerprint(),
                            ruleResult.getRuleName(),
                            ruleResult.getReason());
                    return;
                }
            }

            log.info("收集到异常 - fingerprint={}, type={}, location={}",
                    exceptionInfo.getFingerprint(),
                    exceptionInfo.getExceptionType(),
                    exceptionInfo.getErrorLocation());

            // 5. HTTP 上报（可选）
            if (properties.getStorageStrategy().isEnableHttpReport()) {
                // 注意：采样率可以在 HTTP 上报时使用，避免上报过多数据
                if (shouldSample()) {
                    reporter.report(exceptionInfo);
                } else {
                    log.debug("HTTP 上报被采样过滤 - fingerprint={}", exceptionInfo.getFingerprint());
                }
            }

            // 6. 通知监听器（本地持久化等）
            // 注意：第 2 层 AI 去噪在持久化阶段进行，AI 会决定是否真正需要持久化和生成工单
            notifyListeners(exceptionInfo);

        } catch (Exception e) {
            log.error("收集异常信息失败", e);
        } finally {
            // 清除标记，允许该线程处理后续的新异常
            PROCESSING.remove();
        }
    }

    /**
     * 通知监听器
     */
    private void notifyListeners(ExceptionInfo exceptionInfo) {
        for (Consumer<ExceptionInfo> listener : listeners) {
            try {
                listener.accept(exceptionInfo);
            } catch (Exception e) {
                log.error("监听器处理异常失败 - listener={}, error={}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
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
     * 基于 Throwable 的快速过滤（保留旧逻辑）
     * 在构建 ExceptionInfo 之前进行快速判断，避免不必要的堆栈解析
     */
    private boolean shouldIgnoreByThrowable(Throwable throwable) {
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
