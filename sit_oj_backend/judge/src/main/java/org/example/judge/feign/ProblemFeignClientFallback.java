package org.example.judge.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * ProblemFeignClient 熔断降级（judge 模块侧）
 */
@Slf4j
@Component
public class ProblemFeignClientFallback implements FallbackFactory<ProblemFeignClient> {

    @Override
    public ProblemFeignClient create(Throwable cause) {
        log.error("ProblemFeignClient 熔断触发 (judge): {}", cause.getMessage());
        return id -> {
            log.warn("ProblemFeignClient.getProblemById 降级: id={}", id);
            return Collections.emptyMap();
        };
    }
}
