package org.example.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 判题结果消息体 - 通过 RabbitMQ 从 judge 发送回 submission
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeResultMessage implements Serializable {
    private Integer submissionId;
    private Integer userId;
    private Integer problemId;
    private Integer competitionId;
    private String status;         // AC, WA, CE, TLE, MLE, RE, SE
    private Integer timeCost;      // 最大耗时(ms)
    private Integer memoryCost;    // 最大内存(KB)
    private String judgeInfo;      // JSON 字符串，测试点详情
    private String errorMessage;
}
