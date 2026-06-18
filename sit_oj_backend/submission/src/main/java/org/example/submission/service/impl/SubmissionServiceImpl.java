package org.example.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.JudgeMessage;
import org.example.common.utils.Result;
import org.example.submission.entity.Submission;
import org.example.submission.feign.CompetitionFeignClient;
import org.example.submission.feign.JudgeFeignClient;
import org.example.submission.feign.ProblemFeignClient;
import org.example.submission.feign.UserFeignClient;
import org.example.submission.mapper.SubmissionMapper;
import org.example.submission.messaging.JudgeProducer;
import org.example.submission.service.SubmissionService;
import org.example.submission.utils.JwtUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

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
    private final JudgeProducer judgeProducer;

    @Override
    public Submission handleSubmission(Submission submission) {
        // 1. 落库，状态 = Pending
        saveInitialData(submission);

        // 2. 发送 RabbitMQ 消息到判题队列（替代原来的 @Async + Feign）
        JudgeMessage msg = JudgeMessage.builder()
                .submissionId(submission.getSubmissionId())
                .userId(submission.getUserId())
                .problemId(submission.getProblemId())
                .competitionId(submission.getCompetitionId())
                .codeContent(submission.getCodeContent())
                .language(submission.getLanguage())
                .build();
        judgeProducer.sendJudgeRequest(msg);

        // 3. 立即返回，前端通过轮询获取判题结果
        return submission;
    }

    @Transactional
    public void saveInitialData(Submission submission) {
        submission.setStatus("Pending");
        this.save(submission);
    }

    /**
     * 启动时 + 每30秒扫描一次 Pending 提交，补偿推送至 RabbitMQ
     */
    @PostConstruct
    public void rescanPendingOnStartup() {
        resendPendingSubmissions();
    }

    @Scheduled(fixedDelay = 30000)
    public void resendPendingSubmissions() {
        try {
            java.util.List<Submission> pending = this.lambdaQuery()
                    .eq(Submission::getStatus, "Pending")
                    .or()
                    .eq(Submission::getStatus, "JUDGING")
                    .list();
            for (Submission sub : pending) {
                // 只重试提交时间在 2 小时以内的
                if (sub.getSubmissionTime() != null) {
                    long minutes = java.time.Duration.between(sub.getSubmissionTime(), java.time.LocalDateTime.now()).toMinutes();
                    if (minutes > 120) continue;
                }
                try {
                    JudgeMessage msg = JudgeMessage.builder()
                            .submissionId(sub.getSubmissionId())
                            .userId(sub.getUserId())
                            .problemId(sub.getProblemId())
                            .competitionId(sub.getCompetitionId())
                            .codeContent(sub.getCodeContent())
                            .language(sub.getLanguage())
                            .build();
                    judgeProducer.sendJudgeRequest(msg);
                    log.info("补偿推送判题请求: submissionId={}", sub.getSubmissionId());
                } catch (Exception e) {
                    log.error("补偿推送失败: submissionId={}", sub.getSubmissionId(), e);
                }
            }
        } catch (Exception e) {
            log.error("扫描Pending提交失败", e);
        }
    }

    /**
     * 分页查询提交列表
     */
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
        QueryWrapper<Submission> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("competition_id", competitionId)
                .orderByDesc("submission_id");

        Page<Submission> pageParam = new Page<>(current, size);
        IPage<Submission> result = submissionMapper.selectPage(pageParam, queryWrapper);

        // 检查封榜状态
        boolean frozenFlag = false;
        try {
            Result frozenRes = competitionFeignClient.checkFrozen(competitionId);
            if (frozenRes != null && frozenRes.getCode() == 200) {
                frozenFlag = Boolean.TRUE.equals(frozenRes.getData());
            }
        } catch (Exception ignored) {}
        final boolean isFrozen = frozenFlag;

        boolean isAdmin = "ADMIN".equals(role);

        result.getRecords().forEach(sub -> {
            try {
                java.util.Map<String, Object> user = userFeignClient.getUserById(sub.getUserId());
                if (user != null && user.get("username") != null) {
                    sub.setUsername(user.get("username").toString());
                }

                boolean isOwner = userId != null && userId.equals(sub.getUserId());
                sub.setCanSeeDetail(isOwner || isAdmin);
                sub.setCodeContent(null);

                // 封榜期间：管理员和提交者本人可以看到真实结果，其他人看到 Frozen
                if (isFrozen && !isAdmin && !isOwner) {
                    sub.setStatus("Frozen");
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
