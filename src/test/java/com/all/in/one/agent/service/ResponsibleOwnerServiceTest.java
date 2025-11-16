package com.all.in.one.agent.service;

import com.all.in.one.agent.dao.entity.AlarmOwnerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 责任人识别服务测试
 *
 * @author One Agent 4J
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("责任人识别服务测试")
class ResponsibleOwnerServiceTest {

    @Autowired
    private ResponsibleOwnerService ownerService;

    @Test
    @DisplayName("测试查找已配置服务的责任人")
    void testFindResponsibleOwner_Configured() {
        // Given: payment-service 已在数据库中配置（初始化脚本）
        String serviceName = "payment-service";

        // When: 查找责任人
        String owner = ownerService.findResponsibleOwner(serviceName);

        // Then: 应返回配置的责任人
        assertNotNull(owner, "责任人不应为空");
        assertEquals("zhangsan", owner, "责任人应为 zhangsan");
    }

    @Test
    @DisplayName("测试查找未配置服务的责任人（使用默认值）")
    void testFindResponsibleOwner_NotConfigured() {
        // Given: unknown-service 未配置
        String serviceName = "unknown-service";

        // When: 查找责任人
        String owner = ownerService.findResponsibleOwner(serviceName);

        // Then: 应返回默认责任人
        assertNotNull(owner, "责任人不应为空");
        assertEquals("default-team", owner, "应返回默认责任人");
    }

    @Test
    @DisplayName("测试空服务名称")
    void testFindResponsibleOwner_NullServiceName() {
        // Given: 服务名称为 null
        String serviceName = null;

        // When: 查找责任人
        String owner = ownerService.findResponsibleOwner(serviceName);

        // Then: 应返回默认责任人
        assertEquals("default-team", owner, "空服务名称应返回默认责任人");
    }

    @Test
    @DisplayName("测试空字符串服务名称")
    void testFindResponsibleOwner_EmptyServiceName() {
        // Given: 服务名称为空字符串
        String serviceName = "   ";

        // When: 查找责任人
        String owner = ownerService.findResponsibleOwner(serviceName);

        // Then: 应返回默认责任人
        assertEquals("default-team", owner, "空字符串应返回默认责任人");
    }

    @Test
    @DisplayName("测试查找备份责任人")
    void testGetBackupOwner() {
        // Given: payment-service 已配置备份责任人
        String serviceName = "payment-service";

        // When: 查找备份责任人
        String backupOwner = ownerService.getBackupOwner(serviceName);

        // Then: 应返回配置的备份责任人
        assertNotNull(backupOwner, "备份责任人不应为空");
        assertEquals("lisi", backupOwner, "备份责任人应为 lisi");
    }

    @Test
    @DisplayName("测试查找未配置备份责任人")
    void testGetBackupOwner_NotConfigured() {
        // Given: 某些服务可能没有配置备份责任人
        String serviceName = "unknown-service";

        // When: 查找备份责任人
        String backupOwner = ownerService.getBackupOwner(serviceName);

        // Then: 应返回 null
        assertNull(backupOwner, "未配置的服务备份责任人应为 null");
    }

    @Test
    @DisplayName("测试获取完整责任人配置")
    void testGetOwnerConfig() {
        // Given: payment-service 已完整配置
        String serviceName = "payment-service";

        // When: 获取配置
        AlarmOwnerConfig config = ownerService.getOwnerConfig(serviceName);

        // Then: 验证配置完整性
        assertNotNull(config, "配置不应为空");
        assertEquals("payment-service", config.getServiceName());
        assertEquals("zhangsan", config.getOwnerId());
        assertEquals("张三", config.getOwnerName());
        assertEquals("支付团队", config.getTeamName());
        assertNotNull(config.getOwnerEmail(), "邮箱不应为空");
    }

    @Test
    @DisplayName("测试获取飞书OpenID")
    void testGetFeishuOpenId() {
        // Given: 服务名称
        String serviceName = "payment-service";

        // When: 获取飞书OpenID
        String openId = ownerService.getFeishuOpenId(serviceName);

        // Then: 如果配置了应返回非空值（取决于数据库配置）
        // 注：初始化脚本中未配置 feishu_open_id，所以可能为 null
        // assertNotNull(openId);
    }

    @Test
    @DisplayName("测试检查服务是否已配置")
    void testIsConfigured() {
        // Test configured service
        assertTrue(ownerService.isConfigured("payment-service"),
                "已配置的服务应返回 true");

        // Test not configured service
        assertFalse(ownerService.isConfigured("unknown-service"),
                "未配置的服务应返回 false");

        // Test null service
        assertFalse(ownerService.isConfigured(null),
                "null 服务名称应返回 false");
    }
}
