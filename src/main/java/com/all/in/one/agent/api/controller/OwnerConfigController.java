package com.all.in.one.agent.api.controller;

import com.all.in.one.agent.dao.entity.AlarmOwnerConfig;
import com.all.in.one.agent.dao.mapper.AlarmOwnerConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务责任人配置 REST API
 * <p>
 * 提供服务责任人配置的增删改查功能，用于管理界面。
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@RestController
@RequestMapping("/api/owner-config")
public class OwnerConfigController {

    private final AlarmOwnerConfigMapper ownerConfigMapper;

    public OwnerConfigController(AlarmOwnerConfigMapper ownerConfigMapper) {
        this.ownerConfigMapper = ownerConfigMapper;
    }

    /**
     * 查询所有服务责任人配置
     *
     * @return 配置列表
     */
    @GetMapping("/list")
    public List<AlarmOwnerConfig> list() {
        log.info("查询所有服务责任人配置");
        return ownerConfigMapper.selectList(null);
    }

    /**
     * 根据服务名称查询责任人配置
     *
     * @param serviceName 服务名称
     * @return 责任人配置，如果不存在则返回 null
     */
    @GetMapping("/{serviceName}")
    public AlarmOwnerConfig getByServiceName(@PathVariable String serviceName) {
        log.info("查询服务责任人配置 - 服务: {}", serviceName);
        AlarmOwnerConfig config = ownerConfigMapper.selectByServiceName(serviceName);

        if (config == null) {
            log.warn("未找到服务配置 - 服务: {}", serviceName);
        }

        return config;
    }

    /**
     * 新增或更新责任人配置
     * <p>
     * 如果服务已存在配置则更新，否则新增。
     * </p>
     *
     * @param config 责任人配置对象
     * @return 操作结果
     */
    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody AlarmOwnerConfig config) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 检查服务是否已存在配置
            AlarmOwnerConfig existing = ownerConfigMapper.selectByServiceName(config.getServiceName());

            if (existing != null) {
                // 更新现有配置
                config.setId(existing.getId());
                ownerConfigMapper.updateById(config);
                log.info("更新服务责任人配置成功 - 服务: {}, 责任人: {}",
                        config.getServiceName(), config.getOwnerName());
                result.put("success", true);
                result.put("message", "更新成功");
                result.put("action", "update");
            } else {
                // 新增配置
                ownerConfigMapper.insert(config);
                log.info("新增服务责任人配置成功 - 服务: {}, 责任人: {}",
                        config.getServiceName(), config.getOwnerName());
                result.put("success", true);
                result.put("message", "新增成功");
                result.put("action", "insert");
            }

            result.put("data", config);

        } catch (Exception e) {
            log.error("保存服务责任人配置失败 - 服务: {}, 错误: {}",
                    config.getServiceName(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 删除责任人配置
     *
     * @param id 配置ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();

        try {
            AlarmOwnerConfig config = ownerConfigMapper.selectById(id);
            if (config == null) {
                result.put("success", false);
                result.put("message", "配置不存在");
                return result;
            }

            ownerConfigMapper.deleteById(id);
            log.info("删除服务责任人配置成功 - ID: {}, 服务: {}", id, config.getServiceName());

            result.put("success", true);
            result.put("message", "删除成功");

        } catch (Exception e) {
            log.error("删除服务责任人配置失败 - ID: {}, 错误: {}", id, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 批量导入责任人配置
     * <p>
     * 适用于从Excel或CSV批量导入配置。
     * </p>
     *
     * @param configs 配置列表
     * @return 导入结果
     */
    @PostMapping("/batch-import")
    public Map<String, Object> batchImport(@RequestBody List<AlarmOwnerConfig> configs) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failureCount = 0;

        for (AlarmOwnerConfig config : configs) {
            try {
                AlarmOwnerConfig existing = ownerConfigMapper.selectByServiceName(config.getServiceName());
                if (existing != null) {
                    config.setId(existing.getId());
                    ownerConfigMapper.updateById(config);
                } else {
                    ownerConfigMapper.insert(config);
                }
                successCount++;
            } catch (Exception e) {
                log.error("导入配置失败 - 服务: {}, 错误: {}",
                        config.getServiceName(), e.getMessage());
                failureCount++;
            }
        }

        log.info("批量导入完成 - 成功: {}, 失败: {}", successCount, failureCount);

        result.put("success", true);
        result.put("message", String.format("导入完成，成功: %d, 失败: %d", successCount, failureCount));
        result.put("successCount", successCount);
        result.put("failureCount", failureCount);

        return result;
    }
}
