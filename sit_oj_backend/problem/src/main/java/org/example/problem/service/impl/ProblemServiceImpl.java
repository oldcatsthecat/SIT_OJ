package org.example.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.problem.entity.Problem;
import org.example.problem.mapper.ProblemMapper;
import org.example.problem.service.ProblemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service // 别忘了这个注解，否则 Controller 无法注入
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {
    // 在 Problem 微服务的 ServiceImpl 中
    @Transactional
    public void updateStats(Integer problemId, boolean isAccepted) {
        this.update(new UpdateWrapper<Problem>()
                .lambda()
                .eq(Problem::getProblemId, problemId)
                .setSql("submission_number = submission_number + 1")
                .setSql(isAccepted, "accepted_number = accepted_number + 1")
        );
    }

    public List<Problem> getAvailableProblemsForUser(Integer problemId, Integer userId, String role) {
        List<Problem> problems;

        // 1. 获取原始题目列表
        if ("ADMIN".equals(role)) {
            // 管理员看全部
            problems = (problemId != null)
                    ? this.lambdaQuery().eq(Problem::getProblemId, problemId).list()
                    : this.list();
        } else {
            // 普通用户调用你原来的 selectPublicProblems
            problems = this.baseMapper.selectPublicProblems(problemId);
        }

        // 2. 如果用户已登录，处理 isSolved 字段
        if (userId != null && !problems.isEmpty()) {
            // 查询该用户所有已通过的题目 ID
            List<Integer> acIds = this.baseMapper.selectAcceptedProblemIds(userId);

            if (acIds != null && !acIds.isEmpty()) {
                // 将 List 转为 Set 提高查找效率
                java.util.Set<Integer> acSet = new java.util.HashSet<>(acIds);

                // 遍历题目列表，匹配状态
                problems.forEach(p -> {
                    if (acSet.contains(p.getProblemId())) {
                        p.setIsSolved(true);
                    } else {
                        p.setIsSolved(false);
                    }
                });
            }
        }

        return problems;
    }
}