package org.example.submission.feign;

import org.example.common.utils.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 远程调用 Competition 比赛模块的客户端
 * name: 对应 competition 微服务在注册中心（如 Nacos）的服务名
 */
@FeignClient(name = "competition", fallbackFactory = CompetitionFeignClientFallback.class)
public interface CompetitionFeignClient {

    /**
     * 更新比赛的排名统计数据
     */
    @PostMapping("/competitions/internal/updateStats")
    Result updateRankStats(@RequestParam("userId") Integer userId,
                           @RequestParam("competitionId") Integer competitionId,
                           @RequestParam("problemId") Integer problemId,
                           @RequestParam("status") String status,
                           @RequestParam("submissionTime") String submissionTime);

    /**
     * 检查比赛是否处于封榜期
     */
    @GetMapping("/competitions/inner/{id}/frozen")
    Result checkFrozen(@PathVariable("id") Integer id);
}