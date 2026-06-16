package org.example.competition.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * ProblemFeignClient 熔断降级（competition 模块侧）
 */
@Slf4j
@Component
public class ProblemFeignClientFallback implements FallbackFactory<ProblemFeignClient> {

    @Override
    public ProblemFeignClient create(Throwable cause) {
        log.error("ProblemFeignClient 熔断触发 (competition): {}", cause.getMessage());
        return new ProblemFeignClient() {
            @Override
            public Object getProblemById(Integer id) {
                log.warn("ProblemFeignClient.getProblemById 降级: id={}", id);
                return Collections.emptyMap();
            }

            @Override
            public List<Object> getProblemList(Integer problemId) {
                log.warn("ProblemFeignClient.getProblemList 降级");
                return Collections.emptyList();
            }
        };
    }
}
