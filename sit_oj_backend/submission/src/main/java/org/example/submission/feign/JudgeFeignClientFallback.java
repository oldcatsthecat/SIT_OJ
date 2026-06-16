package org.example.submission.feign;

import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.JudgeResultResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * JudgeFeignClient 熔断降级工厂
 * 当 judge 服务不可用时返回 System Error
 */
@Slf4j
@Component
public class JudgeFeignClientFallback implements FallbackFactory<JudgeFeignClient> {

    @Override
    public JudgeFeignClient create(Throwable cause) {
        log.error("JudgeFeignClient 熔断触发: {}", cause.getMessage());
        return submissionId -> {
            log.warn("JudgeFeignClient 降级: submissionId={}, reason={}", submissionId, cause.getMessage());
            return JudgeResultResponse.builder()
                    .status("SE")
                    .errorMessage("判题服务暂时不可用，请稍后重试")
                    .build();
        };
    }
}
