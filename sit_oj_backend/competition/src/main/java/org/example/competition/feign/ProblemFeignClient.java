package org.example.competition.feign;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "problem", fallbackFactory = ProblemFeignClientFallback.class)
public interface ProblemFeignClient {

    @GetMapping("/problems/{id}")
    Object getProblemById(@PathVariable("id") Integer id);

    @GetMapping("/problems/list")
    List<Object> getProblemList(@RequestParam(value = "problemId", required = false) Integer problemId);


}