package com.all.in.one.agent.api.controller;

import com.all.in.one.agent.api.model.FastLogExceptionRequest;
import com.all.in.one.agent.api.model.FastLogExceptionResponse;
import com.all.in.one.agent.service.FastLogExceptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * FAST 日志平台回调 API
 * <p>
 * FAST 日志平台上报异常到 One Agent，经过 AI 去噪和工单生成流程
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/fastlog")
@ConditionalOnProperty(prefix = "one-agent.api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FastLogCallbackController {

    private final FastLogExceptionService fastLogExceptionService;

    public FastLogCallbackController(FastLogExceptionService fastLogExceptionService) {
        this.fastLogExceptionService = fastLogExceptionService;
        log.info("FastLogCallbackController 初始化完成");
    }

    /**
     * FAST 日志平台异常上报接口
     * <p>
     * FAST 平台发现异常后调用此接口，One Agent 会进行：
     * 1. AI 智能去噪判断
     * 2. 持久化异常记录
     * 3. 自动生成工单
     * </p>
     *
     * @param request 异常信息
     * @return 处理结果
     */
    @PostMapping("/exception")
    public FastLogExceptionResponse reportException(@RequestBody FastLogExceptionRequest request) {
        try {
            // 参数校验
            if (!StringUtils.hasText(request.getAppName())) {
                return FastLogExceptionResponse.error(400, "应用名称不能为空");
            }

            if (!StringUtils.hasText(request.getEnvironment())) {
                return FastLogExceptionResponse.error(400, "环境不能为空");
            }

            if (!StringUtils.hasText(request.getExceptionType())) {
                return FastLogExceptionResponse.error(400, "异常类型不能为空");
            }

            log.info("收到 FAST 日志平台异常上报 - appName={}, exceptionType={}, environment={}",
                    request.getAppName(), request.getExceptionType(), request.getEnvironment());

            // 处理异常
            return fastLogExceptionService.processException(request);

        } catch (Exception e) {
            log.error("处理 FAST 日志平台异常上报失败", e);
            return FastLogExceptionResponse.error(500, "系统内部错误: " + e.getMessage());
        }
    }

    /**
     * 批量上报异常
     *
     * @param requests 异常列表
     * @return 处理结果列表
     */
    @PostMapping("/exception/batch")
    public java.util.List<FastLogExceptionResponse> reportExceptionBatch(
            @RequestBody java.util.List<FastLogExceptionRequest> requests) {

        log.info("收到 FAST 日志平台批量异常上报 - count={}", requests.size());

        return requests.stream()
                .map(this::reportException)
                .toList();
    }

    /**
     * 健康检查
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public String health() {
        return "FAST Log Callback API is running";
    }

    /**
     * 获取 API 信息
     *
     * @return API 信息
     */
    @GetMapping("/info")
    public java.util.Map<String, Object> info() {
        return java.util.Map.of(
                "name", "FAST Log Callback API",
                "version", "1.0.0",
                "description", "接收 FAST 日志平台异常上报，自动进行 AI 去噪和工单生成",
                "endpoints", java.util.List.of(
                        "/api/v1/fastlog/exception - 异常上报",
                        "/api/v1/fastlog/exception/batch - 批量异常上报",
                        "/api/v1/fastlog/health - 健康检查"
                )
        );
    }
}
