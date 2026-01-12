package org.example.judge.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

// value 必须对应 problem 模块 yml 中的 spring.application.name
@FeignClient(value = "problem")
public interface ProblemFeignClient {

    @GetMapping("/problems/{id}")
    Map<String, Object> getProblemById(@PathVariable("id") Integer id);
}