package org.example.submission;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.common.utils.Result;
import org.example.submission.entity.Submission;
import org.example.submission.feign.CompetitionFeignClient;
import org.example.submission.feign.JudgeFeignClient;
import org.example.submission.feign.ProblemFeignClient;
import org.example.submission.feign.UserFeignClient;
import org.example.submission.mapper.SubmissionMapper;
import org.example.submission.service.impl.SubmissionServiceImpl;
import org.example.submission.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetitionSubmissionFilterTest {
    @Mock ProblemFeignClient problems;
    @Mock JudgeFeignClient judge;
    @Mock UserFeignClient users;
    @Mock JwtUtils jwt;
    @Mock SubmissionMapper mapper;
    @Mock CompetitionFeignClient competitions;
    @Mock JdbcTemplate jdbc;
    @InjectMocks SubmissionServiceImpl service;

    @Test
    void filtersBeforePaginationAndUsesParametersForUsername() {
        when(competitions.checkFrozen(7)).thenReturn(Result.success(false));
        when(mapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(new Page<Submission>(2, 15));

        service.getCompetitionSubmissions(2, 15, 7, 3, "ADMIN", "alice", 42, "AC");

        ArgumentCaptor<QueryWrapper<Submission>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("competition_id"));
        assertTrue(sql.contains("problem_id"));
        assertTrue(sql.contains("u.username LIKE"));
        assertTrue(sql.contains("status"));
        assertFalse(sql.contains("alice"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("alice"));
    }

    @Test
    void frozenStatusFilterMatchesOnlyVisibleRows() {
        when(competitions.checkAccess(7, 3)).thenReturn(Result.success(true));
        when(competitions.checkFrozen(7)).thenReturn(Result.success(true));
        when(mapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(new Page<Submission>(1, 15));

        service.getCompetitionSubmissions(1, 15, 7, 3, "USER", null, null, "AC");
        ArgumentCaptor<QueryWrapper<Submission>> realStatus = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), realStatus.capture());
        assertTrue(realStatus.getValue().getSqlSegment().contains("user_id"));
        assertTrue(realStatus.getValue().getParamNameValuePairs().containsValue(3));

        clearInvocations(mapper);
        service.getCompetitionSubmissions(1, 15, 7, 3, "USER", null, null, "Frozen");
        ArgumentCaptor<QueryWrapper<Submission>> frozen = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), frozen.capture());
        assertTrue(frozen.getValue().getSqlSegment().contains("<>"));
        assertFalse(frozen.getValue().getSqlSegment().contains("status ="));
    }

    @Test
    void stillMasksOtherUsersWhenProfileLookupFails() {
        when(competitions.checkAccess(7, 3)).thenReturn(Result.success(true));
        when(competitions.checkFrozen(7)).thenReturn(Result.success(true));
        Submission other = new Submission();
        other.setUserId(9);
        other.setStatus("AC");
        Page<Submission> page = new Page<>(1, 15);
        page.setRecords(List.of(other));
        when(mapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);
        when(users.getUserById(9)).thenThrow(new IllegalStateException("unavailable"));

        Submission shown = service.getCompetitionSubmissions(1, 15, 7, 3, "USER").getRecords().get(0);
        assertEquals("Frozen", shown.getStatus());
        assertFalse(shown.getCanSeeDetail());
    }
}
