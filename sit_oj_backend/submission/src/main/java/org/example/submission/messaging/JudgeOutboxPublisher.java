package org.example.submission.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.JudgeMessage;
import org.example.submission.entity.Submission;
import org.example.submission.mapper.SubmissionMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.example.submission.config.RabbitMQConfig.JUDGE_EXCHANGE;
import static org.example.submission.config.RabbitMQConfig.JUDGE_REQUEST_ROUTING_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeOutboxPublisher {
    private final JdbcTemplate jdbcTemplate;
    private final SubmissionMapper submissionMapper;
    private final RabbitTemplate rabbitTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverLegacyPending() {
        // 为升级前已落库但尚无待发送记录的提交补建记录，只在启动时执行一次。
        jdbcTemplate.update("INSERT IGNORE INTO judge_outbox (submission_id) " +
                "SELECT submission_id FROM submissions WHERE status IN ('Pending', 'JUDGING')");
        publishPending();
    }

    @Scheduled(initialDelay = 2000, fixedDelay = 2000)
    public synchronized void publishPending() {
        List<Integer> ids = jdbcTemplate.queryForList(
                "SELECT submission_id FROM judge_outbox WHERE published_at IS NULL " +
                "ORDER BY submission_id LIMIT 100", Integer.class);
        for (Integer id : ids) {
            Submission submission = submissionMapper.selectById(id);
            if (submission == null || !("Pending".equals(submission.getStatus()) ||
                    "JUDGING".equals(submission.getStatus()))) {
                markPublished(id);
                continue;
            }
            JudgeMessage message = JudgeMessage.builder()
                    .submissionId(id)
                    .userId(submission.getUserId())
                    .problemId(submission.getProblemId())
                    .competitionId(submission.getCompetitionId())
                    .codeContent(submission.getCodeContent())
                    .language(submission.getLanguage())
                    .build();
            try {
                CorrelationData confirmation = new CorrelationData(id.toString());
                rabbitTemplate.convertAndSend(JUDGE_EXCHANGE, JUDGE_REQUEST_ROUTING_KEY,
                        message, confirmation);
                if (!confirmation.getFuture().get(10, TimeUnit.SECONDS).isAck() ||
                        confirmation.getReturned() != null) {
                    log.warn("判题请求未被 RabbitMQ 确认: submissionId={}", id);
                    break;
                }
                markPublished(id);
            } catch (Exception e) {
                log.warn("判题请求发送失败，稍后重试: submissionId={}", id, e);
                break;
            }
        }
    }

    private void markPublished(Integer id) {
        jdbcTemplate.update("UPDATE judge_outbox SET published_at = NOW() " +
                "WHERE submission_id = ? AND published_at IS NULL", id);
    }
}
