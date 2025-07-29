package com.all.in.one.agent.service.impl;

import com.all.in.one.agent.entity.User;
import com.all.in.one.agent.mapper.UserMapper;
import com.all.in.one.agent.service.IUserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public User getByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return baseMapper.selectByUsername(username);
    }

    @Override
    public List<User> listByStatus(Integer status) {
        if (status == null) {
            return list();
        }
        return baseMapper.selectByStatus(status);
    }

    @Override
    public IPage<User> getUserPage(Page<User> page, String username, Integer status) {
        return baseMapper.selectUserPage(page, username, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createUser(User user) {
        if (user == null) {
            return false;
        }
        
        // 检查用户名是否已存在
        User existUser = getByUsername(user.getUsername());
        if (existUser != null) {
            log.warn("用户名已存在: {}", user.getUsername());
            return false;
        }
        
        // 设置默认值
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (user.getDeleted() == null) {
            user.setDeleted(0);
        }
        if (user.getVersion() == null) {
            user.setVersion(1);
        }
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        
        return save(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(User user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        
        user.setUpdateTime(LocalDateTime.now());
        return updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long id) {
        if (id == null) {
            return false;
        }
        
        // 使用MyBatis-Plus的逻辑删除
        return removeById(id);
    }
} 