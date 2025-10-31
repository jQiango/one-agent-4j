package com.all.in.one.agent.starter.capture;

import com.all.in.one.agent.common.config.AgentProperties;
import com.all.in.one.agent.starter.collector.ExceptionCollector;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * 异常捕获切面
 * <p>
 * 在 Service 层面通过 AOP 捕获异常
 * </p>
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class ExceptionCaptureAspect {

    private final ExceptionCollector collector;
    private final AgentProperties properties;

    public ExceptionCaptureAspect(ExceptionCollector collector, AgentProperties properties) {
        this.collector = collector;
        this.properties = properties;
        log.info("ExceptionCaptureAspect 初始化完成 - pointcut={}",
                properties.getCaptureConfig().getAopPointcut());
    }

    /**
     * 切入点：默认监控所有 service 包下的方法
     * 可通过配置修改
     */
    @Pointcut("execution(* com.*.*.*.service..*.*(..))")
    public void serviceLayer() {
    }

    /**
     * 在方法抛出异常后捕获
     */
    @AfterThrowing(pointcut = "serviceLayer()", throwing = "throwable")
    public void afterThrowing(Throwable throwable) {
        log.warn("AOP 捕获到异常 - error={}", throwable.getMessage());

        // 收集异常
        collector.collect(throwable);
    }
}
