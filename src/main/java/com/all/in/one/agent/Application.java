package com.all.in.one.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One Agent 4J 主应用
 * <p>
 * 引入 one-agent-4j-starter 后自动监控所有异常
 * </p>
 */
@EnableScheduling
@SpringBootApplication
public class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        LOGGER.info("Starting One Agent 4J Application...");
        SpringApplication.run(Application.class, args);
    }
}