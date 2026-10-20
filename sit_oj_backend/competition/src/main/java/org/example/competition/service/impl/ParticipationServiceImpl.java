package org.example.competition.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.common.utils.Result;
import org.example.competition.entity.Competition;
import org.example.competition.entity.Participation;
import org.example.competition.mapper.CompetitionMapper;
import org.example.competition.mapper.ParticipationMapper;
import org.example.competition.service.ParticipationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ParticipationServiceImpl extends ServiceImpl<ParticipationMapper, Participation> implements ParticipationService {

    @Autowired
    private CompetitionMapper competitionMapper;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> registerUser(Integer cid, Integer userId) {

        Competition competition = competitionMapper.selectById(cid);
        if (competition == null) {
            return Result.error("比赛不存在");
        }

        if (checkRegistration(cid, userId)) {
            return Result.error("您已经报名过该比赛");
        }

        // A practice entry grants access after the contest without joining its ranklist.
        Participation p = new Participation();
        p.setUserId(userId);
        p.setCompetitionId(cid);
        p.setSolvedCount(0);
        p.setTotalPenalty(0);
        p.setIsPractice(!LocalDateTime.now().isBefore(competition.getEndTime()));
        this.baseMapper.insert(p);

        return Result.success("报名成功");
    }
    @Override
    public boolean checkRegistration(Integer cid, Integer userId) {
        Long count = this.baseMapper.selectCount(new LambdaQueryWrapper<Participation>()
                .eq(Participation::getUserId, userId)
                .eq(Participation::getCompetitionId, cid));
        return count > 0;
    }
}
