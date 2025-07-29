package com.all.in.one.agent.service;

import com.all.in.one.agent.entity.User;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 用户服务接口
 */
public interface IUserService extends IService<User> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    User getByUsername(String username);

    /**
     * 根据状态查询用户列表
     *
     * @param status 状态
     * @return 用户列表
     */
    List<User> listByStatus(Integer status);

    /**
     * 分页查询用户
     *
     * @param page     分页对象
     * @param username 用户名（可选）
     * @param status   状态（可选）
     * @return 分页结果
     */
    IPage<User> getUserPage(Page<User> page, String username, Integer status);

    /**
     * 创建用户
     *
     * @param user 用户信息
     * @return 是否成功
     */
    boolean createUser(User user);

    /**
     * 更新用户
     *
     * @param user 用户信息
     * @return 是否成功
     */
    boolean updateUser(User user);

    /**
     * 删除用户（逻辑删除）
     *
     * @param id 用户ID
     * @return 是否成功
     */
    boolean deleteUser(Long id);
} 