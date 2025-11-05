package com.all.in.one.agent.starter.autoconfigure;

import com.all.in.one.agent.common.config.AgentProperties;
import com.all.in.one.agent.starter.capture.ExceptionCaptureAspect;
import com.all.in.one.agent.starter.capture.ExceptionCaptureFilter;
import com.all.in.one.agent.starter.capture.GlobalExceptionHandler;
import com.all.in.one.agent.starter.collector.ExceptionCollector;
import com.all.in.one.agent.starter.dedup.FingerprintDeduplicator;
import com.all.in.one.agent.starter.filter.IgnoreListFilter;
import com.all.in.one.agent.starter.logging.HttpLogFilter;
import com.all.in.one.agent.starter.logging.HttpLogProperties;
import com.all.in.one.agent.starter.reporter.ExceptionReporter;
import com.all.in.one.agent.starter.rule.RuleEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * One Agent 自动装配类
 * <p>
 * 当 one-agent.enabled=true (默认) 时自动装配所有组件
 * </p>
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        prefix = "one-agent",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true  // 默认启用
)
public class AgentAutoConfiguration {

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Bean
    @ConfigurationProperties(prefix = "one-agent")
    public AgentProperties agentProperties() {
        AgentProperties properties = new AgentProperties();

        // 自动设置应用名称和环境
        if (properties.getAppName() == null) {
            properties.setAppName(applicationName);
        }
        if (properties.getEnvironment() == null) {
            properties.setEnvironment(activeProfile);
        }

        log.info("===========================================");
        log.info("One Agent 4J 自动装配开始");
        log.info("应用名称: {}", properties.getAppName());
        log.info("环境: {}", properties.getEnvironment());
        log.info("采样率: {}", properties.getSamplingRate());
        log.info("上报模式: {}", properties.getReportStrategy().getMode());
        log.info("服务器地址: {}", properties.getServerUrl());
        log.info("===========================================");

        return properties;
    }

    @Bean
    public ExceptionReporter exceptionReporter(AgentProperties properties) {
        return new ExceptionReporter(properties);
    }

    @Bean
    public ExceptionCollector exceptionCollector(AgentProperties properties,
                                                   ExceptionReporter reporter,
                                                   IgnoreListFilter ignoreListFilter,
                                                   FingerprintDeduplicator fingerprintDeduplicator,
                                                   @Autowired(required = false) RuleEngine ruleEngine) {
        return new ExceptionCollector(properties, reporter, ignoreListFilter, fingerprintDeduplicator, ruleEngine);
    }

    /**
     * 注册 Filter (需要 Web 应用)
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "one-agent.capture-config", name = "enable-filter", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<ExceptionCaptureFilter> exceptionCaptureFilter(ExceptionCollector collector) {
        FilterRegistrationBean<ExceptionCaptureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ExceptionCaptureFilter(collector));
        registration.addUrlPatterns("/*");
        registration.setName("exceptionCaptureFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

        log.info("注册 ExceptionCaptureFilter");

        return registration;
    }

    /**
     * 注册 ControllerAdvice (需要 Web 应用)
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "one-agent.capture-config", name = "enable-controller-advice", havingValue = "true", matchIfMissing = true)
    public GlobalExceptionHandler globalExceptionHandler(ExceptionCollector collector) {
        log.info("注册 GlobalExceptionHandler");
        return new GlobalExceptionHandler(collector);
    }

    /**
     * 注册 AOP 切面
     */
    @Bean
    @ConditionalOnProperty(prefix = "one-agent.capture-config", name = "enable-aop", havingValue = "true", matchIfMissing = true)
    public ExceptionCaptureAspect exceptionCaptureAspect(ExceptionCollector collector,
                                                          AgentProperties properties) {
        log.info("注册 ExceptionCaptureAspect");
        return new ExceptionCaptureAspect(collector, properties);
    }

    /**
     * 注册 HTTP 请求日志过滤器 (需要 Web 应用)
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "one-agent.http-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<HttpLogFilter> httpLogFilter(HttpLogProperties httpLogProperties) {
        FilterRegistrationBean<HttpLogFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new HttpLogFilter(httpLogProperties));
        registration.addUrlPatterns("/*");
        registration.setName("httpLogFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);  // 在 ExceptionCaptureFilter 之后

        log.info("注册 HttpLogFilter");

        return registration;
    }
}
