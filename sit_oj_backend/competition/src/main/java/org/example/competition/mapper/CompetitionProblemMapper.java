package org.example.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.competition.entity.CompetitionProblem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CompetitionProblemMapper extends BaseMapper<CompetitionProblem> {
    @Select("SELECT DISTINCT problem_id FROM submissions " +
            "WHERE user_id = #{userId} " +
            "AND competition_id = #{competitionId} " +
            "AND (status = 'AC' OR status = 'ACCEPTED')")
    List<Integer> selectAcceptedProblemIdsInCompetition(
            @Param("userId") Integer userId,
            @Param("competitionId") Integer competitionId
    );
}