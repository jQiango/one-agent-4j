package com.all.in.one.agent.common.result;

import com.all.in.one.agent.common.constant.CommonConstants;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应结果类
 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    private int code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 成功标志
     */
    private boolean success;

    public Result() {}

    public Result(int code, String message, T data, boolean success) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.success = success;
    }

    /**
     * 成功响应
     */
    public static <T> Result<T> success() {
        return new Result<>(CommonConstants.SUCCESS_CODE, CommonConstants.SUCCESS_MSG, null, true);
    }

    /**
     * 成功响应带数据
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(CommonConstants.SUCCESS_CODE, CommonConstants.SUCCESS_MSG, data, true);
    }

    /**
     * 成功响应带消息和数据
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(CommonConstants.SUCCESS_CODE, message, data, true);
    }

    /**
     * 失败响应
     */
    public static <T> Result<T> error() {
        return new Result<>(CommonConstants.ERROR_CODE, CommonConstants.ERROR_MSG, null, false);
    }

    /**
     * 失败响应带消息
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(CommonConstants.ERROR_CODE, message, null, false);
    }

    /**
     * 失败响应带状态码和消息
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null, false);
    }

    /**
     * 自定义响应
     */
    public static <T> Result<T> build(int code, String message, T data, boolean success) {
        return new Result<>(code, message, data, success);
    }
} 