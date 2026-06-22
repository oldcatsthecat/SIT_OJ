package org.example.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.competition.entity.Participation;

import java.util.List;

@Mapper
public interface ParticipationMapper extends BaseMapper<Participation> {

    /**
     * 获取比赛排名（ACM模式：解题数降序，罚时升序）
     * 关联 users 表获真名，比赛使用真名
     */
    @Select("SELECT p.*, u.username , u.real_name FROM participations p " +
            "JOIN users u ON p.user_id = u.id " +
            "WHERE p.competition_id = #{competitionId} " +
            "AND EXISTS (SELECT 1 FROM competition_submission_stats s " +
            "WHERE s.user_id = p.user_id AND s.competition_id = p.competition_id) " +
            "ORDER BY p.solved_count DESC, p.total_penalty ASC")
    List<Participation> getRanklist(Integer competitionId);

    /**
     * 分页获取比赛排名
     */
    @Select("SELECT p.*, u.username , u.real_name FROM participations p " +
            "JOIN users u ON p.user_id = u.id " +
            "WHERE p.competition_id = #{competitionId} " +
            "AND EXISTS (SELECT 1 FROM competition_submission_stats s " +
            "WHERE s.user_id = p.user_id AND s.competition_id = p.competition_id) " +
            "ORDER BY p.solved_count DESC, p.total_penalty ASC")
    IPage<Participation> getRanklistPage(Page<Participation> page, Integer competitionId);
}