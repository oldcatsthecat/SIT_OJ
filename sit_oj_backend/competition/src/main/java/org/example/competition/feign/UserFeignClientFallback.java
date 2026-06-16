package org.example.competition.feign;

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
        log.error("UserFeignClient 熔断触发 (competition): {}", cause.getMessage());
        return new UserFeignClient() {
            @Override
            public Map<String, Object> getUserById(Integer id) {
                log.warn("UserFeignClient.getUserById 降级: id={}", id);
                return Collections.emptyMap();
            }
        };
    }
}
