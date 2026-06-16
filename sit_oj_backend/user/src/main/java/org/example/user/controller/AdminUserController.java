package org.example.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.common.utils.Result;
import org.example.user.entity.User;
import org.example.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    /**
     * 分页获取用户列表
     * URL: GET /admin/users/list?current=1&size=20
     */
    @GetMapping("/list")
    public Result<IPage<User>> getAllUsers(@RequestHeader("Authorization") String token,
                                           @RequestParam(defaultValue = "1") Integer current,
                                           @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(userService.getUserPage(current, size));
    }

    /**
     * 删除用户
     * URL: DELETE /admin/users/{id}
     * 规范：RESTful 风格中使用 DELETE 方法，URL 路径通常只包含资源名和ID
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Integer id, @RequestHeader("Authorization") String token) {
        userService.removeById(id);
        return Result.success(null);
    }

    /**
     * 管理员更新用户信息（如修改角色）
     * URL: PUT /admin/users/update
     */
    @PutMapping("/update")
    public Result<Void> updateUser(@RequestBody User user, @RequestHeader("Authorization") String token) {
        userService.updateById(user);
        return Result.success(null);
    }



}
