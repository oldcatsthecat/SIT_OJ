package org.example.problem.controller;

import org.example.common.utils.Result;
import org.example.problem.entity.Problem;
import org.example.problem.service.ProblemService;
import org.example.problem.service.TestCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/admin/problems")
public class AdminProblemController {

    @Autowired
    private TestCaseService testCaseService; // Spring 会自动注入 TestCaseServiceImpl

    @Autowired
    private ProblemService problemService;

    /**
     * 保存或更新题目
     * 如果 Problem 对象包含 problemId，则执行更新；否则执行新增。
     */
    @PostMapping("/save")
    public Result<Void> saveProblem(@RequestBody Problem problem) {
        // MyBatis-Plus 的 saveOrUpdate 会根据 ID 自动判断
        boolean success = problemService.saveOrUpdate(problem);
        return success ? Result.success() : Result.error("保存失败");
    }

    /**
     * 删除题目
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteProblem(@PathVariable Integer id) {
        boolean success = problemService.removeById(id);
        // 注意：实际开发中，删除题目还应同步删除本地的测试用例文件夹
        return success ? Result.success() : Result.error("删除失败");
    }

    /**
     * 获取单个题目详情（用于编辑回显）
     */
    @GetMapping("/{id}")
    public Result<Problem> getProblem(@PathVariable Integer id) {
        Problem problem = problemService.getById(id);
        return Result.success(problem);
    }

    @PostMapping("/testcase/upload")
    public Result upload(@RequestParam("file") MultipartFile file, String problemId) {
        try {
            testCaseService.processAndSync(file, problemId);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}