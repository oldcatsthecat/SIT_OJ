package org.example.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.submission.entity.Submission;

import java.util.List;
import java.util.Map;

public interface SubmissionMapper extends BaseMapper<Submission> {

    @Select("<script>" +
            "SELECT s.* FROM submissions s " +
            "INNER JOIN problems p ON s.problem_id = p.problem_id " +
            "WHERE p.is_public = 1 " + // 核心过滤：只看公开题目的提交
            "<if test='problemId != null'>" +
            "  AND s.problem_id = #{problemId} " +
            "</if>" +
            "ORDER BY s.submission_id DESC" +
            "</script>")
    IPage<Submission> selectPublicSubmissions(Page<Submission> page, @Param("problemId") Integer problemId);

    @Select("SELECT problem_id, " +
            "COUNT(CASE WHEN status = 'AC' THEN 1 END) as acceptedNum, " +
            "COUNT(*) as totalNum " +
            "FROM submissions " +
            "WHERE competition_id = #{competitionId} " +
            "GROUP BY problem_id")
    List<Map<String, Object>> getCompetitionStats(@Param("competitionId") Integer competitionId);

}
