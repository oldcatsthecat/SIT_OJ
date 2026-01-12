package org.example.problem.controller;

import org.example.problem.entity.Problem;
import org.example.problem.service.ProblemService;
import org.example.problem.service.TestCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.example.common.utils.Result;
import java.util.List;

@RestController
@RequestMapping("/problems")
public class ProblemController {

    @Autowired
    private ProblemService problemService; // 注入 Service

    @GetMapping("/list")
    public List<Problem> getProblemList(
            @RequestParam(value = "problemId", required = false) Integer problemId,
            @RequestAttribute(value = "userRole", required = false) String role,
            @RequestAttribute(value = "userId", required = false) Integer userId){ // 假设从 Header 获取角色

        List<Problem> problems = problemService.getAvailableProblemsForUser(problemId, userId, role);
        return problems;
    }

    @GetMapping("/{id}")
    public Problem getProblemById(@PathVariable Integer id) {
        // 使用 service.getById() 获取单条数据
        return problemService.getById(id);
    }

    @PutMapping("/inner/update-stats")
    public void updateProblemStats(@RequestParam Integer problemId, @RequestParam boolean isAccepted) {
        problemService.updateStats(problemId, isAccepted);
    }


}