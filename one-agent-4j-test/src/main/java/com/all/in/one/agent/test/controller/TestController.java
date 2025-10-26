package com.all.in.one.agent.test.controller;

import com.all.in.one.agent.test.service.TestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试 Controller
 * <p>
 * 提供各种异常场景用于测试
 * </p>
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private TestService testService;

    /**
     * 正常请求
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello, One Agent 4J!";
    }

    /**
     * 测试 NullPointerException
     */
    @GetMapping("/null-pointer")
    public String testNullPointer() {
        log.info("测试 NullPointerException");
        String str = null;
        return str.length() + ""; // 触发 NullPointerException
    }

    /**
     * 测试 ArrayIndexOutOfBoundsException
     */
    @GetMapping("/array-index")
    public String testArrayIndex() {
        log.info("测试 ArrayIndexOutOfBoundsException");
        int[] arr = {1, 2, 3};
        return arr[10] + ""; // 触发 ArrayIndexOutOfBoundsException
    }

    /**
     * 测试 ArithmeticException
     */
    @GetMapping("/arithmetic")
    public String testArithmetic() {
        log.info("测试 ArithmeticException");
        int result = 10 / 0; // 触发 ArithmeticException
        return result + "";
    }

    /**
     * 测试 IllegalArgumentException
     */
    @GetMapping("/illegal-argument")
    public String testIllegalArgument(@RequestParam String name) {
        log.info("测试 IllegalArgumentException - name={}", name);
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        return "Hello, " + name;
    }

    /**
     * 测试 Service 层异常 (AOP 捕获)
     */
    @GetMapping("/service-exception")
    public String testServiceException() {
        log.info("测试 Service 层异常");
        return testService.businessMethod();
    }

    /**
     * 测试嵌套异常
     */
    @GetMapping("/nested-exception")
    public String testNestedException() {
        log.info("测试嵌套异常");
        try {
            testService.methodWithNestedException();
        } catch (Exception e) {
            throw new RuntimeException("外层异常", e);
        }
        return "success";
    }
}
