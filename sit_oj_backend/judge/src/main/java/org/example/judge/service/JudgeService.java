package org.example.judge.service;

import org.example.common.dto.JudgeMessage;
import org.example.common.dto.JudgeResultMessage;
import org.example.common.dto.JudgeResultResponse;
import org.example.common.dto.JudgeServerResponse;

public interface JudgeService {
    // 顶层流程控制：从 ID 开始到返回结果包（Feign 兼容保留）
    JudgeResultResponse processJudge(Integer submissionId);

    // RabbitMQ 消息入口：从消息体直接判题，返回 JudgeResultMessage
    JudgeResultMessage processJudge(JudgeMessage message);

    // 底层判题机通讯
    JudgeServerResponse<Object> sendToJudgeServer(String code, String language, String testCaseId, Integer timeLimit, Integer memoryLimit);

    JudgeServerResponse<Object> sendToJudgeServerSpj(String code, String language, String testCaseId, Integer timeLimit, Integer memoryLimit, String spj_src);
}