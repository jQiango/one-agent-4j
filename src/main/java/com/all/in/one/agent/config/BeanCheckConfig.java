package com.all.in.one.agent.config;

import com.all.in.one.agent.service.ExceptionProcessService;
import com.all.in.one.agent.starter.collector.ExceptionCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Bean 检查配置（用于调试）
 *
 * @author One Agent 4J
 */
@Slf4j
@Configuration
public class BeanCheckConfig {

    private final ApplicationContext applicationContext;

    public BeanCheckConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void checkBeans() {
        log.info("========== 检查 Spring Bean 注册情况 ==========");

        // 检查 ExceptionCollector
        if (applicationContext.containsBean("exceptionCollector")) {
            log.info("✓ ExceptionCollector Bean 已注册");
        } else {
            log.warn("✗ ExceptionCollector Bean 未注册");
        }

        // 检查 ExceptionProcessService
        try {
            ExceptionProcessService service = applicationContext.getBean(ExceptionProcessService.class);
            log.info("✓ ExceptionProcessService Bean 已注册: {}", service.getClass().getName());
        } catch (Exception e) {
            log.warn("✗ ExceptionProcessService Bean 未注册: {}", e.getMessage());
        }

        // 检查 ExceptionPersistenceConfig
        if (applicationContext.containsBean("exceptionPersistenceConfig")) {
            log.info("✓ ExceptionPersistenceConfig Bean 已注册");
        } else {
            log.warn("✗ ExceptionPersistenceConfig Bean 未注册");
        }

        log.info("========== Bean 检查完成 ==========");
    }
}
