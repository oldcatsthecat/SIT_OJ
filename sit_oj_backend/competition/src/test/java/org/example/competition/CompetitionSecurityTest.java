package org.example.competition;
import org.example.competition.entity.Competition;
import org.example.competition.mapper.CompetitionMapper;
import org.example.competition.mapper.CompetitionProblemMapper;
import org.example.competition.feign.ProblemFeignClient;
import org.example.competition.feign.SubmissionFeignClient;
import org.example.competition.service.ParticipationService;
import org.example.competition.service.impl.CompetitionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CompetitionSecurityTest {
 final CompetitionServiceImpl service=new CompetitionServiceImpl();
 final CompetitionMapper mapper=mock(CompetitionMapper.class);
 final ParticipationService parts=mock(ParticipationService.class);
 final ProblemFeignClient problems=mock(ProblemFeignClient.class);
 final SubmissionFeignClient submissions=mock(SubmissionFeignClient.class);
 final CompetitionProblemMapper cp=mock(CompetitionProblemMapper.class);
 Competition contest;
 @BeforeEach void setup(){
  ReflectionTestUtils.setField(service,"baseMapper",mapper);ReflectionTestUtils.setField(service,"participationService",parts);
  ReflectionTestUtils.setField(service,"problemFeignClient",problems);ReflectionTestUtils.setField(service,"submissionClient",submissions);ReflectionTestUtils.setField(service,"cpMapper",cp);
  contest=new Competition();contest.setCompetitionId(1);contest.setStartTime(LocalDateTime.now().minusHours(1));contest.setEndTime(LocalDateTime.now().plusHours(1));
  when(mapper.selectById(1)).thenReturn(contest);when(mapper.selectProblemIdsByCompetitionId(1)).thenReturn(List.of(22));
 }
 @Test void guestSeesScoreboardColumnsWithoutStatements(){var r=service.getCompetitionDetail(1,null);assertEquals(List.of(Map.of("problemId",22)),r.getProblems());verifyNoInteractions(problems);}
 @Test void unregisteredUserCannotFetchProblems(){service.getCompetitionDetail(1,3);verifyNoInteractions(problems);}
 @Test void registeredUserCannotFetchBeforeStart(){contest.setStartTime(LocalDateTime.now().plusHours(1));when(parts.checkRegistration(1,3)).thenReturn(true);service.getCompetitionDetail(1,3);verifyNoInteractions(problems);}
 @Test void authorizedParticipantGetsStatementWithoutSpj(){when(parts.checkRegistration(1,3)).thenReturn(true);when(problems.getProblemById(22)).thenReturn(Map.of("problemId",22,"problemDescription","statement","spjCode","secret"));var r=service.getCompetitionDetail(1,3);var p=(Map<?,?>)r.getProblems().get(0);assertEquals("statement",p.get("problemDescription"));assertFalse(p.containsKey("spjCode"));}
 @Test void rejectsUnregisteredSubmissions(){assertEquals(500,service.handleSubmission(3,1,22,"code","cpp").getCode());verifyNoInteractions(submissions);}
 @Test void rejectsProblemOutsideContest(){when(parts.checkRegistration(1,3)).thenReturn(true);assertEquals(500,service.handleSubmission(3,1,999,"code","cpp").getCode());verifyNoInteractions(submissions);}
}
