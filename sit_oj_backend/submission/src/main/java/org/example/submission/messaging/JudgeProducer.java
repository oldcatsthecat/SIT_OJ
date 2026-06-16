package org.example.submission.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.JudgeMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import static org.example.submission.config.RabbitMQConfig.JUDGE_EXCHANGE;
import static org.example.submission.config.RabbitMQConfig.JUDGE_REQUEST_ROUTING_KEY;

/**
 * 判题请求生产者：将提交推送到 RabbitMQ 判题队列
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendJudgeRequest(JudgeMessage message) {
        log.info("发送判题请求到 RabbitMQ: submissionId={}, problemId={}, language={}",
                message.getSubmissionId(), message.getProblemId(), message.getLanguage());
        rabbitTemplate.convertAndSend(JUDGE_EXCHANGE, JUDGE_REQUEST_ROUTING_KEY, message);
    }
}
