package org.example.competition.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 用户服务 Feign 客户端
 * 用于获取排行榜所需的用户名和真名
 */
@FeignClient(value = "user", fallbackFactory = UserFeignClientFallback.class)
public interface UserFeignClient {

    @GetMapping("/users/inner/{id}")
    Map<String, Object> getUserById(@PathVariable("id") Integer id);
}
