package org.example.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.common.utils.Result;
import org.example.user.entity.User;
import org.example.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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

    public record LockRequest(Boolean locked) {}

    @PutMapping("/{id}/locked")
    public Result<Void> setLocked(@PathVariable Integer id, @RequestBody LockRequest body,
                                  HttpServletRequest request) {
        if (body == null || body.locked() == null) return Result.error("请指定禁用状态");
        User target = userService.getById(id);
        if (target == null) return Result.error("用户不存在");
        if ("root".equals(target.getUsername())) return Result.error("不能禁用 root 账户");
        if (Boolean.TRUE.equals(body.locked()) && id.equals(request.getAttribute("userId"))) {
            return Result.error("不能禁用当前登录的账户");
        }
        userService.lambdaUpdate().eq(User::getId, id).set(User::getLocked, body.locked()).update();
        return Result.success(null);
    }

    /**
     * 管理员更新用户信息（如修改角色）
     * URL: PUT /admin/users/update
     */
    @PutMapping("/update")
    public Result<Void> updateUser(@RequestBody User user, @RequestHeader("Authorization") String token) {
        if (user.getId() == null || userService.getById(user.getId()) == null) return Result.error("用户不存在");
        if (user.getRole() != null && !java.util.Set.of("USER", "ADMIN").contains(user.getRole())) return Result.error("角色无效");
        User update = new User();
        update.setId(user.getId());
        update.setRole(user.getRole());
        update.setRealName(user.getRealName());
        update.setStudentId(user.getStudentId());
        update.setGender(user.getGender());
        update.setAge(user.getAge());
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            if (user.getPassword().length() < 8 || user.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                return Result.error("密码至少8位且UTF-8长度不超过72字节");
            update.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userService.updateById(update);
        return Result.success(null);
    }



}
