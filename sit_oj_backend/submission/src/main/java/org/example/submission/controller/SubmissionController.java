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

    @Autowired
    private org.example.submission.feign.ProblemFeignClient problemFeignClient;
    @Autowired
    private org.example.submission.service.SubmissionRateLimiter rateLimiter;

    private Integer requireUser(HttpServletRequest request) {
        try {
            Integer id = submissionService.getUserIdFromToken(request.getHeader("Authorization"));
            if (id != null && id > 0) return id;
        } catch (Exception ignored) { }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "请先登录");
    }

    private Submission cleanSubmission(Submission input, Integer userId, Integer competitionId) {
        org.example.common.security.SubmissionInput.validate(input.getProblemId(), input.getCodeContent());
        Submission clean = new Submission();
        clean.setUserId(userId);
        clean.setProblemId(input.getProblemId());
        clean.setCompetitionId(competitionId);
        clean.setCodeContent(input.getCodeContent());
        clean.setLanguage(org.example.common.security.SubmissionInput.language(input.getLanguage()));
        clean.setSubmissionTime(LocalDateTime.now());
        return clean;
    }

    /**
     * 提交题目
     * 返回 Result<Submission>，前端通过 res.data 拿到包含状态的结果
     */
    @PostMapping("/submit")
    public Result doSubmit(@RequestBody Submission submission, HttpServletRequest request) {
        org.example.common.security.AuditContext.submissionAttempt(request, submission.getUserId(),
                submission.getProblemId(), submission.getCompetitionId(), submission.getLanguage());
        Integer userId = requireUser(request);
        if (submission.getCompetitionId() != null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "请从比赛页面提交");
        Submission clean = cleanSubmission(submission, userId, null);
        Map<String, Object> problem = problemFeignClient.getProblemById(clean.getProblemId());
        String role = submissionService.getUserRoleFromToken(request.getHeader("Authorization"));
        if (problem == null || problem.get("problemId") == null ||
                (!"ADMIN".equals(role) && !Boolean.TRUE.equals(problem.get("isPublic")))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "题目不存在或无权访问");
        }
        rateLimiter.check(userId);
        Submission saved = submissionService.handleSubmission(clean);
        if (saved != null) org.example.common.security.AuditContext.submissionSaved(request, saved.getSubmissionId(), userId);
        return Result.success(saved);
    }

    @PostMapping("/inner/submit")
    public Result submitFromCompetition(@RequestBody Submission input) {
        var request = org.example.common.security.AuditContext.currentRequest();
        org.example.common.security.AuditContext.submissionAttempt(request, null,
                input.getProblemId(), input.getCompetitionId(), input.getLanguage());
        if (input.getUserId() == null || input.getUserId() <= 0 || input.getCompetitionId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "无效的比赛提交");
        }
        Submission clean = cleanSubmission(input, input.getUserId(), input.getCompetitionId());
        rateLimiter.check(clean.getUserId());
        Submission saved = submissionService.handleSubmission(clean);
        if (saved != null) org.example.common.security.AuditContext.submissionSaved(request, saved.getSubmissionId(), clean.getUserId());
        return Result.success(saved);
    }

    /**
     * 根据 ID 获取提交详情
     * 用于页面初始化获取结果或权限校验
     */
    @GetMapping("/{id}")
    public Result getSubmissionById(@PathVariable("id") Integer id, HttpServletRequest request) {
        Integer loginUserId = requireUser(request);
        String role = submissionService.getUserRoleFromToken(request.getHeader("Authorization"));
        Submission submission = submissionService.getById(id);
        if (submission == null) return Result.error("记录不存在");

        if("ADMIN".equals(role))
        {
            return Result.success(submission);
        }

        if (!loginUserId.equals(submission.getUserId())) {
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
            @RequestAttribute(value = "userId", required = false) Integer userId,
            @RequestAttribute(value = "userRole", required = false) String role) {

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
