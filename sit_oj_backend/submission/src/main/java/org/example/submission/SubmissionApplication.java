package org.example.submission;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients // 开启 Feign 客户端功能
@MapperScan("org.example.submission.mapper")
public class SubmissionApplication {
    public static void main(String[] args) {
        SpringApplication.run(SubmissionApplication.class, args);
    }
}