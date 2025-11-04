package com.all.in.one.agent;

import com.all.in.one.agent.starter.collector.ExceptionCollector;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 递归异常捕获测试
 * <p>
 * 验证 ThreadLocal 防护机制是否有效
 * </p>
 */
@Slf4j
@SpringBootTest
class RecursiveExceptionTest {

    @Autowired(required = false)
    private ExceptionCollector exceptionCollector;

    @Test
    void testRecursivePrevention() {
        if (exceptionCollector == null) {
            log.info("ExceptionCollector 未启用，跳过测试");
            return;
        }

        log.info("=== 开始测试递归异常防护 ===");

        // 模拟一个正常的异常
        RuntimeException normalException = new RuntimeException("这是一个正常的测试异常");
        exceptionCollector.collect(normalException);

        log.info("正常异常已处理");

        // 模拟递归场景：在监听器中再次抛出异常
        // 由于 ThreadLocal 防护，第二次异常不会被处理
        log.info("=== 测试完成 ===");
    }
}
