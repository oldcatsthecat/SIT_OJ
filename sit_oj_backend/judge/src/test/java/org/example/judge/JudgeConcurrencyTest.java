package org.example.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.dto.JudgeMessage;
import org.example.common.dto.JudgeServerResponse;
import org.example.judge.feign.ProblemFeignClient;
import org.example.judge.service.impl.JudgeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JudgeConcurrencyTest {

    @Test
    void thirdRequestWaitsUntilOneOfTwoJudgesFinishes() throws Exception {
        CountDownLatch twoActive = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch thirdStarted = new CountDownLatch(1);
        CountDownLatch thirdEntered = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        JudgeServiceImpl service = new JudgeServiceImpl() {
            @Override
            public JudgeServerResponse<Object> sendToJudgeServer(String code, String language,
                    String testCaseId, Integer timeLimit, Integer memoryLimit) {
                int count = active.incrementAndGet();
                if (count == 3) thirdEntered.countDown();
                maximum.accumulateAndGet(count, Math::max);
                twoActive.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    return JudgeServerResponse.<Object>builder().err("SystemError").build();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    active.decrementAndGet();
                }
            }
        };
        ProblemFeignClient problemClient = mock(ProblemFeignClient.class);
        when(problemClient.getProblemById(anyInt())).thenReturn(Map.of(
                "timeLimit", 1000, "memoryLimit", 128, "judgeType", 0));
        ReflectionTestUtils.setField(service, "problemFeignClient", problemClient);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> first = executor.submit(() -> service.processJudge(message(1)));
            Future<?> second = executor.submit(() -> service.processJudge(message(2)));
            assertTrue(twoActive.await(5, TimeUnit.SECONDS));
            Future<?> third = executor.submit(() -> {
                thirdStarted.countDown();
                return service.processJudge(message(3));
            });
            assertTrue(thirdStarted.await(5, TimeUnit.SECONDS));
            assertFalse(thirdEntered.await(200, TimeUnit.MILLISECONDS));
            assertEquals(2, active.get());
            assertEquals(2, maximum.get());
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            third.get(5, TimeUnit.SECONDS);
            assertEquals(2, maximum.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private JudgeMessage message(int id) {
        return JudgeMessage.builder().submissionId(id).problemId(id)
                .codeContent("int main() {}").language("C++").build();
    }
}
