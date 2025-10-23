-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `one_agent_4j` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `one_agent_4j`;

-- 创建用户表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`
(
    `id`          bigint       NOT NULL COMMENT '主键ID',
    `username`    varchar(50)  NOT NULL COMMENT '用户名',
    `password`    varchar(100) NOT NULL COMMENT '密码',
    `email`       varchar(100) DEFAULT NULL COMMENT '邮箱',
    `phone`       varchar(20)  DEFAULT NULL COMMENT '手机号',
    `status`      int          DEFAULT '1' COMMENT '状态 1:启用 0:禁用',
    `deleted`     int          DEFAULT '0' COMMENT '逻辑删除 1:删除 0:未删除',
    `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`     int          DEFAULT '1' COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户表';

-- 插入示例数据
INSERT INTO `sys_user` (`id`, `username`, `password`, `email`, `phone`, `status`, `deleted`, `version`)
VALUES (1, 'admin', '$2a$10$7JB720yubVSOfvVme6/el.u6dK4JjNfQDqz7/u2XhgGgCL9E9G5MO', 'admin@example.com', '13800138000',
        1, 0, 1),
       (2, 'user1', '$2a$10$7JB720yubVSOfvVme6/el.u6dK4JjNfQDqz7/u2XhgGgCL9E9G5MO', 'user1@example.com', '13800138001',
        1, 0, 1),
       (3, 'user2', '$2a$10$7JB720yubVSOfvVme6/el.u6dK4JjNfQDqz7/u2XhgGgCL9E9G5MO', 'user2@example.com', '13800138002',
        1, 0, 1),
       (4, 'test', '$2a$10$7JB720yubVSOfvVme6/el.u6dK4JjNfQDqz7/u2XhgGgCL9E9G5MO', 'test@example.com', '13800138003', 0,
        0, 1);