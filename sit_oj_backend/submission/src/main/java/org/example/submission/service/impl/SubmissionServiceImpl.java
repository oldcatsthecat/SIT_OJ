package org.example.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.common.dto.JudgeResultResponse;
import org.example.submission.entity.Submission;
import org.example.submission.feign.CompetitionFeignClient;
import org.example.submission.feign.JudgeFeignClient;
import org.example.submission.feign.ProblemFeignClient;
import org.example.submission.feign.UserFeignClient;
import org.example.submission.mapper.SubmissionMapper;
import org.example.submission.service.SubmissionService;
import org.example.submission.utils.JwtUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    @Autowired
    private ProblemFeignClient problemFeignClient;

    @Autowired
    private JudgeFeignClient judgeFeignClient;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private SubmissionMapper submissionMapper;

    @Autowired
    private ObjectProvider<SubmissionServiceImpl> selfProvider;

    @Autowired
    private CompetitionFeignClient competitionFeignClient;

    @Override
    public Submission handleSubmission(Submission submission) {
        // 1. 初始保存 (必须先执行)
        this.saveInitialData(submission);

        // 2. 【关键修改】通过代理对象调用异步方法
        selfProvider.getIfAvailable().executeJudgeAsync(submission);


        // 3. 立即返回，此时异步方法已经在线程池里跑了
        return submission;
    }

    @Async("judgeTaskExecutor")
    public void executeJudgeAsync(Submission submission) {
        try {
            // 2. 调用判题机 (Feign)
            JudgeResultResponse result = judgeFeignClient.doJudge(submission.getSubmissionId());

            // 3. 回填结果并更新数据库
            submission.setStatus(result.getStatus());
            submission.setTimeCost(result.getTimeCost());
            submission.setMemoryCost(result.getMemoryCost());
            submission.setJudgeInfo(result.getJudgeInfo());
            submission.setErrorMessage(result.getErrorMessage());
            this.updateById(submission);

            // 3. 【关键：结果回调】如果关联了比赛，通知 Competition 模块
            if (submission.getCompetitionId() != null) {
                // 这里的 competitionFeignClient 是 Submission 模块里定义的
                competitionFeignClient.updateRankStats(
                        submission.getUserId(),
                        submission.getCompetitionId(),
                        submission.getProblemId(),
                        result.getStatus()
                );
            }

            //更新题目统计
            boolean isAccepted = "AC".equals(result.getStatus());
            problemFeignClient.updateProblemStats(submission.getProblemId(), isAccepted);

        } catch (Exception e) {
            submission.setStatus("SE");
            submission.setErrorMessage("异步测评出错: " + e.getMessage());
            this.updateById(submission);
        }
    }

    @Transactional
    public void saveInitialData(Submission submission) {
        submission.setStatus("Pending");
        this.save(submission);
    }


    /**
     * 实现分页查询逻辑
     */
    @Override
    public IPage<Submission> getSubmissionList(Integer current, Integer size, Integer problemId, String role , Integer currentUserId) {
        Page<Submission> pageParam = new Page<>(current, size);
        IPage<Submission> submissionPage;

        // 1. 根据权限执行不同的查询逻辑
        if ("ADMIN".equals(role)) {
            // 管理员：保留原有逻辑，查全部
            LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(problemId != null, Submission::getProblemId, problemId);
            wrapper.orderByDesc(Submission::getSubmissionId);
            submissionPage = this.page(pageParam, wrapper);
        } else {
            // 普通用户：调用 Mapper 中的关联查询，过滤掉隐藏题目
            submissionPage = baseMapper.selectPublicSubmissions(pageParam, problemId);
        }

        // 3. 补全非数据库字段 (problemName 和 username)
        submissionPage.getRecords().forEach(submission -> {
            try {
                // 补全题目名称 (对应 Problem 实体类中的 problemName)
                java.util.Map<String, Object> problem = problemFeignClient.getProblemById(submission.getProblemId());
                if (problem != null && problem.get("problemName") != null) {
                    submission.setProblemName(problem.get("problemName").toString());
                }

                // 补全用户名 (对应 User 实体类中的 username)
                java.util.Map<String, Object> user = userFeignClient.getUserById(submission.getUserId());
                if (user != null && user.get("username") != null) {
                    submission.setUsername(user.get("username").toString());
                }
                //提交列表不需要代码内容
                boolean isOwner = currentUserId != null && currentUserId.equals(submission.getUserId());
                boolean isAdmin = "ADMIN".equals(role);
                submission.setCanSeeDetail(isAdmin || isOwner);

                submission.setCodeContent(null);
            } catch (Exception e) {
                // 避免单个服务异常导致整个列表挂掉
                submission.setProblemName("未知题目(ID:" + submission.getProblemId() + ")");
                submission.setUsername("未知用户");
            }
        });

        return submissionPage;
    }

    @Override
    public Map<String, Object> getCompetitionStats(Integer competitionId) {
        // 1. 调用 Mapper 获取聚合结果
        List<Map<String, Object>> statsList = baseMapper.getCompetitionStats(competitionId);

        // 2. 构造返回结果 Map
        Map<String, Object> resultMap = new HashMap<>();

        if (statsList != null) {
            for (Map<String, Object> row : statsList) {
                // 获取 problem_id 作为 Key
                String problemId = row.get("problem_id").toString();

                // 转换数据类型（MyBatis返回聚合函数结果通常为 Long）
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
    public Integer getUserIdFromToken(String token)
    {
        return jwtUtils.getUserIdFromToken(token);
    }

    @Override
    public String getUserRoleFromToken(String token)
    {
        return jwtUtils.getRoleFromToken(token);
    }

    @Override
    public IPage<Submission> getCompetitionSubmissions(Integer current, Integer size, Integer competitionId, Integer userId, String role) {
        // 1. 基本分页查询
        QueryWrapper<Submission> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("competition_id", competitionId)
                .orderByDesc("submission_id");

        Page<Submission> pageParam = new Page<>(current, size);
        IPage<Submission> result = submissionMapper.selectPage(pageParam, queryWrapper);

        // 2. 补全非数据库字段并处理逻辑
        result.getRecords().forEach(sub -> {
            try {
                // A. 补全用户名
                java.util.Map<String, Object> user = userFeignClient.getUserById(sub.getUserId());
                if (user != null && user.get("username") != null) {
                    sub.setUsername(user.get("username").toString());
                }

                // C. 权限逻辑判断
                boolean isOwner = userId != null && userId.equals(sub.getUserId());
                boolean isAdmin = "ADMIN".equals(role);
                sub.setCanSeeDetail(isOwner || isAdmin);

                // D. 代码脱敏 (列表页不返回源代码，节省流量且安全)
                sub.setCodeContent(null);

            } catch (Exception e) {
                // 容错处理：防止因为某个 Feign 调用失败导致整个列表查不出来
                sub.setUsername("未知用户");
                sub.setProblemName("题目 ID: " + sub.getProblemId());
            }
        });

        return result;
    }
}