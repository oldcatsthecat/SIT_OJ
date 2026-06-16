package org.example.competition.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.common.utils.Result;
import org.example.competition.entity.*;
import org.example.competition.feign.ProblemFeignClient;
import org.example.competition.feign.SubmissionFeignClient;
import org.example.competition.feign.UserFeignClient;
import org.example.competition.mapper.*;
import org.example.competition.service.CompetitionService;
import org.example.competition.service.ParticipationService;
import org.example.competition.service.RankingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CompetitionServiceImpl extends ServiceImpl<CompetitionMapper, Competition> implements CompetitionService {

    @Autowired
    private ParticipationMapper participationMapper;
    @Autowired
    private CompetitionProblemMapper cpMapper;
    @Autowired
    private CompetitionSubmissionStatsMapper statsMapper;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private SubmissionFeignClient submissionClient;

    @Autowired
    private ProblemFeignClient problemFeignClient;

    @Autowired
    private SubmissionFeignClient submissionFeignClient;

    @Autowired
    private RankingService rankingService;

    @Autowired
    private UserFeignClient userFeignClient;

    @Override
    public Competition getCompetitionDetail(Integer id, Integer userId) {
        System.out.println("DEBUG: 正在查询比赛详情，ID=" + id + ", 当前用户ID=" + userId);

        // 1. 获取比赛基本信息
        Competition competition = baseMapper.selectById(id);
        if (competition == null) return null;

        // 2. 从中间表拿到所有题目 ID
        List<Integer> problemIds = baseMapper.selectProblemIdsByCompetitionId(id);

        // --- 新增逻辑：获取该用户在本次比赛中已通过的题目 ID 集合 ---
        java.util.Set<Integer> solvedProblemIds = new java.util.HashSet<>();
        if (userId != null) {
            // 调用刚才在 CompetitionProblemMapper 中写的接口
            // 注意：这里需要注入 competitionProblemMapper 或者是你定义该方法的 Mapper
            List<Integer> acIds = cpMapper.selectAcceptedProblemIdsInCompetition(userId, id);
            if (acIds != null) {
                solvedProblemIds.addAll(acIds);
            }
        }

        // 3. 远程获取每个题目的详细信息并填充状态
        List<Object> problemDetails = new ArrayList<>();
        if (problemIds != null && !problemIds.isEmpty()) {
            for (Integer pid : problemIds) {
                try {
                    Object prob = problemFeignClient.getProblemById(pid);
                    if (prob != null) {
                        // 因为 Feign 返回的 Object 通常是 LinkedHashMap
                        if (prob instanceof java.util.Map) {
                            java.util.Map<String, Object> probMap = (java.util.Map<String, Object>) prob;
                            // 动态注入 isSolved 字段
                            probMap.put("isSolved", solvedProblemIds.contains(pid));
                            problemDetails.add(probMap);
                        } else {
                            problemDetails.add(prob);
                        }
                    }
                } catch (Exception e) {
                    log.error("Feign调用题目详情失败，PID: " + pid, e);
                }
            }
        }
        competition.setProblems(problemDetails);

        // 4. 处理报名状态
        if (userId != null) {
            competition.setIsRegistered(participationService.checkRegistration(id, userId));
        } else {
            competition.setIsRegistered(false);
        }

        return competition;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result handleSubmission(Integer userId, Integer competitionId, Integer problemId, String code, String language) {

        // 1. 基础校验
        Competition comp = this.getById(competitionId);
        if (comp == null) return Result.error("比赛不存在");

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(comp.getStartTime())) return Result.error("比赛尚未开始");
        if (now.isAfter(comp.getEndTime())) return Result.error("比赛已结束");

        // 2. 构造参数（注意：这里增加一个标识，告诉判题服务这是异步处理）
        Map<String, Object> submissionParams = new HashMap<>();
        submissionParams.put("userId", userId);
        submissionParams.put("problemId", problemId);
        submissionParams.put("competitionId", competitionId);
        submissionParams.put("codeContent", code);
        submissionParams.put("language", language);
        // 建议传递 LocalDateTime 对象，由序列化器处理格式

        try {
            // 3. 核心改变：调用判题服务进入队列，立即返回凭证
            // 修改后的 doSubmit 应该只负责“落库”并返回一个 submissionId，状态设为 PENDING
            Result remoteRes = submissionClient.doSubmit(submissionParams);

            if (remoteRes.getCode() != 200) {
                return remoteRes;
            }

            // 4. 获取提交 ID
            Map<String, Object> resultMap = (Map<String, Object>) remoteRes.getData();

        /* 注意：这里不再调用 updateAcmStats。
           统计数据的更新应该放在“判题服务”回调“比赛服务”时，
           或者由一个专门的异步监听器（如消息队列消费者）来处理。
        */

            // 立即返回，前端拿到状态为 PENDING 的数据，开始执行你写的轮询逻辑
            return Result.success(resultMap);

        } catch (Exception e) {
            log.error("判题请求发送失败", e);
            throw new RuntimeException("系统繁忙，请稍后再试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAcmStats(Integer userId, Integer competitionId, Integer problemId, String status) {
        // 1. 获取比赛信息以计算当前比赛时长（分钟）
        Competition comp = this.getById(competitionId);
        if (comp == null) return;

        boolean isAc = "AC".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status);
        // 计算罚时分钟数
        int currentMinute = (int) java.time.Duration.between(comp.getStartTime(), LocalDateTime.now()).toMinutes();

        // 2. 查询该用户此题的统计记录
        CompetitionSubmissionStats stats = statsMapper.selectOne(new LambdaQueryWrapper<CompetitionSubmissionStats>()
                .eq(CompetitionSubmissionStats::getUserId, userId)
                .eq(CompetitionSubmissionStats::getCompetitionId, competitionId)
                .eq(CompetitionSubmissionStats::getProblemId, problemId));

        boolean isNew = false;
        if (stats == null) {
            isNew = true;
            stats = new CompetitionSubmissionStats();
            stats.setUserId(userId);
            stats.setCompetitionId(competitionId);
            stats.setProblemId(problemId);
            stats.setIsAc(false);
            stats.setWrongAttempts(0);
        }

        // 关键逻辑：如果该题已经 AC，则该题后续的所有提交（无论是 AC 还是 WA）都不再计入统计
        if (stats.getIsAc()) return;

        if (isAc) {
            // --- 核心：处理第一次 AC ---
            stats.setIsAc(true);
            stats.setAcTime(currentMinute);

            // ACM 规则：该题罚时 = 成功时的分钟数 + (AC前的错误尝试次数 * 20)
            int problemPenalty = stats.getAcTime() + (stats.getWrongAttempts() * 20);

            // 原子更新用户在本次比赛的总分和总罚时
            UpdateWrapper<Participation> uw = new UpdateWrapper<>();
            uw.eq("user_id", userId).eq("competition_id", competitionId)
                    .setSql("solved_count = solved_count + 1")
                    .setSql("total_penalty = total_penalty + " + problemPenalty);
            participationMapper.update(null, uw);
        } else {
            // --- 核心：处理非 AC 情况 ---
            // 排除编译错误 (CE)，通常只有 WA, TLE, MLE, RE 等才计入错误尝试
            if (!"CE".equalsIgnoreCase(status)) {
                stats.setWrongAttempts(stats.getWrongAttempts() + 1);
            }
        }

        // 3. 执行数据库写入
        if (isNew) {
            statsMapper.insert(stats);
        } else {
            statsMapper.update(stats, new LambdaUpdateWrapper<CompetitionSubmissionStats>()
                    .eq(CompetitionSubmissionStats::getUserId, userId)
                    .eq(CompetitionSubmissionStats::getCompetitionId, competitionId)
                    .eq(CompetitionSubmissionStats::getProblemId, problemId));
        }

        // 4. 同步更新 Redis 排行榜和题目状态缓存
        try {
            // 查询最新的 participation 数据
            Participation participation = participationMapper.selectOne(
                    new LambdaQueryWrapper<Participation>()
                            .eq(Participation::getUserId, userId)
                            .eq(Participation::getCompetitionId, competitionId));
            if (participation != null) {
                rankingService.updateRank(competitionId, userId,
                        participation.getSolvedCount(),
                        participation.getTotalPenalty());
            }
            // 缓存该用户此题的 AC 状态
            rankingService.setProblemStatus(competitionId, userId, problemId, status);
        } catch (Exception e) {
            // Redis 更新失败不影响核心业务
            log.error("Redis 排行榜同步失败", e);
        }
    }


    @Override
    public List<Participation> getRanklist(Integer competitionId) {
        // 1. 先尝试从 Redis 获取排行榜
        try {
            List<Map<String, Object>> redisRanks = rankingService.getRankList(competitionId, 0, 99);
            if (!redisRanks.isEmpty()) {
                // Redis 命中：用 userId 列表批量查询 MySQL
                List<Integer> userIds = redisRanks.stream()
                        .map(m -> (Integer) m.get("userId"))
                        .toList();

                List<Participation> participations = participationMapper.selectList(
                        new LambdaQueryWrapper<Participation>()
                                .eq(Participation::getCompetitionId, competitionId)
                                .in(Participation::getUserId, userIds));

                // 保持 Redis 的排名顺序
                Map<Integer, Participation> partMap = participations.stream()
                        .collect(Collectors.toMap(Participation::getUserId, p -> p));

                // 通过 Feign 批量获取用户信息（用户名、真名），补齐排行榜展示字段
                for (Participation p : participations) {
                    try {
                        Map<String, Object> user = userFeignClient.getUserById(p.getUserId());
                        if (user != null && !user.isEmpty()) {
                            p.setUsername((String) user.get("username"));
                            p.setRealName((String) user.get("realName"));
                        }
                    } catch (Exception e) {
                        log.warn("获取用户信息失败 userId={}: {}", p.getUserId(), e.getMessage());
                    }
                }

                List<Participation> ordered = new ArrayList<>();
                for (Map<String, Object> rankItem : redisRanks) {
                    Integer uid = (Integer) rankItem.get("userId");
                    Participation p = partMap.get(uid);
                    if (p != null) {
                        ordered.add(p);
                    }
                }

                // 补全 submissionStats 详情
                if (!ordered.isEmpty()) {
                    List<Integer> orderedUserIds = ordered.stream().map(Participation::getUserId).toList();
                    List<CompetitionSubmissionStats> allStats = statsMapper.selectList(
                            new LambdaQueryWrapper<CompetitionSubmissionStats>()
                                    .eq(CompetitionSubmissionStats::getCompetitionId, competitionId)
                                    .in(CompetitionSubmissionStats::getUserId, orderedUserIds));
                    Map<Integer, List<CompetitionSubmissionStats>> statsByUser = allStats.stream()
                            .collect(Collectors.groupingBy(CompetitionSubmissionStats::getUserId));
                    for (Participation p : ordered) {
                        List<CompetitionSubmissionStats> userStats = statsByUser.getOrDefault(p.getUserId(), new ArrayList<>());
                        Map<Integer, CompetitionSubmissionStats> statMap = userStats.stream()
                                .collect(Collectors.toMap(CompetitionSubmissionStats::getProblemId, s -> s));
                        p.setSubmissionStats(statMap);
                    }
                }

                return ordered;
            }
        } catch (Exception e) {
            log.error("Redis 排行榜读取失败，降级到 MySQL", e);
        }

        // 2. Redis 未命中或异常 → 降级查 MySQL（原逻辑）
        List<Participation> ranklist = participationMapper.getRanklist(competitionId);
        if (ranklist.isEmpty()) return ranklist;

        List<Integer> userIds = ranklist.stream().map(Participation::getUserId).toList();
        List<CompetitionSubmissionStats> allStats = statsMapper.selectList(
                new LambdaQueryWrapper<CompetitionSubmissionStats>()
                        .eq(CompetitionSubmissionStats::getCompetitionId, competitionId)
                        .in(CompetitionSubmissionStats::getUserId, userIds)
        );

        Map<Integer, List<CompetitionSubmissionStats>> statsByUser = allStats.stream()
                .collect(Collectors.groupingBy(CompetitionSubmissionStats::getUserId));

        for (Participation p : ranklist) {
            List<CompetitionSubmissionStats> userStats = statsByUser.getOrDefault(p.getUserId(), new ArrayList<>());
            Map<Integer, CompetitionSubmissionStats> statMap = userStats.stream()
                    .collect(Collectors.toMap(CompetitionSubmissionStats::getProblemId, s -> s));
            p.setSubmissionStats(statMap);
        }
        return ranklist;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addProblems(Integer competitionId, List<Integer> problemIds) {
        // 1. 先删除该比赛原有的题目关联（如果是“编辑”逻辑）
        cpMapper.delete(new LambdaQueryWrapper<CompetitionProblem>()
                .eq(CompetitionProblem::getCompetitionId, competitionId));

        // 2. 批量插入新的关联
        for (Integer pid : problemIds) {
            CompetitionProblem cp = new CompetitionProblem();
            cp.setCompetitionId(competitionId);
            cp.setProblemId(pid);
            cpMapper.insert(cp);
        }
        return true;
    }

    public Result<Map<String, Object>> getProblemStats(Integer competitionId) {
        return submissionFeignClient.getStatsByCompetition(competitionId);
    }

    @Override
    public List<Competition> getListWithRegisterStatus(Integer userId) {
        // 1. 获取所有比赛基础信息
        List<Competition> competitions = this.list();

        // 2. 如果用户未登录，直接返回列表（此时 isRegistered 默认为 null/false）
        if (userId == null) {
            return competitions;
        }

        // 3. 查询 participations 表，获取该用户参加的所有比赛 ID
        // 这里的 participationService 对应你的 participations 表
        Set<Integer> registeredIds = participationService.list(
                        new LambdaQueryWrapper<Participation>()
                                .eq(Participation::getUserId, userId)
                                .select(Participation::getCompetitionId) // 仅查询 ID 列，提高效率
                ).stream()
                .map(Participation::getCompetitionId)
                .collect(Collectors.toSet());

        // 4. 批量回填状态到比赛对象中
        competitions.forEach(c -> {
            // 如果 set 中包含该 ID，说明已报名
            c.setIsRegistered(registeredIds.contains(c.getCompetitionId()));
        });

        return competitions;
    }


}