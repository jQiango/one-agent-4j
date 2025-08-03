package com.all.in.one.agent.storage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 存储模块启动类
 */
@SpringBootApplication
@MapperScan("com.all.in.one.agent.storage.mapper")
public class StorageApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(StorageApplication.class, args);
    }
} 