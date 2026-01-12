package org.example.competition;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 比赛模块启动类
 */
@SpringBootApplication
@EnableDiscoveryClient // 开启 Nacos 服务注册与发现
@EnableFeignClients(basePackages = "org.example.competition.feign") // 开启 Feign 并扫描接口包
@MapperScan("org.example.competition.mapper") // 扫描 Mapper 接口
public class CompetitionApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompetitionApplication.class, args);
    }
}