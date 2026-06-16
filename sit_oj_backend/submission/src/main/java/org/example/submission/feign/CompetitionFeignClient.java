package org.example.submission.feign;

import org.example.common.utils.Result; // 假设你的 Result 类在这个包下
import org.springframework.cloud.openfeign.FeignClient;
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
     * 对应 Competition 模块 Controller 中的 internal/updateStats 接口
     */
    @PostMapping("/competitions/internal/updateStats")
    Result updateRankStats(@RequestParam("userId") Integer userId,
                           @RequestParam("competitionId") Integer competitionId,
                           @RequestParam("problemId") Integer problemId,
                           @RequestParam("status") String status);
}