package com.all.in.one.agent.mapper;

import com.all.in.one.agent.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 根据状态查询用户列表
     *
     * @param status 状态
     * @return 用户列表
     */
    List<User> selectByStatus(@Param("status") Integer status);

    /**
     * 分页查询用户
     *
     * @param page     分页对象
     * @param username 用户名（可选）
     * @param status   状态（可选）
     * @return 分页结果
     */
    IPage<User> selectUserPage(Page<User> page, 
                               @Param("username") String username, 
                               @Param("status") Integer status);
} 