package com.all.in.one.agent.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 责任人配置属性
 * <p>
 * 从 yml 配置文件加载责任人信息，替代原有的数据库表 alarm_owner_config
 * 适用于部门内部使用场景，人员相对固定
 * </p>
 *
 * <p>配置示例:</p>
 * <pre>
 * one-agent:
 *   responsibility:
 *     default-owner: "zhangsan"
 *     service-owners:
 *       payment-service: "lisi"
 *       order-service: "wangwu"
 *     feishu-mapping:
 *       zhangsan: "ou_xxx123"
 *       lisi: "ou_xxx456"
 * </pre>
 *
 * @author One Agent 4J
 * @since 2024-11-16
 */
@Configuration
@ConfigurationProperties(prefix = "one-agent.responsibility")
@Data
public class ResponsibilityProperties {

    /**
     * 默认责任人用户编码（必填）
     * <p>当某个服务没有配置专属责任人时，使用此默认值</p>
     */
    private String defaultOwner;

    /**
     * 服务责任人映射（可选）
     * <p>key: 服务名称, value: 责任人用户编码</p>
     */
    private Map<String, String> serviceOwners = new HashMap<>();

    /**
     * 飞书 OpenID 映射（可选）
     * <p>key: 用户编码, value: 飞书 OpenID</p>
     * <p>用于飞书通知时 @ 提醒责任人</p>
     */
    private Map<String, String> feishuMapping = new HashMap<>();

    /**
     * 获取指定服务的责任人
     *
     * @param serviceName 服务名称
     * @return 责任人用户编码，如果未配置则返回默认责任人
     */
    public String getOwnerForService(String serviceName) {
        if (!StringUtils.hasText(serviceName)) {
            return defaultOwner;
        }
        return serviceOwners.getOrDefault(serviceName, defaultOwner);
    }

    /**
     * 获取责任人的飞书 OpenID
     *
     * @param ownerCode 责任人用户编码
     * @return 飞书 OpenID，如果未配置则返回 null
     */
    public String getFeishuOpenId(String ownerCode) {
        if (!StringUtils.hasText(ownerCode)) {
            return null;
        }
        return feishuMapping.get(ownerCode);
    }

    /**
     * 检查配置是否有效
     * <p>在 Spring Bean 初始化后自动执行</p>
     *
     * @throws IllegalStateException 如果 defaultOwner 未配置
     */
    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(defaultOwner)) {
            throw new IllegalStateException(
                "one-agent.responsibility.default-owner 配置不能为空，请在 application.yml 中配置默认责任人"
            );
        }
    }

    /**
     * 获取所有已配置的服务列表
     *
     * @return 服务名称集合
     */
    public java.util.Set<String> getAllConfiguredServices() {
        return serviceOwners.keySet();
    }

    /**
     * 检查服务是否有专属责任人配置
     *
     * @param serviceName 服务名称
     * @return true 如果有专属配置，false 如果使用默认责任人
     */
    public boolean hasSpecificOwner(String serviceName) {
        return serviceOwners.containsKey(serviceName);
    }

    /**
     * 检查责任人是否配置了飞书 OpenID
     *
     * @param ownerCode 责任人用户编码
     * @return true 如果已配置，false 如果未配置
     */
    public boolean hasFeishuMapping(String ownerCode) {
        return feishuMapping.containsKey(ownerCode);
    }
}
