package org.example.submission.feign;

import lombok.extern.slf4j.Slf4j;
import org.example.common.utils.Result;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * CompetitionFeignClient 熔断降级
 */
@Slf4j
@Component
public class CompetitionFeignClientFallback implements FallbackFactory<CompetitionFeignClient> {

    @Override
    public CompetitionFeignClient create(Throwable cause) {
        log.error("CompetitionFeignClient 熔断触发: {}", cause.getMessage());
        return new CompetitionFeignClient() {
            @Override
            public Result updateRankStats(Integer userId, Integer competitionId,
                                          Integer problemId, String status) {
                log.warn("CompetitionFeignClient 降级: 排行榜更新延迟, competition={}, user={}",
                        competitionId, userId);
                return Result.error("比赛服务暂时不可用，排行榜将延迟更新");
            }

            @Override
            public Result checkFrozen(Integer id) {
                log.warn("CompetitionFeignClient.checkFrozen 降级: id={}", id);
                return Result.success(false);
            }
        };
    }
}
