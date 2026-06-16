package org.example.submission.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * ProblemFeignClient 熔断降级
 */
@Slf4j
@Component
public class ProblemFeignClientFallback implements FallbackFactory<ProblemFeignClient> {

    @Override
    public ProblemFeignClient create(Throwable cause) {
        log.error("ProblemFeignClient 熔断触发: {}", cause.getMessage());
        return new ProblemFeignClient() {
            @Override
            public Map<String, Object> getProblemById(Integer id) {
                log.warn("ProblemFeignClient.getProblemById 降级: id={}", id);
                return Collections.singletonMap("problemName", "加载中...");
            }

            @Override
            public void updateProblemStats(Integer problemId, boolean isAccepted) {
                log.warn("ProblemFeignClient.updateProblemStats 降级: problemId={}", problemId);
                // 统计更新失败不阻塞主流程
            }
        };
    }
}
