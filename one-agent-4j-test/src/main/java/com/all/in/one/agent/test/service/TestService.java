package com.all.in.one.agent.test.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 测试 Service
 * <p>
 * 用于测试 AOP 捕获异常
 * </p>
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Slf4j
@Service
public class TestService {

    /**
     * 业务方法 - 抛出异常
     */
    public String businessMethod() {
        log.info("执行业务方法");
        throw new RuntimeException("业务异常：数据处理失败");
    }

    /**
     * 带嵌套异常的方法
     */
    public void methodWithNestedException() {
        try {
            innerMethod();
        } catch (Exception e) {
            throw new RuntimeException("Service 层异常", e);
        }
    }

    private void innerMethod() {
        throw new IllegalStateException("内部方法异常");
    }
}
