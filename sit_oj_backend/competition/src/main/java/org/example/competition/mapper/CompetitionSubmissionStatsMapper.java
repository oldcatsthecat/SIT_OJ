package org.example.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.competition.entity.CompetitionSubmissionStats;

import java.util.List;
import java.util.Map;

@Mapper
public interface CompetitionSubmissionStatsMapper extends BaseMapper<CompetitionSubmissionStats> {
    @Select("SELECT problem_id, " +
            "COUNT(CASE WHEN is_ac = TRUE THEN 1 END) as acceptedNum, " +
            "SUM(wrong_attempts + (CASE WHEN is_ac = TRUE THEN 1 ELSE 0 END)) as totalNum " +
            "FROM competition_submission_stats " +
            "WHERE competition_id = #{competitionId} " +
            "GROUP BY problem_id")
    List<Map<String, Object>> getStatsByCompetitionId(@Param("competitionId") Integer competitionId);
}