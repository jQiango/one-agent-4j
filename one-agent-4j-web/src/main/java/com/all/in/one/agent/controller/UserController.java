package com.all.in.one.agent.controller;

import com.all.in.one.agent.common.result.Result;
import com.all.in.one.agent.entity.User;
import com.all.in.one.agent.service.IUserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    /**
     * 获取所有用户
     */
    @GetMapping
    public Result<List<User>> getAllUsers() {
        List<User> users = userService.list();
        return Result.success("查询成功", users);
    }

    /**
     * 根据ID获取用户
     */
    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user != null) {
            return Result.success("查询成功", user);
        } else {
            return Result.error("用户不存在");
        }
    }

    /**
     * 根据用户名获取用户
     */
    @GetMapping("/username/{username}")
    public Result<User> getUserByUsername(@PathVariable String username) {
        User user = userService.getByUsername(username);
        if (user != null) {
            return Result.success("查询成功", user);
        } else {
            return Result.error("用户不存在");
        }
    }

    /**
     * 根据状态获取用户列表
     */
    @GetMapping("/status/{status}")
    public Result<List<User>> getUsersByStatus(@PathVariable Integer status) {
        List<User> users = userService.listByStatus(status);
        return Result.success("查询成功", users);
    }

    /**
     * 分页查询用户
     */
    @GetMapping("/page")
    public Result<IPage<User>> getUserPage(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status) {
        
        Page<User> page = new Page<>(current, size);
        IPage<User> userPage = userService.getUserPage(page, username, status);
        return Result.success("查询成功", userPage);
    }

    /**
     * 创建用户
     */
    @PostMapping
    public Result<String> createUser(@RequestBody User user) {
        boolean success = userService.createUser(user);
        if (success) {
            return Result.success("创建成功");
        } else {
            return Result.error("创建失败");
        }
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public Result<String> updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        boolean success = userService.updateUser(user);
        if (success) {
            return Result.success("更新成功");
        } else {
            return Result.error("更新失败");
        }
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteUser(@PathVariable Long id) {
        boolean success = userService.deleteUser(id);
        if (success) {
            return Result.success("删除成功");
        } else {
            return Result.error("删除失败");
        }
    }

    /**
     * 批量删除用户
     */
    @DeleteMapping("/batch")
    public Result<String> batchDeleteUsers(@RequestBody List<Long> ids) {
        boolean success = userService.removeByIds(ids);
        if (success) {
            return Result.success("批量删除成功");
        } else {
            return Result.error("批量删除失败");
        }
    }
} 