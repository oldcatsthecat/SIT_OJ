package org.example.submission;

import org.example.common.dto.JudgeMessage;
import org.example.submission.entity.Submission;
import org.example.submission.mapper.SubmissionMapper;
import org.example.submission.messaging.JudgeOutboxPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class JudgeOutboxPublisherTest {

    @Test
    void brokerNackKeepsSubmissionForNextAttempt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SubmissionMapper mapper = mock(SubmissionMapper.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        Submission submission = new Submission();
        submission.setSubmissionId(7);
        submission.setUserId(8);
        submission.setProblemId(9);
        submission.setStatus("Pending");
        submission.setCodeContent("print(1)");
        submission.setLanguage("Python");
        when(jdbc.queryForList(anyString(), eq(Integer.class))).thenReturn(List.of(7));
        when(mapper.selectById(7)).thenReturn(submission);
        AtomicBoolean acknowledge = new AtomicBoolean();
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(acknowledge.get(), null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(JudgeMessage.class),
                any(CorrelationData.class));

        JudgeOutboxPublisher publisher = new JudgeOutboxPublisher(jdbc, mapper, rabbit);
        publisher.publishPending();
        verify(jdbc, never()).update(startsWith("UPDATE judge_outbox"), eq(7));

        acknowledge.set(true);
        publisher.publishPending();
        verify(jdbc).update(startsWith("UPDATE judge_outbox"), eq(7));
        verify(rabbit, times(2)).convertAndSend(eq("judge.exchange"), eq("judge.request.v2"),
                any(JudgeMessage.class), any(CorrelationData.class));
    }
}
