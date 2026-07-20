package org.example.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.example.common.utils.Result;
import org.example.user.entity.User;
import org.example.user.service.UserService;
import org.example.user.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private JwtUtils jwtUtil;

    @Autowired
    private UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, Object> params) {
        try {
            userService.register(params);
            return Result.success(null);
        } catch (RuntimeException e) {
            // 返回具体的业务错误原因（如：验证码过期、用户名占用等）
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("系统异常，注册失败");
        }
    }

    /**
     * 用户登录
     * @return 返回 JWT Token
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody Map<String, String> loginRequest) {
        try {
            String username = loginRequest.get("username");
            String password = loginRequest.get("password");
            String token = userService.login(username, password);
            return Result.success(token);
        } catch (Exception e) {
            return Result.error("用户名或密码不存在！");
        }
    }

    @GetMapping("/inner/{id}")
    public User getUserByIdInternal(@PathVariable("id") Integer id) {
        // 直接使用 MyBatis-Plus 的 getById
        User user = userService.getById(id);
        if (user != null) {
            user.setPassword(null); // 安全起见，屏蔽密码
        }
        return user;
    }


    @GetMapping("/me")
    public Result<User> getMyInfo(@RequestHeader("Authorization") String token) {
        try {

            User user = userService.tokenToUser(token);
            if (user == null) {
                return Result.error("用户不存在");
            }
            user.setPassword(null);
            return Result.success(user);
        } catch (Exception e) {
            return Result.error("登录状态失效，请重新登录");
        }
    }

    /**
     * 刷新 JWT Token
     * 接受即将过期或刚过期的旧 Token（过期不超过 7 天），返回新 Token
     */
    @PostMapping("/refresh")
    public Result<String> refreshToken(@RequestHeader("Authorization") String token) {
        try {
            // 1. 解析旧 token（允许过期）
            Claims claims = jwtUtil.getClaimsAllowExpired(token);
            if (claims == null) {
                return Result.error("Token 无效");
            }

            // 2. 检查过期时间：过期超过 7 天则拒绝刷新
            Date expiration = claims.getExpiration();
            long sevenDaysMs = 7L * 24 * 60 * 60 * 1000;
            if (System.currentTimeMillis() - expiration.getTime() > sevenDaysMs) {
                return Result.error("登录已过期超过 7 天，请重新登录");
            }

            // 3. 根据 userId 查找用户并生成新 token
            Object userIdObj = claims.get("id");
            if (userIdObj == null) {
                return Result.error("Token 无效");
            }
            Integer userId = Integer.valueOf(userIdObj.toString());
            User user = userService.getById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }

            String newToken = jwtUtil.generateToken(user);
            return Result.success(newToken);
        } catch (Exception e) {
            return Result.error("Token 刷新失败，请重新登录");
        }
    }

    @PutMapping("/update")
    public Result<Void> updateUser(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        try {
            // 1. 从拦截器存入的 request 属性中获取真正登录的用户 ID
            Integer currentUserId = (Integer) request.getAttribute("userId");
            if (currentUserId == null) {
                return Result.error("未登录或 Token 已失效");
            }

            // 2. 提取参数
            String code = (String) params.get("code");

            // 3. 将 Map 转换为 User 对象（或者手动提取）
            User user = new User();
            user.setId(currentUserId); // 强制使用当前登录 ID，防止越权
            user.setEmail((String) params.get("email"));
            user.setPassword((String) params.get("password"));
            user.setRealName((String) params.get("realName"));
            user.setStudentId((String) params.get("studentId"));
            user.setGender((String) params.get("gender"));
            user.setAge((Integer) params.get("age"));

            // 4. 调用业务层逻辑（处理验证码校验和冲突删除）
            userService.updateUserInfo(user, code);

            return Result.success(); // 修改成功
        } catch (RuntimeException e) {
            // 捕获业务异常（如：验证码错误、邮箱已占用等）
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("服务器内部错误");
        }
    }

    @PostMapping("/sendCode")
    public Result<Void> sendCode(@RequestParam String email, @RequestParam(defaultValue = "register") String type) {
        userService.sendCode(email, type);
        return Result.success();
    }

    @PostMapping("/resetPassword")
    public Result<Void> resetPassword(@RequestBody Map<String, Object> params) {
        userService.resetPassword(params);
        return Result.success();
    }

}