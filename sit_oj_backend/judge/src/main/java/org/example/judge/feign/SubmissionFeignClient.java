package org.example.judge.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;


@FeignClient(value = "submission")
public interface SubmissionFeignClient {

    // 路径保持 /submissions/{id}，因为这是我们在 SubmissionController 里定义的映射
    @GetMapping("/submissions/inner/{id}")
    Map<String, Object> getSubmissionById(@PathVariable("id") Integer id);
}