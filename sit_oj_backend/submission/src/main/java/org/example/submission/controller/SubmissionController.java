package org.example.submission.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.common.utils.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.example.submission.entity.Submission;
import org.example.submission.service.SubmissionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    @Autowired
    private SubmissionService submissionService;

    /**
     * 提交题目
     * 返回 Result<Submission>，前端通过 res.data 拿到包含状态的结果
     */
    @PostMapping("/submit")
    public Result doSubmit(@RequestBody Submission submission, HttpServletRequest request) {
        // 1. 安全性检查：从 Token 获取真实用户 ID，防止前端伪造
        if (submission.getUserId() == null) {
            Integer loginUserId = submissionService.getUserIdFromToken(request.getHeader("Authorization"));
            if (loginUserId == null) return Result.error("登录过期");
            submission.setUserId(loginUserId);
        }

        LocalDateTime now = LocalDateTime.now();
        submission.setSubmissionTime(now);
        // 2. 调用 Service 执行判题逻辑
        Submission result = submissionService.handleSubmission(submission);


        return Result.success(result);
    }

    /**
     * 根据 ID 获取提交详情
     * 用于页面初始化获取结果或权限校验
     */
    @GetMapping("/{id}")
    public Result getSubmissionById(@PathVariable("id") Integer id, HttpServletRequest request) {
        Integer loginUserId = submissionService.getUserIdFromToken(request.getHeader("Authorization"));
        String role = submissionService.getUserRoleFromToken(request.getHeader("Authorization"));
        Submission submission = submissionService.getById(id);
        if (submission == null) return Result.error("记录不存在");

        if(role.equals("ADMIN"))
        {
            return Result.success(submission);
        }

        if (loginUserId != null && !submission.getUserId().equals(loginUserId)) {
            return Result.error("无权查看");
        }

        return Result.success(submission);
    }

    @GetMapping("/inner/{id}")
    public Submission getByIdInternal(@PathVariable("id") Integer id) {
        // 内部微服务调用，直接返回实体对象
        return submissionService.getById(id);
    }

    /**
     * 获取用户 ID 的私有方法
     * 请确保这里的解析逻辑与你登录时生成 Token 的逻辑一致
     */

    @GetMapping("/list")
    public Result getSubmissionList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer problemId,
            @RequestAttribute(value = "userId") Integer userId,
            @RequestAttribute(value = "userRole") String role) {

        // 直接调用 Service 层封装的方法
        IPage<Submission> result = submissionService.getSubmissionList(current, size, problemId,role,userId);
        return Result.success(result);
    }


    @GetMapping("/inner/stats/{competitionId}")
    public Result getCompetitionStats(@PathVariable Integer competitionId) {
        return Result.success(submissionService.getCompetitionStats(competitionId));
    }

    @GetMapping("/competition/list")
    public Result getCompetitionSubmissions(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "15") Integer size,
            @RequestParam Integer competitionId,
            HttpServletRequest request) {

        Integer userId = (Integer) request.getAttribute("userId");
        String role = (String) request.getAttribute("userRole");

        IPage<Submission> page = submissionService.getCompetitionSubmissions(current, size, competitionId, userId, role);

        return Result.success(page);
    }

    /**
     * 内部接口：导出比赛所有提交（用于 ICPC 滚榜数据生成）
     */
    @GetMapping("/inner/export/{competitionId}")
    public List<Map<String, Object>> exportCompetitionSubmissions(@PathVariable Integer competitionId) {
        return submissionService.getExportSubmissions(competitionId);
    }

}