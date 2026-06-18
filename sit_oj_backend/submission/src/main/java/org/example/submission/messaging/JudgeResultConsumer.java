package org.example.submission.messaging;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.JudgeResultMessage;
import org.example.submission.entity.Submission;
import org.example.submission.feign.CompetitionFeignClient;
import org.example.submission.feign.ProblemFeignClient;
import org.example.submission.service.SubmissionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.example.submission.config.RabbitMQConfig.JUDGE_RESULT_QUEUE;

/**
 * 判题结果消费者：接收 judge 模块返回的判题结果，更新数据库和比赛排名
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeResultConsumer {

    private final SubmissionService submissionService;
    private final ProblemFeignClient problemFeignClient;
    private final CompetitionFeignClient competitionFeignClient;

    @RabbitListener(queues = JUDGE_RESULT_QUEUE, concurrency = "2-4")
    public void handleJudgeResult(JudgeResultMessage result, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            log.info("收到判题结果: submissionId={}, status={}", result.getSubmissionId(), result.getStatus());

            // 1. 更新 submission 状态和判题详情
            Submission submission = new Submission();
            submission.setSubmissionId(result.getSubmissionId());
            submission.setStatus(result.getStatus());
            submission.setTimeCost(result.getTimeCost());
            submission.setMemoryCost(result.getMemoryCost());
            submission.setJudgeInfo(result.getJudgeInfo());
            submission.setErrorMessage(result.getErrorMessage());
            submissionService.updateById(submission);

            // 2. 如果是比赛提交，回调 Competition 模块更新 ACM 分数
            if (result.getCompetitionId() != null) {
                competitionFeignClient.updateRankStats(
                        result.getUserId(),
                        result.getCompetitionId(),
                        result.getProblemId(),
                        result.getStatus()
                );
            }

            // 3. 更新题目提交/通过统计
            boolean isAccepted = "AC".equals(result.getStatus());
            problemFeignClient.updateProblemStats(result.getProblemId(), isAccepted);

            // 手动 ACK
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("处理判题结果失败: submissionId={}, error={}", result.getSubmissionId(), e.getMessage(), e);
            try {
                // NACK 重新入队（最多重试 3 次，由 application.yml 中 RabbitMQ 配置控制）
                channel.basicNack(tag, false, true);
            } catch (IOException ex) {
                log.error("NACK 失败", ex);
            }
        }
    }
}
