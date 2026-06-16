package org.example.submission.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.submission.entity.Submission;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

public interface SubmissionService extends IService<Submission> {

    Submission handleSubmission(Submission submission);

    IPage<Submission> getSubmissionList(Integer current, Integer size, Integer problemId, String role , Integer currentUserId);

    Map<String, Object> getCompetitionStats(Integer competitionId);

    Integer getUserIdFromToken(String token);

    String getUserRoleFromToken(String token);

    IPage<Submission> getCompetitionSubmissions(Integer current, Integer size, Integer competitionId, Integer userId , String role);
}