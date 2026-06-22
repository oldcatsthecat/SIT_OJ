package org.example.competition.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.common.utils.Result;
import org.example.competition.entity.Competition;
import org.example.competition.entity.Participation;

import java.util.List;
import java.util.Map;

public interface CompetitionService extends IService<Competition> {
    // 获取比赛详情（包含关联题目和用户状态）
    Competition getCompetitionDetail(Integer competitionId, Integer userId);

    // 获取排名列表（分页），返回 {records: [...], total: N}
    Map<String, Object> getRanklist(Integer competitionId, Integer current, Integer size);

    // 核心：处理比赛提交后的数据更新（由判题服务回调）
    void updateAcmStats(Integer userId, Integer competitionId, Integer problemId, String status, String submissionTime);

    Result handleSubmission(Integer userId, Integer competitionId, Integer problemId, String code, String language);

    boolean addProblems(Integer competitionId, List<Integer> problemIds);

    Result<Map<String, Object>> getProblemStats(Integer competitionId);

    IPage<Competition> getListWithRegisterStatus(Integer userId, Integer current, Integer size);

    /** 检查比赛是否处于封榜期 */
    boolean isFrozen(Integer competitionId);

    /** 管理员手动解封比赛（设置 freezeMinute=0 并重建 Redis 排名） */
    Result unfreeze(Integer competitionId);

    /** 导出比赛数据为 ICPC Resolver NDJSON 格式 */
    String exportForResolver(Integer competitionId);

}