package org.example.submission;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.common.security.AuditResultAdvice;
import org.example.common.security.RequestAuditFilter;
import org.example.submission.config.JwtProperties;
import org.example.submission.controller.SubmissionController;
import org.example.submission.entity.Submission;
import org.example.submission.feign.ProblemFeignClient;
import org.example.submission.service.SubmissionRateLimiter;
import org.example.submission.service.SubmissionService;
import org.example.submission.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SubmissionAuditTest {
    final String secret = UUID.randomUUID().toString() + UUID.randomUUID();
    final List<Map<String, Object>> records = new ArrayList<>();
    final SubmissionService service = mock(SubmissionService.class);
    MockMvc mvc;

    @BeforeEach void setup() {
        var controller = new SubmissionController();
        var problems = mock(ProblemFeignClient.class);
        ReflectionTestUtils.setField(controller, "submissionService", service);
        ReflectionTestUtils.setField(controller, "problemFeignClient", problems);
        ReflectionTestUtils.setField(controller, "rateLimiter", mock(SubmissionRateLimiter.class));
        when(problems.getProblemById(22)).thenReturn(Map.of("problemId", 22, "isPublic", true));
        // Use the production JWT verifier for request authentication; only persistence is mocked.
        var properties = new JwtProperties(); properties.setSecret(secret);
        var jwt = new JwtUtils(); ReflectionTestUtils.setField(jwt, "jwtProperties", properties);
        when(service.getUserIdFromToken(anyString())).thenAnswer(call -> jwt.getUserIdFromToken(call.getArgument(0)));
        when(service.getUserRoleFromToken(anyString())).thenAnswer(call -> jwt.getRoleFromToken(call.getArgument(0)));
        when(service.handleSubmission(any())).thenAnswer(call -> {
            Submission saved = call.getArgument(0);
            assertEquals(77, saved.getUserId());
            saved.setSubmissionId(551); return saved;
        });
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new AuditResultAdvice())
                .addFilters(new RequestAuditFilter(secret, UUID.randomUUID().toString(), "submission", records::add)).build();
    }
    String token(String key) {
        return Jwts.builder().setSubject("account-A").claim("id", 77).claim("role", "USER")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8))).compact();
    }
    @Test void httpSubmitWithVictimIdSavesAsActorAndRecordsAttempt() throws Exception {
        mvc.perform(post("/submissions/submit").header("Authorization", "Bearer " + token(secret))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":88,\"problemId\":22,\"language\":\"py\",\"codeContent\":\"print(1)\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(77))
                .andExpect(jsonPath("$.data.submissionId").value(551));
        var event = records.get(0);
        assertEquals(77, event.get("actorUserId")); assertEquals(88L, event.get("claimedUserId"));
        assertEquals(true, event.get("userIdMismatch")); assertEquals(551L, event.get("submissionId"));
        assertEquals(200, event.get("businessCode")); assertFalse(event.toString().contains("print(1)"));
    }
    @Test void forgedAuthFailsBeforePersistenceAndHasNoAttributedAccount() throws Exception {
        mvc.perform(post("/submissions/submit").header("Authorization", token(UUID.randomUUID().toString() + UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":88,\"problemId\":22,\"language\":\"py\",\"codeContent\":\"print(1)\"}"))
                .andExpect(status().isUnauthorized());
        verify(service, never()).handleSubmission(any());
        assertEquals("INVALID", records.get(0).get("authStatus"));
        assertNull(records.get(0).get("actorUserId")); assertEquals(401, records.get(0).get("httpStatus"));
    }
}
