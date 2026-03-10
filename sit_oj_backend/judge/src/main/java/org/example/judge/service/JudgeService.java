package org.example.judge.service;

import org.example.common.dto.JudgeResultResponse;
import org.example.common.dto.JudgeServerResponse;

public interface JudgeService {
    // 顶层流程控制：从 ID 开始到返回结果包
    JudgeResultResponse processJudge(Integer submissionId);

    // 底层判题机通讯
    JudgeServerResponse<Object> sendToJudgeServer(String code, String language, String testCaseId, Integer timeLimit, Integer memoryLimit);

    JudgeServerResponse<Object> sendToJudgeServerSpj(String code, String language, String testCaseId, Integer timeLimit, Integer memoryLimit, String spj_src);
}