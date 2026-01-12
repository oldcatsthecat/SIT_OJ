package org.example.competition.config;

import org.example.competition.interceptor.LoginInterceptor;
import org.example.competition.interceptor.AdminInterceptor;
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
        // 1. 登录拦截器：去掉对详情页的排除
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/competitions/**", "/api/admin/competitions/**");

        // 2. 管理员拦截器保持不变
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/competitions/**");
    }
}