package com.all.in.one.agent.starter.capture;

import com.all.in.one.agent.starter.collector.ExceptionCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 在 Controller 层面捕获异常
 * </p>
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
@Order // 最低优先级，让业务的 ExceptionHandler 先执行
public class GlobalExceptionHandler {

    private final ExceptionCollector collector;

    public GlobalExceptionHandler(ExceptionCollector collector) {
        this.collector = collector;
        log.info("GlobalExceptionHandler 初始化完成");
    }

    /**
     * 捕获所有异常
     */
    @ExceptionHandler(Throwable.class)
    public void handleException(Throwable throwable) {
        log.warn("ControllerAdvice 捕获到异常 - error={}", throwable.getMessage());

        // 收集异常
        collector.collect(throwable);

        // 继续抛出，让其他异常处理器处理或返回给客户端
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        } else {
            throw new RuntimeException(throwable);
        }
    }
}
