package com.all.in.one.agent.config;

import com.all.in.one.agent.service.ExceptionProcessService;
import com.all.in.one.agent.starter.collector.ExceptionCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 异常持久化配置
 * <p>
 * 将持久化逻辑注入到 ExceptionCollector 中
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "one-agent.storage-strategy", name = "enable-local-persistence", havingValue = "true", matchIfMissing = true)
public class ExceptionPersistenceConfig {

    private final ExceptionCollector exceptionCollector;
    private final ExceptionProcessService exceptionProcessService;

    public ExceptionPersistenceConfig(ExceptionCollector exceptionCollector,
                                       ExceptionProcessService exceptionProcessService) {
        this.exceptionCollector = exceptionCollector;
        this.exceptionProcessService = exceptionProcessService;
        log.info("ExceptionPersistenceConfig 构造函数被调用");
    }

    @PostConstruct
    public void init() {
        log.info("初始化异常持久化配置...");
        // 注册持久化监听器
        exceptionCollector.addListener(exceptionProcessService::processException);
        log.info("异常持久化配置完成 - 已注册持久化监听器");
    }
}
