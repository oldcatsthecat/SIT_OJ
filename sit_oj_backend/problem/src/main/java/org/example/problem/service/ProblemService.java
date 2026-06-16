package org.example.problem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.problem.entity.Problem;

public interface ProblemService extends IService<Problem> {
    void updateStats(Integer problemId, boolean isAccepted);

    IPage<Problem> getAvailableProblemsForUser(Integer current, Integer size, Integer problemId, Integer userId, String role);
}