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
        log.info("ACM_STATS_CALLED: userId={} compId={} probId={} status={}", userId, competitionId, problemId, status);
        Competition comp = this.getById(competitionId);
        if (comp == null) { log.warn("ACM_STATS_COMP_NULL: compId={}", competitionId); return; }

        boolean isAc = "AC".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status);
        boolean frozen = isInFreezePeriod(comp);
        log.info("ACM_STATS_FROZEN_CHECK: compId={} freezeMinute={} isFrozen={}", competitionId, comp.getFreezeMinute(), frozen);
        int currentMinute = (int) java.time.Duration.between(comp.getStartTime(), LocalDateTime.now()).toMinutes();

        // 查 stats 记录
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

        if (stats.getIsAc()) return;

        // 封榜期间：不写 MySQL stats，AC 隐藏，所有提交仅记录到 Redis 供前端蓝底 +N 显示
        if (frozen) {
            if (!"CE".equalsIgnoreCase(status)) {
                rankingService.incrFreezeAttempt(competitionId, userId, problemId);
                log.info("FROZEN_INCR: userId={} compId={} probId={} status={}", userId, competitionId, problemId, status);
            }
            return;
        }

        // --- 非封榜期：正常更新 ---
        if (isAc) {
            stats.setIsAc(true);
            stats.setAcTime(currentMinute);

            int problemPenalty = stats.getAcTime() + (stats.getWrongAttempts() * 20);
            UpdateWrapper<Participation> uw = new UpdateWrapper<>();
            uw.eq("user_id", userId).eq("competition_id", competitionId)
                    .setSql("solved_count = solved_count + 1")
                    .setSql("total_penalty = total_penalty + " + problemPenalty);
            participationMapper.update(null, uw);
        } else {
            if (!"CE".equalsIgnoreCase(status)) {
                stats.setWrongAttempts(stats.getWrongAttempts() + 1);
            }
        }

        if (isNew) {
            statsMapper.insert(stats);
        } else {
            statsMapper.update(stats, new LambdaUpdateWrapper<CompetitionSubmissionStats>()
                    .eq(CompetitionSubmissionStats::getUserId, userId)
                    .eq(CompetitionSubmissionStats::getCompetitionId, competitionId)
                    .eq(CompetitionSubmissionStats::getProblemId, problemId));
        }

        try {
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
        } catch (Exception e) {
            log.error("Redis 排行榜同步失败", e);
        }
    }

    /**
     * 重放单条提交以重建统计数据（解封时调用，不做 freeze 检查）
     */
    private void replaySubmission(Integer userId, Integer competitionId, Integer problemId, String status,
                                   Object timeObj, Competition comp) {
        boolean isAc = "AC".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status);
        int currentMinute;
        if (timeObj instanceof LocalDateTime) {
            currentMinute = (int) java.time.Duration.between(comp.getStartTime(), (LocalDateTime) timeObj).toMinutes();
        } else {
            try {
                LocalDateTime t = LocalDateTime.parse(timeObj.toString().replace("T", " ").substring(0, 19),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                currentMinute = (int) java.time.Duration.between(comp.getStartTime(), t).toMinutes();
            } catch (Exception e) {
                currentMinute = (int) java.time.Duration.between(comp.getStartTime(), LocalDateTime.now()).toMinutes();
            }
        }

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

        if (stats.getIsAc()) return;

        if (isAc) {
            stats.setIsAc(true);
            stats.setAcTime(currentMinute);
            int problemPenalty = stats.getAcTime() + (stats.getWrongAttempts() * 20);
            UpdateWrapper<Participation> uw = new UpdateWrapper<>();
            uw.eq("user_id", userId).eq("competition_id", competitionId)
                    .setSql("solved_count = solved_count + 1")
                    .setSql("total_penalty = total_penalty + " + problemPenalty);
            participationMapper.update(null, uw);
        } else {
            if (!"CE".equalsIgnoreCase(status)) {
                stats.setWrongAttempts(stats.getWrongAttempts() + 1);
            }
        }

        if (isNew) {
            statsMapper.insert(stats);
        } else {
            statsMapper.update(stats, new LambdaUpdateWrapper<CompetitionSubmissionStats>()
                    .eq(CompetitionSubmissionStats::getUserId, userId)
                    .eq(CompetitionSubmissionStats::getCompetitionId, competitionId)
                    .eq(CompetitionSubmissionStats::getProblemId, problemId));
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
                // 附加封榜期尝试次数（蓝底 +N 数据源）
                Map<Integer, Map<Integer, Integer>> frozenMap = new HashMap<>();
                for (Participation p : ordered) {
                    Map<Integer, Integer> attempts = rankingService.getFreezeAttempts(competitionId, p.getUserId());
                    if (!attempts.isEmpty()) frozenMap.put(p.getUserId(), attempts);
                }
                result.put("frozenAttempts", frozenMap);
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
        Map<Integer, Map<Integer, Integer>> frozenMap2 = new HashMap<>();
        for (Participation p : ranklist) {
            Map<Integer, Integer> attempts = rankingService.getFreezeAttempts(competitionId, p.getUserId());
            if (!attempts.isEmpty()) frozenMap2.put(p.getUserId(), attempts);
        }
        result.put("frozenAttempts", frozenMap2);
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

        // 1. 从 submissions 表重建全部统计数据（封榜期间 stats + participations 均未更新）
        try {
            List<Map<String, Object>> allSubs = submissionFeignClient.exportSubmissions(competitionId);
            // 按提交时间排序
            allSubs.sort((a, b) -> {
                String ta = (String) a.getOrDefault("submissionTime", "");
                String tb = (String) b.getOrDefault("submissionTime", "");
                return ta.compareTo(tb);
            });
            // 清空现有 stats
            statsMapper.delete(new LambdaQueryWrapper<CompetitionSubmissionStats>()
                    .eq(CompetitionSubmissionStats::getCompetitionId, competitionId));
            // 重置 participations
            participationMapper.update(null, new LambdaUpdateWrapper<Participation>()
                    .eq(Participation::getCompetitionId, competitionId)
                    .set(Participation::getSolvedCount, 0)
                    .set(Participation::getTotalPenalty, 0));
            // 重放所有提交
            for (Map<String, Object> sub : allSubs) {
                Integer uid = (Integer) sub.get("userId");
                Integer pid = (Integer) sub.get("problemId");
                String st = (String) sub.get("status");
                Object timeObj = sub.get("submissionTime");
                if (uid == null || pid == null || st == null) continue;
                replaySubmission(uid, competitionId, pid, st, timeObj, comp);
            }
            log.info("比赛 {} 已解封，从 {} 条提交重建完成", competitionId, allSubs.size());
        } catch (Exception e) {
            log.error("解封后重建统计数据失败", e);
            return Result.error("解封失败：无法获取提交数据");
        }

        // 2. 清除封榜标记（重建成功后才修改）
        comp.setFreezeMinute(0);
        this.updateById(comp);

        // 2.5 清空封榜期 Redis 尝试计数
        rankingService.clearFreezeAttempts(competitionId);

        // 3. 重建 Redis 排名
        try {
            List<Participation> all = participationMapper.selectList(
                    new LambdaQueryWrapper<Participation>()
                            .eq(Participation::getCompetitionId, competitionId));
            for (Participation p : all) {
                rankingService.updateRank(competitionId, p.getUserId(),
                        p.getSolvedCount(), p.getTotalPenalty());
            }
            log.info("比赛 {} Redis 排名已同步 ({} 名参赛者)", competitionId, all.size());
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

        Map<Integer, String> problemLabels = new java.util.LinkedHashMap<>();
        List<Integer> pids = cpList.stream().map(CompetitionProblem::getProblemId).toList();
        int idx = 0;
        for (Integer pid : pids) {
            problemLabels.put(pid, String.valueOf((char) ('A' + idx++)));
        }

        java.time.format.DateTimeFormatter tsFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String startTime = comp.getStartTime() != null ? comp.getStartTime().format(tsFmt) + ".000+08:00" : "";
        String endTime = comp.getEndTime() != null ? comp.getEndTime().format(tsFmt) + ".000+08:00" : "";
        long durationSec = comp.getStartTime() != null && comp.getEndTime() != null
                ? java.time.Duration.between(comp.getStartTime(), comp.getEndTime()).toSeconds() : 0;
        String durationStr = String.format("%02d:%02d:%02d.000", durationSec / 3600, (durationSec % 3600) / 60, durationSec % 60);
        int freezeSec = comp.getFreezeMinute() != null ? comp.getFreezeMinute() * 60 : 0;
        String freezeStr = String.format("%02d:%02d:%02d.000", freezeSec / 3600, (freezeSec % 3600) / 60, freezeSec % 60);

        StringBuilder sb = new StringBuilder();
        int token = 0;

        // contest
        sb.append("{\"data\":{\"allow_submit\":true,\"end_time\":\"").append(endTime)
          .append("\",\"runtime_as_score_tiebreaker\":null,\"shortname\":\"").append(competitionId)
          .append("\",\"penalty_time\":20,\"duration\":\"").append(durationStr)
          .append("\",\"warning_message\":null,\"start_time\":\"").append(startTime)
          .append("\",\"scoreboard_thaw_time\":null,\"scoreboard_type\":\"pass-fail\",")
          .append("\"scoreboard_freeze_duration\":\"").append(freezeStr).append("\",")
          .append("\"name\":\"").append(escapeJson(comp.getCompetitionName())).append("\",")
          .append("\"id\":\"").append(competitionId).append("\",")
          .append("\"formal_name\":\"").append(escapeJson(comp.getCompetitionName())).append("\",")
          .append("\"cid\":").append(competitionId).append("},")
          .append("\"id\":null,\"time\":\"").append(startTime).append("\",\"type\":\"contest\",\"token\":\"").append(token++).append("\"}\n");

        // judgement-types
        String[][] jts = {{"AC","correct","false"},{"CE","compiler error","false"},{"MLE","memory limit","true"},
                          {"OLE","output limit","true"},{"PE","presentation error","true"},{"RTE","run error","true"},
                          {"TLE","timelimit","true"},{"WA","wrong answer","true"}};
        for (String[] jt : jts) {
            sb.append("{\"data\":{\"penalty\":").append(jt[2]).append(",\"name\":\"").append(jt[1])
              .append("\",\"solved\":").append(jt[0].equals("AC")?"true":"false").append(",\"id\":\"").append(jt[0]).append("\"},")
              .append("\"id\":\"").append(jt[0]).append("\",\"time\":\"").append(startTime).append("\",\"type\":\"judgement-types\",\"token\":\"").append(token++).append("\"}\n");
        }

        // languages
        String[][] langs = {{"c","C"},{"cpp","C++"},{"java","Java"},{"python","Python 3"}};
        for (String[] l : langs) {
            sb.append("{\"data\":{\"extensions\":[\"").append(l[0]).append("\"],\"allow_judge\":true,\"name\":\"").append(l[1])
              .append("\",\"id\":\"").append(l[0]).append("\",\"time_factor\":1.0,\"entry_point_required\":false,\"entry_point_name\":null},")
              .append("\"id\":\"").append(l[0]).append("\",\"time\":\"").append(startTime).append("\",\"type\":\"languages\",\"token\":\"").append(token++).append("\"}\n");
        }

        // problems
        int ord = 1;
        for (Map.Entry<Integer, String> e : problemLabels.entrySet()) {
            sb.append("{\"data\":{\"attachments\":[],\"color\":null,\"time_limit\":1.0,\"statement\":[],\"name\":\"")
              .append(e.getValue()).append("\",\"probid\":").append(e.getKey()).append(",\"label\":\"").append(e.getValue())
              .append("\",\"id\":\"").append(e.getValue()).append("\",\"test_data_count\":1,\"rgb\":null,\"shortname\":\"").append(e.getValue())
              .append("\",\"ordinal\":").append(ord++).append("},")
              .append("\"id\":\"").append(e.getValue()).append("\",\"time\":\"").append(startTime).append("\",\"type\":\"problems\",\"token\":\"").append(token++).append("\"}\n");
        }

        // groups
        sb.append("{\"data\":{\"hidden\":false,\"color\":null,\"name\":\"participants\",\"sortorder\":0,\"id\":\"participants\",\"icpc_id\":null,\"allow_self_registration\":false,\"categoryid\":1},")
          .append("\"id\":\"participants\",\"time\":\"").append(startTime).append("\",\"type\":\"groups\",\"token\":\"").append(token++).append("\"}\n");

        // organizations & teams & accounts
        try {
            Map<String, String> orgNames = new java.util.LinkedHashMap<>();
            for (Participation p : parts) {
                Map<String, Object> user = null;
                try { user = userFeignClient.getUserById(p.getUserId()); } catch (Exception ignored) {}
                String orgName = "SIT";
                if (user != null && user.get("realName") != null) orgName = "上海应用技术大学";
                if (!orgNames.containsKey(orgName)) {
                    sb.append("{\"data\":{\"country\":\"CHN\",\"affilid\":\"").append(escapeJson(orgName)).append("\",\"name\":\"").append(escapeJson(orgName))
                      .append("\",\"id\":\"").append(escapeJson(orgName)).append("\",\"icpc_id\":\"").append(escapeJson(orgName))
                      .append("\",\"shortname\":\"").append(escapeJson(orgName)).append("\",\"formal_name\":\"").append(escapeJson(orgName)).append("\"},")
                      .append("\"id\":\"").append(escapeJson(orgName)).append("\",\"time\":\"").append(startTime).append("\",\"type\":\"organizations\",\"token\":\"").append(token++).append("\"}\n");
                    orgNames.put(orgName, "");
                }
                String teamName = user != null ? (String) user.getOrDefault("realName",
                        user.getOrDefault("username", "User" + p.getUserId())) : "User" + p.getUserId();
                if (teamName == null || teamName.isEmpty()) teamName = "User" + p.getUserId();
                sb.append("{\"data\":{\"hidden\":false,\"nationality\":\"CHN\",\"affiliation\":\"").append(escapeJson(orgName))
                  .append("\",\"organization_id\":\"").append(escapeJson(orgName)).append("\",\"teamid\":").append(p.getUserId())
                  .append(",\"group_ids\":[\"participants\"],\"name\":\"").append(escapeJson(teamName))
                  .append("\",\"id\":\"").append(p.getUserId()).append("\",\"icpc_id\":\"").append(p.getUserId())
                  .append("\",\"label\":\"").append(p.getUserId()).append("\",\"display_name\":\"").append(escapeJson(teamName)).append("\"},")
                  .append("\"id\":\"").append(p.getUserId()).append("\",\"time\":\"").append(startTime).append("\",\"type\":\"teams\",\"token\":\"").append(token++).append("\"}\n");

                sb.append("{\"data\":{\"last_ip\":null,\"last_login_time\":null,\"roles\":[\"team\"],\"ip\":null,\"team\":\"").append(escapeJson(teamName))
                  .append("\",\"team_id\":\"").append(p.getUserId()).append("\",\"type\":\"team\",\"userid\":").append(p.getUserId())
                  .append(",\"enabled\":true,\"name\":\"").append(escapeJson(teamName)).append("\",\"last_api_login_time\":null,\"id\":\"").append(p.getUserId())
                  .append("\",\"first_login_time\":null,\"email\":null,\"username\":\"").append(p.getUserId()).append("\"},")
                  .append("\"id\":\"").append(p.getUserId()).append("\",\"time\":\"").append(startTime).append("\",\"type\":\"accounts\",\"token\":\"").append(token++).append("\"}\n");
            }
        } catch (Exception e) { log.error("导出teams失败", e); }

        // state (start)
        sb.append("{\"data\":{\"thawed\":null,\"finalized\":null,\"end_of_updates\":null,\"ended\":null,\"frozen\":null,\"started\":\"")
          .append(startTime).append("\"},\"id\":null,\"time\":\"").append(startTime).append("\",\"type\":\"state\",\"token\":\"").append(token++).append("\"}\n");

        // submissions & judgements & runs
        try {
            List<Map<String, Object>> rawSubs = getCompetitionSubmissionsForExport(competitionId);
            for (Map<String, Object> sub : rawSubs) {
                Integer subId = (Integer) sub.get("submissionId");
                Integer uid = (Integer) sub.get("userId");
                Integer pid = (Integer) sub.get("problemId");
                String probLabel = problemLabels.getOrDefault(pid, "?");
                String status = (String) sub.get("status");
                String lang = (String) sub.getOrDefault("language", "cpp");
                Object timeObj = sub.get("submissionTime");
                java.time.LocalDateTime subTime;
                if (timeObj instanceof String) {
                    subTime = java.time.LocalDateTime.parse(((String) timeObj).replace("T", " ").substring(0, 19),
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } else {
                    subTime = comp.getStartTime(); // fallback
                }
                long contestMs = java.time.Duration.between(comp.getStartTime(), subTime).toMillis();
                String cTime = String.format("%02d:%02d:%02d.%03d", contestMs / 3600000, (contestMs % 3600000) / 60000, (contestMs % 60000) / 1000, contestMs % 1000);
                String subTimeStr = subTime != null ? subTime.format(tsFmt) + ".000+08:00" : startTime;

                double runTime = 0.003;
                try { Object tc = sub.get("timeCost"); if (tc instanceof Number) runTime = ((Number) tc).doubleValue() / 1000.0; } catch (Exception ignored) {}

                // submission
                sb.append("{\"data\":{\"problem_id\":\"").append(probLabel).append("\",\"files\":[{\"filename\":\"submission.zip\",\"mime\":\"application/zip\",\"href\":\"contests/submissions/").append(subId).append("/files\"}],")
                  .append("\"import_error\":null,\"language_id\":\"").append(lang).append("\",\"time\":\"").append(subTimeStr).append("\",")
                  .append("\"contest_time\":\"").append(cTime).append("\",\"team_id\":\"").append(uid).append("\",\"id\":\"").append(subId).append("\",")
                  .append("\"entry_point\":null,\"submitid\":").append(subId).append("},")
                  .append("\"id\":\"").append(subId).append("\",\"time\":\"").append(subTimeStr).append("\",\"type\":\"submissions\",\"token\":\"").append(token++).append("\"}\n");

                // judgement (pending)
                sb.append("{\"data\":{\"valid\":true,\"start_time\":\"").append(subTimeStr).append("\",\"submission_id\":\"").append(subId).append("\",")
                  .append("\"end_contest_time\":null,\"end_time\":null,\"start_contest_time\":\"").append(cTime).append("\",\"id\":\"").append(subId).append("\",")
                  .append("\"max_run_time\":null,\"judgement_type_id\":null},")
                  .append("\"id\":\"").append(subId).append("\",\"time\":\"").append(subTimeStr).append("\",\"type\":\"judgements\",\"token\":\"").append(token++).append("\"}\n");

                // run
                String jt = mapStatus(status);
                sb.append("{\"data\":{\"run_time\":").append(runTime).append(",\"time\":\"").append(subTimeStr).append("\",\"contest_time\":\"").append(cTime).append("\",")
                  .append("\"id\":\"").append(subId).append("\",\"judgement_id\":\"").append(subId).append("\",\"judgement_type_id\":\"").append(jt).append("\",\"ordinal\":1},")
                  .append("\"id\":\"").append(subId).append("\",\"time\":\"").append(subTimeStr).append("\",\"type\":\"runs\",\"token\":\"").append(token++).append("\"}\n");

                // judgement (final)
                sb.append("{\"data\":{\"valid\":true,\"start_time\":\"").append(subTimeStr).append("\",\"submission_id\":\"").append(subId).append("\",")
                  .append("\"end_contest_time\":\"").append(cTime).append("\",\"end_time\":\"").append(subTimeStr).append("\",\"start_contest_time\":\"").append(cTime).append("\",")
                  .append("\"id\":\"").append(subId).append("\",\"max_run_time\":").append(runTime).append(",\"judgement_type_id\":\"").append(jt).append("\"},")
                  .append("\"id\":\"").append(subId).append("\",\"time\":\"").append(subTimeStr).append("\",\"type\":\"judgements\",\"token\":\"").append(token++).append("\"}\n");
            }
        } catch (Exception e) { log.error("导出提交失败", e); }

        // state (end, no finalized)
        String freezeTime = (comp.getFreezeMinute() != null && comp.getFreezeMinute() > 0)
                ? comp.getEndTime().minusMinutes(comp.getFreezeMinute()).format(tsFmt) + ".000+08:00" : endTime;
        sb.append("{\"data\":{\"thawed\":null,\"finalized\":null,\"end_of_updates\":null,\"ended\":\"").append(endTime).append("\",")
          .append("\"frozen\":\"").append(freezeTime).append("\",\"started\":\"").append(startTime).append("\"},")
          .append("\"id\":null,\"time\":\"").append(endTime).append("\",\"type\":\"state\",\"token\":\"").append(token++).append("\"}\n");
        // state (end, finalized) — Resolver uses this to confirm contest is over
        sb.append("{\"data\":{\"thawed\":null,\"finalized\":\"").append(endTime).append("\",\"end_of_updates\":null,\"ended\":\"").append(endTime).append("\",")
          .append("\"frozen\":\"").append(freezeTime).append("\",\"started\":\"").append(startTime).append("\"},")
          .append("\"id\":null,\"time\":\"").append(endTime).append("\",\"type\":\"state\",\"token\":\"").append(token).append("\"}\n");

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
        if (s.contains("AC") || s.contains("ACCEPT")) return "AC";
        if (s.contains("WA") || s.contains("WRONG")) return "WA";
        if (s.contains("TLE") || s.contains("TIME")) return "TLE";
        if (s.contains("CE") || s.contains("COMPILE")) return "CE";
        if (s.contains("RE") || s.contains("RUNTIME")) return "RTE";
        if (s.contains("MLE") || s.contains("MEMORY")) return "MLE";
        if (s.contains("OLE") || s.contains("OUTPUT")) return "OLE";
        if (s.contains("PE") || s.contains("PRESENT")) return "PE";
        if (s.contains("SE") || s.contains("SYSTEM") || s.contains("ERROR")) return "RTE";
        return "WA";
    }

}