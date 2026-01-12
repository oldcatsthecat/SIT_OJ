package org.example.user.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.example.common.utils.Result;
import org.example.user.entity.User;
import org.example.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users") // 统一加上 admin 前缀，明确这是管理端接口
public class AdminUserController {

    @Autowired
    private UserService userService;


    /**
     * 获取所有用户列表
     * URL: GET /api/admin/users/list
     */
    @GetMapping("/list")
    public Result<List<User>> getAllUsers(@RequestHeader("Authorization") String token) {
        // 使用 queryChain 方便进行条件过滤
        List<User> list = userService.lambdaQuery()
                .notLikeRight(User::getUsername, "tmp_") // 排除 tmp_ 开头的用户
                .list();

        list.forEach(u -> u.setPassword(null));
        return Result.success(list);
    }

    /**
     * 删除用户
     * URL: DELETE /api/admin/users/{id}
     * 规范：RESTful 风格中使用 DELETE 方法，URL 路径通常只包含资源名和ID
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Integer id, @RequestHeader("Authorization") String token) {
        userService.removeById(id);
        return Result.success(null);
    }

    /**
     * 管理员更新用户信息（如修改角色）
     * URL: PUT /api/admin/users/update
     */
    @PutMapping("/update")
    public Result<Void> updateUser(@RequestBody User user, @RequestHeader("Authorization") String token) {
        userService.updateById(user);
        return Result.success(null);
    }



}
