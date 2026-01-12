package org.example.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.competition.entity.Competition;

import java.util.List;

@Mapper
public interface CompetitionMapper extends BaseMapper<Competition> {

    // 查询中间表，获取该比赛关联的所有题目ID
    @Select("SELECT problem_id FROM competition_problems WHERE competition_id = #{competitionId}")
    List<Integer> selectProblemIdsByCompetitionId(@Param("competitionId") Integer competitionId);
}
