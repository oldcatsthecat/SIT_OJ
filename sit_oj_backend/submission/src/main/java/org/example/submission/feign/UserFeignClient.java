package org.example.submission.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user", fallbackFactory = UserFeignClientFallback.class)
public interface UserFeignClient {


    @GetMapping("/users/inner/{id}")
    java.util.Map<String, Object> getUserById(@PathVariable("id") Integer id);
}