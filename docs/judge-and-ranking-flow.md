# 判题核心逻辑 & 排行榜更新流程时序图

## 1. 判题核心逻辑（提交 → 判题 → 结果回调）

> **参与者说明**: User=用户, CC=CompetitionController, SS=SubmissionServiceImpl, DB=MySQL, JP=JudgeProducer, MQ=RabbitMQ, JC=JudgeRequestConsumer, JS=JudgeServiceImpl, JSrv=JudgeServer, RC=JudgeResultConsumer, PF=ProblemFeignClient, CF=CompetitionFeignClient

```mermaid
sequenceDiagram
    actor User
    participant CC
    participant SS
    participant DB
    participant JP
    participant MQ
    participant JC
    participant JS
    participant JSrv
    participant RC
    participant PF
    participant CF

    Note over User, CF: === Phase 1: Submit (Sync) ===
    User->>CC: POST /competitions/submit
    CC->>SS: handleSubmission(submission)
    SS->>DB: INSERT submission (status=PENDING)
    SS->>JP: sendJudgeRequest(JudgeMessage)
    JP->>MQ: publish to judge.exchange<br/>routingKey=judge.request
    SS-->>CC: Return immediately (status=PENDING)
    CC-->>User: "Submitted, waiting for judge"

    Note over User, CF: === Phase 2: Async Judging ===
    MQ->>JC: consume judge.request.queue
    JC->>JS: processJudge(JudgeMessage)

    Note over JS, JSrv: === Phase 3: Judge Server (blocking) ===
    JS->>PF: getProblemById(problemId)
    PF-->>JS: timeLimit, memoryLimit, judgeType

    alt judgeType = 0 (normal)
        JS->>JSrv: POST /judge
    else judgeType = 1 (SPJ)
        JS->>JSrv: POST /compile_spj
        JS->>JSrv: POST /judge (with SPJ)
    end

    JSrv-->>JS: JudgeServerResponse (test case results)
    JS->>JS: parseJudgeResponse()<br/>0=AC -1=WA 1=TLE 3=MLE 4=RE 5=SE
    JS->>JS: Take maxTime, maxMemory, first non-zero status
    JS-->>JC: JudgeResultMessage

    JC->>MQ: publish to judge.exchange<br/>routingKey=judge.result

    Note over User, CF: === Phase 4: Result Consumption ===
    MQ->>RC: consume judge.result.queue
    RC->>DB: UPDATE submission<br/>(status, timeCost, memoryCost, judgeInfo)
    RC->>PF: updateProblemStats(problemId, isAccepted)
    RC->>CF: updateRankStats(userId, competitionId, problemId, status)

    alt success
        RC->>MQ: basicAck (manual)
    else failure
        RC->>MQ: basicNack (requeue, max 3 retries)
    end

    Note over MQ: Compensation: @Scheduled every 30s<br/>Scan PENDING/JUDGING >2h old<br/>Resend to RabbitMQ
```

## 2. 排行榜更新流程

### 2.1 实时更新（updateAcmStats）

> **参与者说明**: RC=JudgeResultConsumer, CC=CompetitionController, CS=CompetitionServiceImpl, DB=MySQL, RS=RankingService

```mermaid
sequenceDiagram
    participant RC
    participant CC
    participant CS
    participant DB
    participant RS
    participant Redis

    RC->>CC: Feign: updateRankStats(userId, compId, probId, status)
    CC->>CS: updateAcmStats(userId, compId, probId, status)

    CS->>DB: Query Competition
    CS->>CS: isInFreezePeriod(comp)<br/>= now > (endTime - freezeMinute)

    alt Frozen period (frozen = true)
        Note over CS, Redis: FREEZE: Skip MySQL, only Redis counter
        alt status != CE
            CS->>RS: incrFreezeAttempt(compId, userId, probId)
            RS->>Redis: HINCRBY freeze:{compId}:{userId} probId +1
            Redis-->>RS: OK
        end
        CS-->>CC: return (early exit)
    else Normal period (frozen = false)
        Note over CS, DB: NORMAL: Update MySQL stats
        CS->>DB: Query CompetitionSubmissionStats<br/>(userId, compId, probId)

        alt stats == null (first attempt)
            CS->>DB: INSERT stats (isAc=false, wrongAttempts=0)
        else stats.isAc == true (already AC)
            CS-->>CC: return (ignore)
        end

        alt status == AC
            CS->>DB: UPDATE stats: isAc=true, acTime=currentMinute
            CS->>DB: UPDATE participation:<br/>solved_count+1, total_penalty+=penalty
        else status != CE
            CS->>DB: UPDATE stats: wrongAttempts+1
        end

        Note over CS, Redis: Sync Redis ranking (best-effort)
        CS->>DB: Query Participation (solvedCount, totalPenalty)
        CS->>RS: updateRank(compId, userId, solved, penalty)
        RS->>Redis: ZADD competition:rank:{compId}<br/>score = solved*1000000 - penalty
        CS->>RS: setProblemStatus(compId, userId, probId, status)
        RS->>Redis: HSET competition:stats:{compId}:{userId}<br/>probId -> status (TTL 24h)
    end
```

### 2.2 读取排行榜（getRanklist）

> **参与者说明**: User=用户, CC=CompetitionController, CS=CompetitionServiceImpl, DB=MySQL, RS=RankingService, UF=UserFeignClient

