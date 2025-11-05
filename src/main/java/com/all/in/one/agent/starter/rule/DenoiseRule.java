package com.all.in.one.agent.starter.rule;

import com.all.in.one.agent.common.model.ExceptionInfo;

/**
 * 降噪规则接口
 * <p>
 * 所有业务规则都实现此接口
 * </p>
 *
 * @author One Agent 4J
 */
public interface DenoiseRule {

    /**
     * 判断是否应该过滤此异常
     *
     * @param exceptionInfo 异常信息
     * @return true=应该过滤（不告警），false=不过滤（继续处理）
     */
    boolean shouldFilter(ExceptionInfo exceptionInfo);

    /**
     * 获取规则名称
     */
    String getRuleName();

    /**
     * 获取过滤原因
     */
    String getReason();

    /**
     * 获取规则优先级（数字越小优先级越高）
     * 默认优先级为 100
     */
    default int getPriority() {
        return 100;
    }

    /**
     * 规则是否启用
     */
    default boolean isEnabled() {
        return true;
    }
}
