package com.all.in.one.agent.service;

import com.all.in.one.agent.config.ResponsibilityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 告警责任人识别服务
 * <p>
 * 根据服务名称自动识别责任人
 * 优化版：使用 yml 配置替代数据库表
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Service
public class ResponsibleOwnerService {

    @Autowired(required = false)
    private ResponsibilityProperties responsibilityProps;

    public ResponsibleOwnerService() {
        log.info("ResponsibleOwnerService 初始化完成");
    }

    /**
     * 根据服务名称查找主责任人
     * <p>
     * 查询逻辑：
     * 1. 从 yml 配置查询责任人映射
     * 2. 如果找到配置，返回责任人用户编码
     * 3. 如果未找到配置，返回默认责任人
     * </p>
     *
     * @param serviceName 服务名称
     * @return 责任人用户编码，如果未配置则返回默认值
     */
    public String findResponsibleOwner(String serviceName) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            log.warn("服务名称为空，使用默认责任人");
            return getDefaultOwner();
        }

        try {
            if (responsibilityProps == null) {
                log.warn("责任人配置未加载，使用默认值");
                return "unknown";
            }

            String owner = responsibilityProps.getOwnerForService(serviceName);

            if (responsibilityProps.hasSpecificOwner(serviceName)) {
                log.debug("找到责任人配置 - 服务: {}, 责任人: {}", serviceName, owner);
            } else {
                log.debug("未找到服务专属责任人配置 - 服务: {}, 使用默认责任人: {}",
                        serviceName, owner);
            }

            return owner;

        } catch (Exception e) {
            log.error("查询责任人配置失败 - 服务: {}, 错误: {}",
                    serviceName, e.getMessage(), e);
            return getDefaultOwner();
        }
    }

    /**
     * 获取责任人的飞书 OpenID
     * <p>
     * 用于飞书通知时 @ 提醒责任人。
     * </p>
     *
     * @param serviceName 服务名称
     * @return 飞书 OpenID，如果未配置则返回 null
     */
    public String getFeishuOpenId(String serviceName) {
        if (responsibilityProps == null) {
            return null;
        }

        String owner = findResponsibleOwner(serviceName);
        return responsibilityProps.getFeishuOpenId(owner);
    }

    /**
     * 检查服务是否已配置专属责任人
     *
     * @param serviceName 服务名称
     * @return 如果已配置专属责任人返回 true，否则返回 false
     */
    public boolean isConfigured(String serviceName) {
        if (responsibilityProps == null) {
            return false;
        }
        return responsibilityProps.hasSpecificOwner(serviceName);
    }

    /**
     * 检查责任人是否配置了飞书 OpenID
     *
     * @param serviceName 服务名称
     * @return 如果已配置返回 true，否则返回 false
     */
    public boolean hasFeishuOpenId(String serviceName) {
        if (responsibilityProps == null) {
            return false;
        }

        String owner = findResponsibleOwner(serviceName);
        return responsibilityProps.hasFeishuMapping(owner);
    }

    /**
     * 获取默认责任人
     *
     * @return 默认责任人用户编码
     */
    private String getDefaultOwner() {
        if (responsibilityProps != null) {
            return responsibilityProps.getDefaultOwner();
        }
        return "unknown";
    }
}
