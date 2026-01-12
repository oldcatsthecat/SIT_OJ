package org.example.submission.feign;

import org.example.common.dto.JudgeResultResponse; // 确保引入了该 DTO
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "judge")
public interface JudgeFeignClient {

    // 返回值改为对象，Feign 会自动将 Judge 服务返回的 JSON 转为该对象
    @PostMapping("/judge/doJudge")
    JudgeResultResponse doJudge(@RequestParam("submissionId") Integer submissionId);
}