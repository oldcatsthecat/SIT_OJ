package org.example.judge.controller;

import com.alibaba.nacos.api.model.v2.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.dto.JudgeResultItem;
import org.example.common.dto.JudgeResultResponse;
import org.example.common.dto.JudgeServerResponse;
import org.example.judge.feign.ProblemFeignClient;
import org.example.judge.feign.SubmissionFeignClient;
import org.example.judge.service.JudgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/judge")
public class JudgeController {



    @Autowired
    private JudgeService judgeService;

    @PostMapping("/doJudge")
    public JudgeResultResponse doJudge(@RequestParam Integer submissionId) {
        // 直接交给 Service 处理完整的判题流程
        return judgeService.processJudge(submissionId);
    }


}