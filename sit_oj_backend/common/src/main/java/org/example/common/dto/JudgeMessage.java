package org.example.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 判题请求消息体 - 通过 RabbitMQ 从 submission 发送到 judge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeMessage implements Serializable {
    private Integer submissionId;
    private Integer userId;
    private Integer problemId;
    private Integer competitionId;  // nullable，非比赛提交则为 null
    private String codeContent;
    private String language;
}
