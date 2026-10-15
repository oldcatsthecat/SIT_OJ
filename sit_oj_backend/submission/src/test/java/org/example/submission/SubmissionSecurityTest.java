package org.example.submission;
import org.example.submission.controller.SubmissionController;
import org.example.submission.entity.Submission;
import org.example.submission.service.SubmissionService;
import org.example.submission.service.SubmissionRateLimiter;
import org.example.submission.feign.ProblemFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SubmissionSecurityTest {
 final SubmissionController controller=new SubmissionController();
 final SubmissionService service=mock(SubmissionService.class);
 final ProblemFeignClient problems=mock(ProblemFeignClient.class);
 final SubmissionRateLimiter limiter=mock(SubmissionRateLimiter.class);
 @BeforeEach void setup(){ReflectionTestUtils.setField(controller,"submissionService",service);ReflectionTestUtils.setField(controller,"problemFeignClient",problems);ReflectionTestUtils.setField(controller,"rateLimiter",limiter);}
 Submission input(){var s=new Submission();s.setUserId(1);s.setProblemId(2);s.setLanguage("py");s.setCodeContent("print(1)");s.setSubmissionId(999);s.setStatus("AC");s.setTimeCost(1);return s;}
 MockHttpServletRequest loggedIn(){var r=new MockHttpServletRequest();r.addHeader("Authorization","test-token");when(service.getUserIdFromToken("test-token")).thenReturn(77);when(service.getUserRoleFromToken("test-token")).thenReturn("USER");return r;}
 @Test void cannotSubmitAsVictimWithoutLoggingIn(){assertThrows(RuntimeException.class,()->controller.doSubmit(input(),new MockHttpServletRequest()));verify(service,never()).handleSubmission(any());}
 @Test void ignoresSpoofedIdentityAndServerOwnedFields(){
  var req=loggedIn();when(problems.getProblemById(2)).thenReturn(Map.of("problemId",2,"isPublic",true));
  controller.doSubmit(input(),req);var c=ArgumentCaptor.forClass(Submission.class);verify(service).handleSubmission(c.capture());
  assertEquals(77,c.getValue().getUserId());assertNull(c.getValue().getSubmissionId());assertNull(c.getValue().getStatus());assertNull(c.getValue().getTimeCost());assertEquals("python",c.getValue().getLanguage());verify(limiter).check(77);
 }
 @Test void cannotSubmitHiddenProblemThroughPublicRoute(){var req=loggedIn();when(problems.getProblemById(2)).thenReturn(Map.of("problemId",2,"isPublic",false));assertThrows(RuntimeException.class,()->controller.doSubmit(input(),req));verify(service,never()).handleSubmission(any());}
 @Test void cannotBypassContestRulesWithCompetitionId(){var req=loggedIn();var s=input();s.setCompetitionId(1);assertThrows(RuntimeException.class,()->controller.doSubmit(s,req));verify(service,never()).handleSubmission(any());}
 @Test void rejectsUnknownLanguageBeforeSaving(){var req=loggedIn();var s=input();s.setLanguage("bash");assertThrows(RuntimeException.class,()->controller.doSubmit(s,req));verify(service,never()).handleSubmission(any());}
 @Test void anonymousCannotReadSource(){assertThrows(RuntimeException.class,()->controller.getSubmissionById(1,new MockHttpServletRequest()));verify(service,never()).getById(any());}
 @Test void cannotReadAnotherUsersSource(){var req=loggedIn();when(service.getById(1)).thenReturn(input());assertEquals(500,controller.getSubmissionById(1,req).getCode());}
}
