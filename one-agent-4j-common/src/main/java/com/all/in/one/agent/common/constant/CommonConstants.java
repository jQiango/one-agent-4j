package com.all.in.one.agent.common.constant;

/**
 * 公共常量类
 */
public class CommonConstants {

    /**
     * 成功状态码
     */
    public static final int SUCCESS_CODE = 200;

    /**
     * 失败状态码
     */
    public static final int ERROR_CODE = 500;

    /**
     * 成功消息
     */
    public static final String SUCCESS_MSG = "操作成功";

    /**
     * 失败消息
     */
    public static final String ERROR_MSG = "操作失败";

    /**
     * 用户状态 - 启用
     */
    public static final int USER_STATUS_ENABLED = 1;

    /**
     * 用户状态 - 禁用
     */
    public static final int USER_STATUS_DISABLED = 0;

    /**
     * 逻辑删除 - 已删除
     */
    public static final int DELETED = 1;

    /**
     * 逻辑删除 - 未删除
     */
    public static final int NOT_DELETED = 0;

    /**
     * 默认分页大小
     */
    public static final long DEFAULT_PAGE_SIZE = 10L;

    /**
     * 最大分页大小
     */
    public static final long MAX_PAGE_SIZE = 100L;
} 