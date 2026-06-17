package org.example.competition.feign;

import org.example.common.utils.Result;
import org.example.competition.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

@FeignClient(value = "submission", configuration = FeignConfig.class, fallbackFactory = SubmissionFeignClientFallback.class)
public interface SubmissionFeignClient {

    /**
     * 使用 Map 传递参数，避免依赖 submission 模块的 Entity
     */
    @PostMapping("/submissions/submit")
    Result doSubmit(@RequestBody Map<String, Object> submissionData);

    @GetMapping("/submissions/inner/stats/{competitionId}")
    Result<Map<String, Object>> getStatsByCompetition(@PathVariable("competitionId") Integer competitionId);

    @GetMapping("/submissions/inner/export/{competitionId}")
    java.util.List<java.util.Map<String, Object>> exportSubmissions(@PathVariable("competitionId") Integer competitionId);
}