package org.example.problem.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.common.utils.Result;
import org.example.problem.entity.Problem;
import org.example.problem.service.ProblemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/problems")
public class ProblemController {

    @Autowired
    private ProblemService problemService;

    @GetMapping("/list")
    public Result<IPage<Problem>> getProblemList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(value = "problemId", required = false) Integer problemId,
            @RequestAttribute(value = "userRole", required = false) String role,
            @RequestAttribute(value = "userId", required = false) Integer userId) {

        IPage<Problem> page = problemService.getAvailableProblemsForUser(current, size, problemId, userId, role);
        return Result.success(page);
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