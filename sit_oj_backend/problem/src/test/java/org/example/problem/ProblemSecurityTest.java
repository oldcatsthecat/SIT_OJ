package org.example.problem;
import org.example.problem.entity.Problem;
import org.example.problem.mapper.ProblemMapper;
import org.example.problem.service.impl.ProblemServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProblemSecurityTest {
 private final ProblemMapper mapper=mock(ProblemMapper.class);
 private final ProblemServiceImpl service=new ProblemServiceImpl();
 private Problem problem(boolean visible){var p=new Problem();p.setProblemId(22);p.setIsPublic(visible);p.setSpjCode("private judge source");return p;}
 private void setup(Problem p){ReflectionTestUtils.setField(service,"baseMapper",mapper);when(mapper.selectById(22)).thenReturn(p);}
 @Test void anonymousCannotReadHiddenProblem(){setup(problem(false));assertThrows(RuntimeException.class,()->service.getAccessibleProblem(22,null,null,null));}
 @Test void loggedInCannotReadHiddenProblemWithoutContest(){setup(problem(false));assertThrows(RuntimeException.class,()->service.getAccessibleProblem(22,null,2,"USER"));}
 @Test void fakeContestIdDoesNotGrantAccess(){setup(problem(false));assertThrows(RuntimeException.class,()->service.getAccessibleProblem(22,999,2,"USER"));}
 @Test void registeredStartedContestCanReadButCannotSeeSpj(){setup(problem(false));when(mapper.countAuthorizedContestProblem(eq(22),eq(1),eq(2),any(java.time.LocalDateTime.class))).thenReturn(1L);assertNull(service.getAccessibleProblem(22,1,2,"USER").getSpjCode());}
 @Test void contestCheckUsesShanghaiWallTime(){
  setup(problem(false));
  when(mapper.countAuthorizedContestProblem(eq(22),eq(1),eq(2),any(java.time.LocalDateTime.class))).thenReturn(1L);
  service.getAccessibleProblem(22,1,2,"USER");
  var time=org.mockito.ArgumentCaptor.forClass(java.time.LocalDateTime.class);
  verify(mapper).countAuthorizedContestProblem(eq(22),eq(1),eq(2),time.capture());
  var shanghaiNow=java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
  assertTrue(Math.abs(java.time.Duration.between(time.getValue(),shanghaiNow).toSeconds())<5);
 }
 @Test void publicProblemDoesNotLeakSpj(){setup(problem(true));assertNull(service.getAccessibleProblem(22,null,null,null).getSpjCode());}
 @Test void adminCanReadHiddenProblemForEditing(){setup(problem(false));assertNotNull(service.getAccessibleProblem(22,null,1,"ADMIN").getSpjCode());}
}
