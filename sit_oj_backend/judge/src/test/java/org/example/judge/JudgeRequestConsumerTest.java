package org.example.judge;

import org.example.common.dto.JudgeMessage;
import org.example.common.dto.JudgeResultMessage;
import org.example.judge.messaging.JudgeRequestConsumer;
import org.example.judge.service.JudgeService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JudgeRequestConsumerTest {
    @Test
    void resultNackLeavesRequestUnacknowledged() {
        JudgeService service = mock(JudgeService.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        JudgeMessage request = JudgeMessage.builder().submissionId(7).build();
        when(service.processJudge(request)).thenReturn(
                JudgeResultMessage.builder().submissionId(7).status("AC").build());
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(false, "broker rejected"));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(JudgeResultMessage.class),
                any(CorrelationData.class));

        assertThrows(IllegalStateException.class,
                () -> new JudgeRequestConsumer(service, rabbit).handleJudgeRequest(request));
    }
}
