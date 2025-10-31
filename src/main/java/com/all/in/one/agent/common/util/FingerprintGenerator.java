package com.all.in.one.agent.common.util;

import cn.hutool.crypto.digest.DigestUtil;

/**
 * 异常指纹生成器
 * <p>
 * 用于生成异常的唯一指纹,用于去重和聚合
 * </p>
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
public class FingerprintGenerator {

    /**
     * 生成异常指纹
     * <p>
     * 指纹由以下部分组成:
     * - 异常类型
     * - 错误位置 (类名.方法名:行号)
     * </p>
     *
     * @param exceptionType 异常类型
     * @param errorLocation 错误位置
     * @return 异常指纹 (MD5)
     */
    public static String generate(String exceptionType, String errorLocation) {
        String fingerprintSource = exceptionType + ":" + errorLocation;
        return DigestUtil.md5Hex(fingerprintSource);
    }

    /**
     * 生成异常指纹
     *
     * @param exceptionType 异常类型
     * @param errorClass    错误类名
     * @param errorMethod   错误方法名
     * @param errorLine     错误行号
     * @return 异常指纹 (MD5)
     */
    public static String generate(String exceptionType, String errorClass,
                                   String errorMethod, Integer errorLine) {
        String errorLocation = errorClass + "." + errorMethod + ":" + errorLine;
        return generate(exceptionType, errorLocation);
    }
}