```mermaid
sequenceDiagram
    actor User
    participant CC
    participant CS
    participant DB
    participant RS
    participant Redis
    participant UF

    User->>CC: GET /competitions/{id}/ranklist?current=1&size=20
    CC->>CS: getRanklist(compId, current, size)

    CS->>DB: Query Competition (check freeze)
    CS->>CS: isFrozen = isInFreezePeriod(comp)

    CS->>DB: Query ALL Participations<br/>WHERE competition_id = compId
    Note over CS: totalParticipants = allParts.size()

    CS->>RS: getRankList(compId, 0, totalParticipants)
    RS->>Redis: ZREVRANGE competition:rank:{compId}<br/>0 totalParticipants WITHSCORES

    alt Redis hit (has ranking data)
        Note over CS: Merge all participants + Redis ranks

        CS->>UF: Batch fetch user info (username, realName)
        UF-->>CS: user info

        loop For each Redis ranked user
            CS->>CS: ordered.add(partMap.get(uid))<br/>Sort by Redis score DESC
        end
        Note over CS: Append users NOT in Redis<br/>(first submit during freeze / no submits)<br/>Sort by userId ASC

        CS->>DB: Query CompetitionSubmissionStats<br/>WHERE compId AND userId IN orderedUserIds

        loop For each in ordered
            CS->>CS: Fill submissionStats<br/>(userId -> Map<probId, stats>)
        end

        alt During freeze period
            loop For each page record
                CS->>RS: getFreezeAttempts(compId, userId)
                RS->>Redis: HGETALL freeze:{compId}:{userId}
                Redis-->>RS: {probId -> count}
            end
            Note over CS: Build frozenAttempts<br/>for frontend blue +N display
        end

        CS->>CS: In-memory pagination subList(start, start+size)
        CS-->>CC: {records, total, isFrozen, frozenAttempts}

    else Redis empty / error -> fallback MySQL
        CS->>DB: getRanklistPage(current, size)<br/>ORDER BY solved_count DESC, total_penalty ASC
        CS->>DB: Query CompetitionSubmissionStats
        CS-->>CC: {records, total, isFrozen, frozenAttempts}
    end

    CC-->>User: Ranking data + freeze status
```

### 2.3 解封（unfreeze）

> **参与者说明**: Admin=管理员, CC=CompetitionController, CS=CompetitionServiceImpl, SF=SubmissionFeignClient, DB=MySQL, RS=RankingService

```mermaid
sequenceDiagram
    actor Admin
    participant CC
    participant CS
    participant SF
    participant DB
    participant RS
    participant Redis

    Admin->>CC: POST /admin/competitions/{id}/unfreeze
    CC->>CS: unfreeze(compId)

    Note over CS, SF: Step 1: Rebuild all stats from submissions
    CS->>SF: exportSubmissions(compId)
    SF-->>CS: All submissions (sorted by time)

    CS->>DB: DELETE all CompetitionSubmissionStats<br/>WHERE competition_id = compId
    CS->>DB: UPDATE participations<br/>SET solved_count=0, total_penalty=0

    loop For each submission (time order)
        CS->>CS: replaySubmission(userId, compId, probId, status, time)
        Note over CS: Same logic as updateAcmStats<br/>but skip freeze check
        CS->>DB: INSERT/UPDATE stats
        CS->>DB: UPDATE participation (solvedCount, totalPenalty)
    end

    Note over CS, DB: Step 2: Clear freeze flag
    CS->>DB: UPDATE competition SET freeze_minute=0

    Note over CS, Redis: Step 3: Clear freeze counters
    CS->>RS: clearFreezeAttempts(compId)
    RS->>Redis: DELETE freeze:{compId}:*

    Note over CS, Redis: Step 4: Rebuild Redis rankings
    CS->>DB: Query ALL Participations
    loop For each participant
        CS->>RS: updateRank(compId, userId, solvedCount, totalPenalty)
        RS->>Redis: ZADD competition:rank:{compId}
    end

    CS-->>CC: Result.success("Unfreeze complete")
    CC-->>Admin: Unfreeze done
```

## 关键数据结构

| 数据 | 存储位置 | 格式 | 说明 |
|------|---------|------|------|
| 提交记录 | MySQL `submission` | 全字段 | 含 status, timeCost, memoryCost, judgeInfo |
| 单题统计 | MySQL `competition_submission_stats` | userId+compId+probId | isAc, acTime, wrongAttempts (封榜期间不更新) |
| 参赛成绩 | MySQL `participation` | userId+compId | solvedCount, totalPenalty (封榜期间不更新) |
| 排行榜 | Redis ZSET `competition:rank:{compId}` | userId -> score | score = solved x 10^6 - penalty |
| 题目状态 | Redis Hash `competition:stats:{compId}:{userId}` | probId -> status | TTL 24h, 快速判断是否已 AC |
| 封榜计数 | Redis Hash `competition:freeze:{compId}:{userId}` | probId -> attempts | TTL 48h, 前端蓝底 +N 数据源 |

## 封榜状态流转

```
Contest Created -> Started -> ... -> freezeStart = endTime - freezeMinute
                                          |
                                     now > freezeStart ?
                                          | YES
                               isInFreezePeriod = true
                               |  updateAcmStats: Redis only
                               |  getRanklist: frozen ranking
                               |  Frontend: blue cell "+N"
                                          |
                              Admin calls unfreeze()
                                          |
                               Rebuild all MySQL stats
                               freezeMinute = 0
                               Clear Redis freeze counters
                               Rebuild Redis rankings
                                          |
                               isInFreezePeriod = false
```
