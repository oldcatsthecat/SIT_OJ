package org.example.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.competition.entity.Participation;

import java.util.List;

@Mapper
public interface ParticipationMapper extends BaseMapper<Participation> {

    /**
     * 获取所有报名者的比赛排名（ACM模式：解题数降序，罚时升序）
     * 关联 users 表获取姓名，未提交者也显示在榜单中
     */
    @Select("SELECT p.*, u.username , u.real_name FROM participations p " +
            "LEFT JOIN users u ON p.user_id = u.id " +
            "WHERE p.competition_id = #{competitionId} AND p.is_practice = 0 " +
            "ORDER BY p.solved_count DESC, p.total_penalty ASC, p.user_id ASC")
    List<Participation> getRanklist(@Param("competitionId") Integer competitionId);

    /**
     * 分页获取比赛排名
     */
    @Select("SELECT p.*, u.username , u.real_name FROM participations p " +
            "LEFT JOIN users u ON p.user_id = u.id " +
            "WHERE p.competition_id = #{competitionId} AND p.is_practice = 0 " +
            "ORDER BY p.solved_count DESC, p.total_penalty ASC, p.user_id ASC")
    IPage<Participation> getRanklistPage(Page<Participation> page, @Param("competitionId") Integer competitionId);
}
