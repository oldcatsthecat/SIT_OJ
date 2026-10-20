package org.example.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.utils.Result;
import org.example.submission.entity.Submission;
import org.example.submission.feign.CompetitionFeignClient;
import org.example.submission.feign.JudgeFeignClient;
import org.example.submission.feign.ProblemFeignClient;
import org.example.submission.feign.UserFeignClient;
import org.example.submission.mapper.SubmissionMapper;
import org.example.submission.service.SubmissionService;
import org.example.submission.utils.JwtUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Submission 核心服务实现
 * 判题流程改为 RabbitMQ 异步解耦：
 *   submission → RabbitMQ (judge.request.queue) → judge → RabbitMQ (judge.result.queue) → submission
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final ProblemFeignClient problemFeignClient;
    private final JudgeFeignClient judgeFeignClient;
    private final UserFeignClient userFeignClient;
    private final JwtUtils jwtUtils;
    private final SubmissionMapper submissionMapper;
    private final CompetitionFeignClient competitionFeignClient;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public Submission handleSubmission(Submission submission) {
        // 1. 落库，状态 = Pending
        saveInitialData(submission);

        // 与提交记录在同一数据库事务中保存待发送任务；后台发布器负责重试。
        jdbcTemplate.update("INSERT INTO judge_outbox (submission_id) VALUES (?)", submission.getSubmissionId());

        // 3. 立即返回，前端通过轮询获取判题结果
        return submission;
    }

    @Transactional
    public void saveInitialData(Submission submission) {
        submission.setStatus("Pending");
        this.save(submission);
    }

    @Override
    public IPage<Submission> getSubmissionList(Integer current, Integer size, Integer problemId, String role, Integer currentUserId) {
        Page<Submission> pageParam = new Page<>(current, size);
        IPage<Submission> submissionPage;

        if ("ADMIN".equals(role)) {
            LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(problemId != null, Submission::getProblemId, problemId);
            wrapper.orderByDesc(Submission::getSubmissionId);
            submissionPage = this.page(pageParam, wrapper);
        } else {
            submissionPage = baseMapper.selectPublicSubmissions(pageParam, problemId);
        }

        submissionPage.getRecords().forEach(submission -> {
            submission.setCodeContent(null);
            submission.setJudgeInfo(null);
            submission.setErrorMessage(null);
            try {
                java.util.Map<String, Object> problem = problemFeignClient.getProblemById(submission.getProblemId());
                if (problem != null && problem.get("problemName") != null) {
                    submission.setProblemName(problem.get("problemName").toString());
                }

                java.util.Map<String, Object> user = userFeignClient.getUserById(submission.getUserId());
                if (user != null && user.get("username") != null) {
                    submission.setUsername(user.get("username").toString());
                }

                boolean isOwner = currentUserId != null && currentUserId.equals(submission.getUserId());
                boolean isAdmin = "ADMIN".equals(role);
                submission.setCanSeeDetail(isAdmin || isOwner);
                submission.setCodeContent(null);
            } catch (Exception e) {
                submission.setProblemName("未知题目(ID:" + submission.getProblemId() + ")");
                submission.setUsername("未知用户");
            }
        });

        return submissionPage;
    }

    @Override
    public Map<String, Object> getCompetitionStats(Integer competitionId) {
        List<Map<String, Object>> statsList = baseMapper.getCompetitionStats(competitionId);
        Map<String, Object> resultMap = new HashMap<>();

        if (statsList != null) {
            for (Map<String, Object> row : statsList) {
                String problemId = row.get("problem_id").toString();
                Map<String, Object> statData = new HashMap<>();
                statData.put("problemId", row.get("problem_id"));
                statData.put("acceptedNum", ((Number) row.get("acceptedNum")).intValue());
                statData.put("totalNum", ((Number) row.get("totalNum")).intValue());
                resultMap.put(problemId, statData);
            }
        }

        return resultMap;
    }

    @Override
    public Integer getUserIdFromToken(String token) {
        return jwtUtils.getUserIdFromToken(token);
    }

    @Override
    public String getUserRoleFromToken(String token) {
        return jwtUtils.getRoleFromToken(token);
    }

    @Override
    public IPage<Submission> getCompetitionSubmissions(Integer current, Integer size, Integer competitionId, Integer userId, String role) {
        return getCompetitionSubmissions(current, size, competitionId, userId, role, null, null, null);
    }

    @Override
    public IPage<Submission> getCompetitionSubmissions(Integer current, Integer size, Integer competitionId, Integer userId,
                                                        String role, String username, Integer problemId, String status) {
        if (!"ADMIN".equals(role)) {
            Result<Boolean> access = userId == null ? null : competitionFeignClient.checkAccess(competitionId, userId);
            if (access == null || access.getCode() != 200 || !Boolean.TRUE.equals(access.getData())) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "无权查看比赛提交");
            }
        }
        // 检查封榜状态
        boolean frozenFlag = true;
        try {
            Result frozenRes = competitionFeignClient.checkFrozen(competitionId);
            if (frozenRes != null && frozenRes.getCode() == 200) {
                frozenFlag = Boolean.TRUE.equals(frozenRes.getData());
            }
        } catch (Exception ignored) {}
        final boolean isFrozen = frozenFlag;

        boolean isAdmin = "ADMIN".equals(role);
        QueryWrapper<Submission> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("competition_id", competitionId);
        if (problemId != null) queryWrapper.eq("problem_id", problemId);
        if (username != null && !username.isBlank()) {
            String search = username.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            queryWrapper.apply("EXISTS (SELECT 1 FROM users u WHERE u.id = user_id " +
                    "AND u.username LIKE CONCAT('%', {0}, '%'))", search);
        }
        if (status != null && !status.isBlank()) {
            String selectedStatus = status.trim();
            if (isFrozen && !isAdmin) {
                if ("Frozen".equals(selectedStatus)) {
                    queryWrapper.ne("user_id", userId);
                } else {
                    queryWrapper.eq("user_id", userId).eq("status", selectedStatus);
                }
            } else {
                queryWrapper.eq("status", selectedStatus);
            }
        }
        queryWrapper.orderByDesc("submission_id");
        Page<Submission> pageParam = new Page<>(current, size);
        IPage<Submission> result = submissionMapper.selectPage(pageParam, queryWrapper);

        result.getRecords().forEach(sub -> {
            sub.setCodeContent(null);
            sub.setJudgeInfo(null);
            sub.setErrorMessage(null);
            boolean isOwner = userId != null && userId.equals(sub.getUserId());
            sub.setCanSeeDetail(isOwner || isAdmin);
            if (isFrozen && !isAdmin && !isOwner) {
                sub.setStatus("Frozen");
            }
            try {
                java.util.Map<String, Object> user = userFeignClient.getUserById(sub.getUserId());
                if (user != null && user.get("username") != null) {
                    sub.setUsername(user.get("username").toString());
                }
            } catch (Exception e) {
                sub.setUsername("未知用户");
                sub.setProblemName("题目 ID: " + sub.getProblemId());
            }
        });

        return result;
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> getExportSubmissions(Integer competitionId) {
        QueryWrapper<Submission> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("competition_id", competitionId)
                .orderByAsc("submission_id");

        java.util.List<Submission> subs = submissionMapper.selectList(queryWrapper);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();

        for (Submission sub : subs) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("submissionId", sub.getSubmissionId());
            map.put("userId", sub.getUserId());
            map.put("problemId", sub.getProblemId());
            map.put("status", sub.getStatus());
            map.put("submissionTime", sub.getSubmissionTime() != null
                    ? sub.getSubmissionTime().toString() : null);
            map.put("language", sub.getLanguage());
            map.put("timeCost", sub.getTimeCost());
            result.add(map);
        }

        return result;
    }
}
