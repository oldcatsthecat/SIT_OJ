package org.example.competition;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.competition.entity.Competition;
import org.example.competition.entity.CompetitionProblem;
import org.example.competition.entity.Participation;
import org.example.competition.feign.SubmissionFeignClient;
import org.example.competition.feign.UserFeignClient;
import org.example.competition.mapper.CompetitionMapper;
import org.example.competition.mapper.CompetitionProblemMapper;
import org.example.competition.mapper.ParticipationMapper;
import org.example.competition.service.impl.CompetitionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResolverSelectedParticipantsTest {
    @Test
    void exportsOnlySelectedTeamsAndTheirSubmissions() throws Exception {
        CompetitionServiceImpl service = new CompetitionServiceImpl();
        CompetitionMapper competitions = mock(CompetitionMapper.class);
        ParticipationMapper participations = mock(ParticipationMapper.class);
        CompetitionProblemMapper problems = mock(CompetitionProblemMapper.class);
        SubmissionFeignClient submissions = mock(SubmissionFeignClient.class);
        UserFeignClient users = mock(UserFeignClient.class);
        ReflectionTestUtils.setField(service, "baseMapper", competitions);
        ReflectionTestUtils.setField(service, "participationMapper", participations);
        ReflectionTestUtils.setField(service, "cpMapper", problems);
        ReflectionTestUtils.setField(service, "submissionFeignClient", submissions);
        ReflectionTestUtils.setField(service, "userFeignClient", users);

        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        Competition competition = new Competition();
        competition.setCompetitionId(7);
        competition.setCompetitionName("测试比赛");
        competition.setStartTime(start);
        competition.setEndTime(start.plusHours(2));
        when(competitions.selectById(7)).thenReturn(competition);
        Participation first = new Participation();
        first.setUserId(1);
        Participation second = new Participation();
        second.setUserId(2);
        when(participations.selectList(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> new ArrayList<>(List.of(first, second)));
        CompetitionProblem problem = new CompetitionProblem();
        problem.setProblemId(10);
        when(problems.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(problem));
        when(users.getUserById(any())).thenReturn(Map.of("username", "选手"));
        when(submissions.exportSubmissions(7)).thenReturn(List.of(
                submission(101, 1, start.plusMinutes(5)),
                submission(102, 2, start.plusMinutes(10))));

        String ndjson = service.exportForResolver(7, Map.of(1, "stars"), List.of(1));
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> events = ndjson.lines().map(line -> {
            try { return mapper.readTree(line); }
            catch (Exception e) { throw new AssertionError(e); }
        }).toList();
        assertTrue(events.stream().anyMatch(event -> event.path("type").asText().equals("teams")
                && event.path("id").asText().equals("1")));
        assertFalse(events.stream().anyMatch(event -> event.path("type").asText().equals("teams")
                && event.path("id").asText().equals("2")));
        assertTrue(events.stream().anyMatch(event -> event.path("type").asText().equals("submissions")
                && event.path("id").asText().equals("101")));
        assertFalse(events.stream().anyMatch(event -> event.path("type").asText().equals("submissions")
                && event.path("id").asText().equals("102")));
        assertTrue(events.stream().anyMatch(event -> event.path("type").asText().equals("teams")
                && event.path("data").path("group_ids").get(0).asText().equals("stars")));
        assertThrows(IllegalArgumentException.class,
                () -> service.exportForResolver(7, Map.of(), List.of(3)));
    }

    private Map<String, Object> submission(int submissionId, int userId, LocalDateTime time) {
        return Map.of("submissionId", submissionId, "userId", userId, "problemId", 10,
                "submissionTime", time, "status", "AC", "language", "cpp", "timeCost", 10);
    }
}
