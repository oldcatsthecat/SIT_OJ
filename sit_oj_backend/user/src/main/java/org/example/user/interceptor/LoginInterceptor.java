package org.example.user.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.user.utils.JwtUtils;
import org.example.user.entity.User;
import org.example.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");



        if (token != null && !token.isEmpty()) {
            try {
                if (token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }


                Integer userId = jwtUtils.getUserIdFromToken(token);
                String role = jwtUtils.getRoleFromToken(token);
                User user = userId == null ? null : userService.getById(userId);
                if (user == null || Boolean.TRUE.equals(user.getLocked())) {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"登录状态失效，请重新登录\"}");
                    return false;
                }

                //存入属性
                request.setAttribute("userId", userId);
                request.setAttribute("userRole", role);

                return true;
            } catch (Exception e) {
                // Token 解析失败时不抛异常，由后续逻辑判断
                System.err.println("Token 解析失败");
            }
        }
        return true;
    }
}
