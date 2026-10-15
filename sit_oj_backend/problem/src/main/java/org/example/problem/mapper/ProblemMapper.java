package org.example.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.problem.entity.Problem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {
    @Select("SELECT COUNT(*) FROM competition_problems cp " +
            "JOIN competitions c ON c.competition_id = cp.competition_id " +
            "JOIN participations p ON p.competition_id = c.competition_id AND p.user_id = #{userId} " +
            "WHERE cp.problem_id = #{problemId} AND c.competition_id = #{competitionId} AND c.start_time <= #{now}")
    long countAuthorizedContestProblem(@Param("problemId") Integer problemId,
                                      @Param("competitionId") Integer competitionId,
                                      @Param("userId") Integer userId,
                                      @Param("now") LocalDateTime now);
    // 更新总提交数
    @Update("UPDATE problems SET submission_number = submission_number + 1 WHERE problem_id = #{problemId}")
    int incrementSubmission(@Param("problemId") Integer problemId);

    // 更新通过数
    @Update("UPDATE problems SET accepted_number = accepted_number + 1 WHERE problem_id = #{problemId}")
    int incrementAccepted(@Param("problemId") Integer problemId);


    @Select("<script>" +
            "SELECT p.* FROM problems p " +
            "WHERE p.is_public = 1 " +
            "<if test='problemId != null'>" +
            "  AND p.problem_id = #{problemId}" +
            "</if>" +
            "</script>")
    List<Problem> selectPublicProblems(@Param("problemId") Integer problemId);

    @Select("SELECT DISTINCT problem_id FROM submissions WHERE user_id = #{userId} AND status = 'AC'")
    List<Integer> selectAcceptedProblemIds(@Param("userId") Integer userId);

}
