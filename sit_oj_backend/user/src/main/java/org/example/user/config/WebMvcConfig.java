package org.example.user.config;

import org.example.user.interceptor.AdminInterceptor;
import org.example.user.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;
    @Autowired
    private AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一层：通用登录拦截（所有需要登录的接口）
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/users/**","/admin/**")
                .excludePathPatterns("/users/login", "/users/register");

        // 第二层：管理员权限拦截（仅限管理员路径）
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**"); // 确保你的 AdminUserController 路径匹配
    }
}
