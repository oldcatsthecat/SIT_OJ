package org.example.competition.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("competition_submission_stats")
public class CompetitionSubmissionStats {
    private Integer userId;
    private Integer competitionId;
    private Integer problemId;

    private Boolean isAc;          // 该题是否已通过
    private Integer wrongAttempts; // AC前的错误尝试次数
    private Integer acTime;        // 从比赛开始到AC的分钟数
}