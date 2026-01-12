package org.example.submission.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user") // 这里的 name 必须是你 user 模块在注册中心的服务名
public interface UserFeignClient {


    @GetMapping("/api/users/inner/{id}")
    java.util.Map<String, Object> getUserById(@PathVariable("id") Integer id);
}