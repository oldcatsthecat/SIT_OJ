package org.example.competition.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.competition.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtUtils jwtUtils; // 确保这是 competition 模块本地那个不依赖 User 实体的工具类

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String token = request.getHeader("Authorization");
        if (token != null && !token.isEmpty()) {
            try {
                // 解析 ID 和 Role
                Integer userId = jwtUtils.getUserIdFromToken(token);
                String role = jwtUtils.getRoleFromToken(token); // 需确保 JwtUtils 有此方法

                if (userId != null) {
                    request.setAttribute("userId", userId);
                    request.setAttribute("userRole", role); // 存入角色供 AdminInterceptor 使用
                }
            } catch (Exception e) {
                System.err.println("Competition模块Token解析失败: " + e.getMessage());
            }
        }
        return true;
    }
}