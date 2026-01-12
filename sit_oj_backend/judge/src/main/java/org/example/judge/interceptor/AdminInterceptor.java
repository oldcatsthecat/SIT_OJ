package org.example.judge.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从 Request 属性中获取 LoginInterceptor 解析出的角色
        String role = (String) request.getAttribute("userRole");

        if ("ADMIN".equalsIgnoreCase(role)) {
            return true;
        }

        // 权限不足，返回 403
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\": 403, \"message\": \"权限不足，仅管理员可访问\"}");
        return false;
    }
}