package org.example.judge.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * SubmissionFeignClient 熔断降级（judge 模块侧）
 */
@Slf4j
@Component
public class SubmissionFeignClientFallback implements FallbackFactory<SubmissionFeignClient> {

    @Override
    public SubmissionFeignClient create(Throwable cause) {
        log.error("SubmissionFeignClient 熔断触发 (judge): {}", cause.getMessage());
        return id -> {
            log.warn("SubmissionFeignClient.getSubmissionById 降级: id={}", id);
            return Collections.singletonMap("error", "提交记录获取失败");
        };
    }
}
