package com.all.in.one.agent.service;

import com.all.in.one.agent.common.model.ExceptionInfo;
import com.all.in.one.agent.dao.entity.ExceptionRecord;
import com.all.in.one.agent.dao.mapper.ExceptionRecordMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 异常持久化服务
 *
 * @author One Agent 4J
 */
@Slf4j
@Service
public class ExceptionPersistenceService {

    private final ExceptionRecordMapper exceptionRecordMapper;
    private final ObjectMapper objectMapper;

    public ExceptionPersistenceService(ExceptionRecordMapper exceptionRecordMapper,
                                       ObjectMapper objectMapper) {
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.objectMapper = objectMapper;
        log.info("ExceptionPersistenceService 初始化完成");
    }

    /**
     * 保存异常记录
     *
     * @param exceptionInfo 异常信息
     * @return 异常记录ID
     */
    public Long saveException(ExceptionInfo exceptionInfo) {
        try {
            ExceptionRecord record = convertToEntity(exceptionInfo);
            exceptionRecordMapper.insert(record);
            log.info("异常记录已保存 - id={}, fingerprint={}, exceptionType={}",
                    record.getId(), record.getFingerprint(), record.getExceptionType());
            return record.getId();
        } catch (Exception e) {
            log.error("保存异常记录失败 - fingerprint={}, error={}",
                    exceptionInfo.getFingerprint(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 转换 ExceptionInfo 到 ExceptionRecord 实体
     */
    private ExceptionRecord convertToEntity(ExceptionInfo exceptionInfo) {
        ExceptionRecord record = new ExceptionRecord();

        // 应用信息
        record.setAppName(exceptionInfo.getAppName());
        record.setEnvironment(exceptionInfo.getEnvironment());
        record.setInstanceId(exceptionInfo.getInstanceId());
        record.setHostname(exceptionInfo.getHostname());
        record.setIp(exceptionInfo.getIp());

        // 异常信息
        record.setExceptionType(exceptionInfo.getExceptionType());
        record.setExceptionMessage(exceptionInfo.getExceptionMessage());
        record.setStackTrace(exceptionInfo.getStackTrace());
        record.setFingerprint(exceptionInfo.getFingerprint());

        // 错误位置
        record.setErrorClass(exceptionInfo.getErrorClass());
        record.setErrorMethod(exceptionInfo.getErrorMethod());
        record.setErrorLine(exceptionInfo.getErrorLine());
        record.setErrorLocation(exceptionInfo.getErrorLocation());

        // 请求信息
        if (exceptionInfo.getRequestInfo() != null) {
            record.setRequestMethod(exceptionInfo.getRequestInfo().getMethod());
            record.setRequestUri(exceptionInfo.getRequestInfo().getUri());
            record.setRequestParams(convertMapToJson(exceptionInfo.getRequestInfo().getParams()));
            record.setClientIp(exceptionInfo.getRequestInfo().getClientIp());
        }

        // 线程信息
        if (exceptionInfo.getThreadInfo() != null) {
            record.setThreadId(exceptionInfo.getThreadInfo().getThreadId());
            record.setThreadName(exceptionInfo.getThreadInfo().getThreadName());
        }

        // 链路追踪
        record.setTraceId(exceptionInfo.getTraceId());
        record.setSpanId(exceptionInfo.getSpanId());

        // 时间信息
        record.setOccurredAt(LocalDateTime.ofInstant(
                exceptionInfo.getOccurredAt(),
                ZoneId.systemDefault()));
        record.setReportedAt(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());

        return record;
    }

    /**
     * 将 Map 转换为 JSON 字符串
     */
    private String convertMapToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("转换 Map 到 JSON 失败", e);
            return obj.toString();
        }
    }
}
