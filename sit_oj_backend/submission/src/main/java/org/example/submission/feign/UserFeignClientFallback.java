package org.example.submission.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * UserFeignClient 熔断降级
 */
@Slf4j
@Component
public class UserFeignClientFallback implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("UserFeignClient 熔断触发: {}", cause.getMessage());
        return id -> {
            log.warn("UserFeignClient.getUserById 降级: id={}", id);
            return Collections.singletonMap("username", "未知用户");
        };
    }
}
