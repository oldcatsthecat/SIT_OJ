package org.example.problem.service;

import com.baomidou.mybatisplus.extension.service.IService; // 必须有这个导入
import org.example.problem.entity.Problem;

import java.util.List;

public interface ProblemService extends IService<Problem> {
    void updateStats(Integer problemId, boolean isAccepted);

    List<Problem> getAvailableProblemsForUser(Integer problemId, Integer userId, String role);
}