package org.example.competition.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Map;

@Data
@TableName("participations")
public class Participation {

    private Integer userId;
    private Integer competitionId;

    private Integer solvedCount;   // 解题数量
    private Integer totalPenalty;  // 总罚时（分钟）


    @TableField(exist = false)
    private String username;       // 用于排名展示时的用户名
    @TableField(exist = false)
    private String realName;       // 用于排名展示时的用户名

    @TableField(exist = false)
    private Map<Integer, CompetitionSubmissionStats> submissionStats;

}