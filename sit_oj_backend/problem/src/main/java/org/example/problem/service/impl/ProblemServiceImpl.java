package org.example.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.problem.entity.Problem;
import org.example.problem.mapper.ProblemMapper;
import org.example.problem.service.ProblemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {

    @Transactional
    public void updateStats(Integer problemId, boolean isAccepted) {
        this.update(new UpdateWrapper<Problem>()
                .lambda()
                .eq(Problem::getProblemId, problemId)
                .setSql("submission_number = submission_number + 1")
                .setSql(isAccepted, "accepted_number = accepted_number + 1")
        );
    }

    @Override
    public IPage<Problem> getAvailableProblemsForUser(Integer current, Integer size,
                                                       Integer problemId, Integer userId, String role) {
        Page<Problem> page = new Page<>(current, size);

        // 1. 分页查询题目列表
        if ("ADMIN".equals(role)) {
            // 管理员看全部
            if (problemId != null) {
                this.lambdaQuery().eq(Problem::getProblemId, problemId).page(page);
            } else {
                this.lambdaQuery().page(page);
            }
        } else {
            // 普通用户：只显示公开题目
            LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Problem::getIsPublic, true);
            if (problemId != null) {
                wrapper.eq(Problem::getProblemId, problemId);
            }
            this.baseMapper.selectPage(page, wrapper);
        }

        // 2. 如果用户已登录，处理 isSolved 字段
        if (userId != null && !page.getRecords().isEmpty()) {
            List<Integer> acIds = this.baseMapper.selectAcceptedProblemIds(userId);
            if (acIds != null && !acIds.isEmpty()) {
                java.util.Set<Integer> acSet = new java.util.HashSet<>(acIds);
                page.getRecords().forEach(p -> p.setIsSolved(acSet.contains(p.getProblemId())));
            }
        }

        return page;
    }
}