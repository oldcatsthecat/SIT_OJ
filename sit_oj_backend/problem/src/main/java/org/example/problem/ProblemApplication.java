package org.example.problem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("org.example.problem.mapper") // 必须指向你的 mapper 包路径
@ComponentScan(basePackages = {
        "org.example.common",
        "org.example.problem"
})
public class ProblemApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProblemApplication.class, args);
    }
}