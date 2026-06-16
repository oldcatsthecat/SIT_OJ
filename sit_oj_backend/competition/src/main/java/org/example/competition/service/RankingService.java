package org.example.competition.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Redis 排行榜服务
 * 使用 ZSET 按 ACM 规则排序：score = solvedCount * 1000000 - totalPenalty
 * score 越大排名越前（使用 reverseRange 获取降序排名）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RANK_KEY_PREFIX  = "competition:rank:";
    private static final String STATS_KEY_PREFIX = "competition:stats:";

    // ==================== 排行榜操作 ====================

    /**
     * 更新排行榜中某用户的分数
     * ACM 规则：score = solvedCount * 1_000_000 - totalPenalty
     */
    public void updateRank(Integer competitionId, Integer userId,
                           int solvedCount, int totalPenalty) {
        String key = RANK_KEY_PREFIX + competitionId;
        double score = solvedCount * 1_000_000.0 - totalPenalty;
        redisTemplate.opsForZSet().add(key, userId.toString(), score);
        log.debug("Redis 排行榜更新: competition={}, userId={}, solved={}, penalty={}, score={}",
                competitionId, userId, solvedCount, totalPenalty, score);
    }

    /**
     * 获取排行榜（降序：分数高的在前）
     * @return List<Map> [{rank, userId, score}]
     */
    public List<Map<String, Object>> getRankList(Integer competitionId, int start, int end) {
        String key = RANK_KEY_PREFIX + competitionId;
        Set<ZSetOperations.TypedTuple<Object>> ranked =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        List<Map<String, Object>> list = new ArrayList<>();
        int rank = start + 1;
        if (ranked != null) {
            for (ZSetOperations.TypedTuple<Object> tuple : ranked) {
                Map<String, Object> item = new HashMap<>();
                item.put("rank", rank++);
                item.put("userId", Integer.parseInt(Objects.requireNonNull(tuple.getValue()).toString()));
                item.put("score", tuple.getScore());
                list.add(item);
            }
        }
        return list;
    }

    /**
     * 获取排行榜总人数
     */
    public Long getParticipantCount(Integer competitionId) {
        Long count = redisTemplate.opsForZSet().size(RANK_KEY_PREFIX + competitionId);
        return count != null ? count : 0L;
    }

    // ==================== 题目状态缓存 ====================

    /**
     * 缓存用户在某题的状态（用于快速判断是否已 AC）
     */
    public void setProblemStatus(Integer competitionId, Integer userId,
                                 Integer problemId, String status) {
        String key = STATS_KEY_PREFIX + competitionId + ":" + userId;
        redisTemplate.opsForHash().put(key, problemId.toString(), status);
        // 比赛结束后 24h 自动过期
        redisTemplate.expire(key, Duration.ofHours(24));
    }

    /**
     * 获取用户在某题的状态
     */
    public String getProblemStatus(Integer competitionId, Integer userId, Integer problemId) {
        String key = STATS_KEY_PREFIX + competitionId + ":" + userId;
        Object status = redisTemplate.opsForHash().get(key, problemId.toString());
        return status != null ? status.toString() : null;
    }

    /**
     * 检查用户在某题是否已 AC（走缓存）
     */
    public boolean isProblemAccepted(Integer competitionId, Integer userId, Integer problemId) {
        String status = getProblemStatus(competitionId, userId, problemId);
        return "AC".equalsIgnoreCase(status);
    }
}
