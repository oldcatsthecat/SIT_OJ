package org.example.competition;

import org.example.competition.entity.Competition;
import org.example.competition.entity.Participation;
import org.example.competition.feign.ProblemFeignClient;
import org.example.competition.mapper.CompetitionMapper;
import org.example.competition.mapper.CompetitionProblemMapper;
import org.example.competition.mapper.ParticipationMapper;
import org.example.competition.service.impl.CompetitionServiceImpl;
import org.example.competition.service.impl.ParticipationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompetitionPracticeAccessTest {
    @Test
    void openingPracticeAfterContestEndsUnlocksContestProblems() {
        Competition contest = new Competition();
        contest.setCompetitionId(17);
        contest.setStartTime(LocalDateTime.now().minusHours(2));
        contest.setEndTime(LocalDateTime.now().minusHours(1));

        CompetitionMapper competitions = mock(CompetitionMapper.class);
        ParticipationMapper participations = mock(ParticipationMapper.class);
        ProblemFeignClient problems = mock(ProblemFeignClient.class);
        AtomicBoolean registered = new AtomicBoolean(false);
        when(competitions.selectById(17)).thenReturn(contest);
        when(competitions.selectProblemIdsByCompetitionId(17)).thenReturn(List.of(23));
        when(participations.selectCount(any())).thenAnswer(call -> registered.get() ? 1L : 0L);
        when(participations.insert(any(Participation.class))).thenAnswer(call -> {
            assertTrue(call.<Participation>getArgument(0).getIsPractice());
            registered.set(true);
            return 1;
        });
        when(problems.getProblemById(23)).thenReturn(Map.of("problemId", 23, "problemDescription", "statement"));

        ParticipationServiceImpl registration = new ParticipationServiceImpl();
        ReflectionTestUtils.setField(registration, "baseMapper", participations);
        ReflectionTestUtils.setField(registration, "competitionMapper", competitions);
        CompetitionServiceImpl details = new CompetitionServiceImpl();
        ReflectionTestUtils.setField(details, "baseMapper", competitions);
        ReflectionTestUtils.setField(details, "participationService", registration);
        ReflectionTestUtils.setField(details, "problemFeignClient", problems);
        ReflectionTestUtils.setField(details, "cpMapper", mock(CompetitionProblemMapper.class));

        assertFalse(details.canAccessCompetition(17, 3));
        assertEquals(List.of(Map.of("problemId", 23)), details.getCompetitionDetail(17, 3).getProblems());
        assertEquals(200, registration.registerUser(17, 3).getCode());
        assertTrue(details.canAccessCompetition(17, 3));
        assertEquals("statement", ((Map<?, ?>) details.getCompetitionDetail(17, 3).getProblems().get(0))
                .get("problemDescription"));
        verify(participations, times(1)).insert(any(Participation.class));
    }

    @Test
    void registrationBeforeContestEndsRemainsOfficial() {
        Competition contest = new Competition();
        contest.setEndTime(LocalDateTime.now().plusHours(1));
        CompetitionMapper competitions = mock(CompetitionMapper.class);
        ParticipationMapper participations = mock(ParticipationMapper.class);
        when(competitions.selectById(17)).thenReturn(contest);
        when(participations.selectCount(any())).thenReturn(0L);
        ParticipationServiceImpl registration = new ParticipationServiceImpl();
        ReflectionTestUtils.setField(registration, "baseMapper", participations);
        ReflectionTestUtils.setField(registration, "competitionMapper", competitions);

        assertEquals(200, registration.registerUser(17, 3).getCode());
        verify(participations).insert(argThat((Participation part) -> Boolean.FALSE.equals(part.getIsPractice())));
    }
}
