package org.example.competition.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("competition_problems")
public class CompetitionProblem {
    private Integer competitionId;
    private Integer problemId;
}
