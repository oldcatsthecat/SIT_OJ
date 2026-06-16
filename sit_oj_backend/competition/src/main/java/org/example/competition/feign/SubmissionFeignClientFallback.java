package org.example.competition.feign;

import lombok.extern.slf4j.Slf4j;
import org.example.common.utils.Result;
import org.example.competition.config.FeignConfig;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SubmissionFeignClient 熔断降级（competition 模块侧）
 */
@Slf4j
@Component
public class SubmissionFeignClientFallback implements FallbackFactory<SubmissionFeignClient> {

    @Override
    public SubmissionFeignClient create(Throwable cause) {
        log.error("SubmissionFeignClient 熔断触发 (competition): {}", cause.getMessage());
        return new SubmissionFeignClient() {
            @Override
            public Result doSubmit(Map<String, Object> submissionData) {
                log.warn("SubmissionFeignClient.doSubmit 降级");
                return Result.error("判题服务暂时不可用，请稍后重试");
            }

            @Override
            public Result<Map<String, Object>> getStatsByCompetition(Integer competitionId) {
                log.warn("SubmissionFeignClient.getStatsByCompetition 降级: competitionId={}", competitionId);
                return Result.error("统计数据暂不可用");
            }
        };
    }
}
