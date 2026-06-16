package org.example.submission.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "problem", fallbackFactory = ProblemFeignClientFallback.class)
public interface ProblemFeignClient {

    @GetMapping("/problems/{id}")
    java.util.Map<String, Object> getProblemById(@PathVariable("id") Integer id);

    // 新增：通知题目服务更新计数
    @PutMapping("/problems/inner/update-stats")
    void updateProblemStats(@RequestParam("problemId") Integer problemId,
                            @RequestParam("isAccepted") boolean isAccepted);
}