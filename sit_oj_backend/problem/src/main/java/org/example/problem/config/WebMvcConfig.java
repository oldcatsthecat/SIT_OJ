package org.example.problem.config;

import org.example.problem.interceptor.AdminInterceptor;
import org.example.problem.interceptor.LoginInterceptor;
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
        // 1. 所有的题目接口都尝试解析身份
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/problems/**", "/api/admin/problems/**");

        // 2. 所有的管理接口强制校验 ADMIN 角色
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/problems/**");
    }
}