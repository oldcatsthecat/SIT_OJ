package org.example.judge.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.JudgeMessage;
import org.example.common.dto.JudgeResultMessage;
import org.example.judge.service.JudgeService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 判题请求消费者：从 RabbitMQ 消费判题请求，调用判题机，返回结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeRequestConsumer {

    private final JudgeService judgeService;
    private final RabbitTemplate rabbitTemplate;

    private static final String JUDGE_EXCHANGE = "judge.exchange";
    private static final String JUDGE_RESULT_ROUTING_KEY = "judge.result";

    @RabbitListener(queues = {"judge.request.queue.v2", "judge.request.queue"})
    public void handleJudgeRequest(JudgeMessage message) throws Exception {
        log.info("收到判题请求: submissionId={}, problemId={}, language={}",
                message.getSubmissionId(), message.getProblemId(), message.getLanguage());

        // 调用判题逻辑（内部调用外部判题机）
        JudgeResultMessage result = judgeService.processJudge(message);

        // 发送结果到结果队列，由 submission 模块消费
        CorrelationData confirmation = new CorrelationData(message.getSubmissionId().toString());
        rabbitTemplate.convertAndSend(JUDGE_EXCHANGE, JUDGE_RESULT_ROUTING_KEY, result, confirmation);
        if (!confirmation.getFuture().get(10, TimeUnit.SECONDS).isAck() ||
                confirmation.getReturned() != null) {
            throw new IllegalStateException("判题结果未被 RabbitMQ 确认: " + message.getSubmissionId());
        }
        log.info("判题完成并发送结果: submissionId={}, status={}", result.getSubmissionId(), result.getStatus());
    }
}
