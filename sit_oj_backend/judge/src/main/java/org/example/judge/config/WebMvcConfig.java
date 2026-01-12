package org.example.judge.config;

import org.example.judge.interceptor.AdminInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 管理员权限拦截（仅限管理员路径）
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**"); // 确保你的 AdminUserController 路径匹配
    }
}
