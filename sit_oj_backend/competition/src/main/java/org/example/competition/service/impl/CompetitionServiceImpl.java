package org.example.competition.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

        // 4. 同步更新 Redis 排行榜和题目状态缓存（封榜期间跳过）
        try {
            if (!isInFreezePeriod(comp)) {
                Participation participation = participationMapper.selectOne(
                        new LambdaQueryWrapper<Participation>()
                                .eq(Participation::getUserId, userId)
                                .eq(Participation::getCompetitionId, competitionId));
                if (participation != null) {
                    rankingService.updateRank(competitionId, userId,
                            participation.getSolvedCount(),
                            participation.getTotalPenalty());
                }
                rankingService.setProblemStatus(competitionId, userId, problemId, status);
            }
        } catch (Exception e) {
            log.error("Redis 排行榜同步失败", e);
        }
    }

    /**
     * 判断比赛当前是否处于封榜期（结束前 freezeMinute 分钟 ~ 结束时刻）
     */
    private boolean isInFreezePeriod(Competition comp) {
        if (comp.getFreezeMinute() == null || comp.getFreezeMinute() <= 0) return false;
        if (comp.getEndTime() == null) return false;
        LocalDateTime freezeStart = comp.getEndTime().minusMinutes(comp.getFreezeMinute());
        LocalDateTime now = LocalDateTime.now();
        // 封榜持续到管理员手动解封，不随比赛结束自动解榜
        return now.isAfter(freezeStart);
    }


    @Override
    public Map<String, Object> getRanklist(Integer competitionId, Integer current, Integer size) {
        int start = (current - 1) * size;
        int end = start + size - 1;

        // 检查封榜状态
        Competition comp = this.getById(competitionId);
        boolean isFrozen = comp != null && isInFreezePeriod(comp);

        // 1. 先尝试从 Redis 获取排行榜
        try {
            List<Map<String, Object>> redisRanks = rankingService.getRankList(competitionId, start, end);
            if (!redisRanks.isEmpty()) {
                // 获取总参与人数
                Long total = rankingService.getParticipantCount(competitionId);

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

                Map<String, Object> result = new HashMap<>();
                result.put("records", ordered);
                result.put("total", total);
                result.put("isFrozen", isFrozen);
                return result;
            }
        } catch (Exception e) {
            log.error("Redis 排行榜读取失败，降级到 MySQL", e);
        }

        // 2. Redis 未命中或异常 → 降级查 MySQL（分页）
        Page<Participation> pageParam = new Page<>(current, size);
        IPage<Participation> page = participationMapper.getRanklistPage(pageParam, competitionId);
        List<Participation> ranklist = page.getRecords();

        if (!ranklist.isEmpty()) {
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
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", ranklist);
        result.put("total", page.getTotal());
        result.put("isFrozen", isFrozen);
        return result;
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
    public IPage<Competition> getListWithRegisterStatus(Integer userId, Integer current, Integer size) {
        // 1. 分页获取比赛基础信息
        Page<Competition> page = new Page<>(current, size);
        this.lambdaQuery().orderByDesc(Competition::getCreateTime).page(page);

        // 2. 如果用户未登录，直接返回分页结果（此时 isRegistered 默认为 null/false）
        if (userId == null) {
            return page;
        }

        // 3. 查询 participations 表，获取该用户参加的所有比赛 ID
        Set<Integer> registeredIds = participationService.list(
                        new LambdaQueryWrapper<Participation>()
                                .eq(Participation::getUserId, userId)
                                .select(Participation::getCompetitionId)
                ).stream()
                .map(Participation::getCompetitionId)
                .collect(Collectors.toSet());

        // 4. 批量回填状态到比赛对象中
        page.getRecords().forEach(c ->
            c.setIsRegistered(registeredIds.contains(c.getCompetitionId()))
        );

        return page;
    }

    @Override
    public boolean isFrozen(Integer competitionId) {
        Competition comp = this.getById(competitionId);
        return comp != null && isInFreezePeriod(comp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result unfreeze(Integer competitionId) {
        Competition comp = this.getById(competitionId);
        if (comp == null) return Result.error("比赛不存在");
        if (comp.getFreezeMinute() == null || comp.getFreezeMinute() <= 0) {
            return Result.error("该比赛未设置封榜");
        }

        // 1. 清除封榜
        comp.setFreezeMinute(0);
        this.updateById(comp);

        // 2. 重建 Redis 排名（封榜期间的提交只写了 MySQL，需同步到 Redis）
        try {
            List<Participation> all = participationMapper.selectList(
                    new LambdaQueryWrapper<Participation>()
                            .eq(Participation::getCompetitionId, competitionId));
            for (Participation p : all) {
                rankingService.updateRank(competitionId, p.getUserId(),
                        p.getSolvedCount(), p.getTotalPenalty());
            }
            log.info("比赛 {} 已解封，Redis 排名已重建 ({} 名参赛者)", competitionId, all.size());
        } catch (Exception e) {
            log.error("解封后重建 Redis 排名失败", e);
        }

        return Result.success("解封成功");
    }

    @Override
    public String exportForResolver(Integer competitionId) {
        Competition comp = this.getById(competitionId);
        if (comp == null) return null;

        List<Participation> parts = participationMapper.selectList(
                new LambdaQueryWrapper<Participation>()
                        .eq(Participation::getCompetitionId, competitionId));
        List<CompetitionProblem> cpList = cpMapper.selectList(
                new LambdaQueryWrapper<CompetitionProblem>()
                        .eq(CompetitionProblem::getCompetitionId, competitionId));

        // 获取问题详情
        Map<Integer, String> problemLabels = new java.util.LinkedHashMap<>();
        List<Integer> pids = cpList.stream().map(CompetitionProblem::getProblemId).toList();
        int idx = 0;
        for (Integer pid : pids) {
            problemLabels.put(pid, String.valueOf((char) ('A' + idx++)));
        }

        // 构建 NDJSON
        StringBuilder sb = new StringBuilder();

        // Contest info
        sb.append("{\"type\":\"contests\",\"id\":\"").append(competitionId).append("\",\"data\":{");
        sb.append("\"id\":\"").append(competitionId).append("\",");
        sb.append("\"name\":\"").append(escapeJson(comp.getCompetitionName())).append("\",");
        sb.append("\"start_time\":\"").append(comp.getStartTime()).append("\",");
        sb.append("\"end_time\":\"").append(comp.getEndTime()).append("\",");
        sb.append("\"duration\":\"").append(formatDuration(comp.getStartTime(), comp.getEndTime())).append("\",");
        sb.append("\"scoreboard_freeze_duration\":\"").append(formatFreezeDuration(comp.getFreezeMinute())).append("\",");
        sb.append("\"scoreboard_type\":\"pass-fail\"");
        sb.append("}}\n");

        // Teams
        for (Participation p : parts) {
            String teamName = "User" + p.getUserId();
            try {
                Map<String, Object> user = userFeignClient.getUserById(p.getUserId());
                if (user != null && !user.isEmpty()) {
                    teamName = (String) user.getOrDefault("realName",
                            user.getOrDefault("username", "User" + p.getUserId()));
                }
            } catch (Exception ignored) {}
            sb.append("{\"type\":\"teams\",\"id\":\"").append(p.getUserId()).append("\",\"data\":{");
            sb.append("\"id\":\"").append(p.getUserId()).append("\",");
            sb.append("\"name\":\"").append(escapeJson(teamName)).append("\",");
            sb.append("\"group_ids\":[]");
            sb.append("}}\n");
        }

        // Problems
        for (Map.Entry<Integer, String> e : problemLabels.entrySet()) {
            sb.append("{\"type\":\"problems\",\"id\":\"").append(e.getKey()).append("\",\"data\":{");
            sb.append("\"id\":\"").append(e.getKey()).append("\",");
            sb.append("\"label\":\"").append(e.getValue()).append("\",");
            sb.append("\"name\":\"").append(e.getValue()).append("\"");
            sb.append("}}\n");
        }

        // Submissions & Judgements
        try {
            List<Map<String, Object>> rawSubs = getCompetitionSubmissionsForExport(competitionId);
            for (Map<String, Object> sub : rawSubs) {
                Integer subId = (Integer) sub.get("submissionId");
                Integer uid = (Integer) sub.get("userId");
                Integer pid = (Integer) sub.get("problemId");
                String status = (String) sub.get("status");
                java.time.LocalDateTime subTime = (java.time.LocalDateTime) sub.get("submissionTime");
                long contestMin = java.time.Duration.between(comp.getStartTime(), subTime).toMinutes();

                sb.append("{\"type\":\"submissions\",\"id\":\"").append(subId).append("\",\"data\":{");
                sb.append("\"id\":\"").append(subId).append("\",");
                sb.append("\"team_id\":\"").append(uid).append("\",");
                sb.append("\"problem_id\":\"").append(pid).append("\",");
                sb.append("\"contest_time\":\"").append(formatContestTime(contestMin)).append("\"");
                sb.append("}}\n");

                String judgement = mapStatus(status);
                sb.append("{\"type\":\"judgements\",\"id\":\"J").append(subId).append("\",\"data\":{");
                sb.append("\"id\":\"J").append(subId).append("\",");
                sb.append("\"submission_id\":\"").append(subId).append("\",");
                sb.append("\"judgement_type_id\":\"").append(judgement).append("\"");
                sb.append("}}\n");
            }
        } catch (Exception e) {
            log.error("导出比赛提交数据失败", e);
        }

        return sb.toString();
    }

    // --- Helper methods for export ---

    private List<Map<String, Object>> getCompetitionSubmissionsForExport(Integer competitionId) {
        try {
            return submissionFeignClient.exportSubmissions(competitionId);
        } catch (Exception e) {
            log.error("导出比赛提交失败: competitionId={}", competitionId, e);
            return new ArrayList<>();
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private String formatDuration(LocalDateTime start, LocalDateTime end) {
        long mins = java.time.Duration.between(start, end).toMinutes();
        return String.format("%d:%02d:00", mins / 60, mins % 60);
    }

    private String formatFreezeDuration(Integer freezeMinute) {
        if (freezeMinute == null || freezeMinute <= 0) return "0:00:00";
        return String.format("%d:%02d:00", freezeMinute / 60, freezeMinute % 60);
    }

    private String formatContestTime(long minutes) {
        return String.format("%d:%02d:00", minutes / 60, minutes % 60);
    }

    private String mapStatus(String status) {
        if (status == null) return "WA";
        String s = status.toUpperCase();
        if (s.contains("AC")) return "AC";
        if (s.contains("WA") || s.contains("WRONG")) return "WA";
        if (s.contains("TLE")) return "TLE";
        if (s.contains("CE") || s.contains("COMPILE")) return "CE";
        if (s.contains("RE") || s.contains("RUNTIME")) return "RTE";
        return "WA";
    }

}