package org.example.competition;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.example.competition.entity.Competition;
import org.example.competition.entity.CompetitionSubmissionStats;
import org.example.competition.entity.Participation;
import org.example.competition.feign.UserFeignClient;
import org.example.competition.mapper.CompetitionMapper;
import org.example.competition.mapper.CompetitionSubmissionStatsMapper;
import org.example.competition.mapper.ParticipationMapper;
import org.example.competition.service.RankingService;
import org.example.competition.service.impl.CompetitionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.annotations.Select;

class CompetitionRankPaginationTest {

    @Test
    void registeredUsersWithoutSubmissionsAreIncludedAcrossPages() {
        verifyPagination(false, 17);
        verifyPagination(true, 17);
        verifyPagination(true, 25);
    }

    @Test
    void rankQueriesExcludePracticeParticipants() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "test"), Participation.class);
        Select pageQuery = ParticipationMapper.class
                .getMethod("getRanklistPage", Page.class, Integer.class).getAnnotation(Select.class);
        assertTrue(String.join(" ", pageQuery.value()).contains("p.is_practice = 0"));

        CompetitionServiceImpl service = new CompetitionServiceImpl();
        CompetitionMapper competitions = mock(CompetitionMapper.class);
        ParticipationMapper participations = mock(ParticipationMapper.class);
        RankingService ranking = mock(RankingService.class);
        ReflectionTestUtils.setField(service, "baseMapper", competitions);
        ReflectionTestUtils.setField(service, "participationMapper", participations);
        ReflectionTestUtils.setField(service, "rankingService", ranking);
        when(participations.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(participations.getRanklistPage(any(Page.class), eq(17))).thenAnswer(call -> {
            Page<Participation> page = call.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service.getRanklist(17, 1, 20);

        ArgumentCaptor<LambdaQueryWrapper<Participation>> countQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(participations).selectCount(countQuery.capture());
        assertTrue(countQuery.getValue().getSqlSegment().contains("is_practice"));
    }

    private void verifyPagination(boolean frozen, int cachedCount) {
        CompetitionServiceImpl service = new CompetitionServiceImpl();
        CompetitionMapper competitionMapper = mock(CompetitionMapper.class);
        ParticipationMapper participationMapper = mock(ParticipationMapper.class);
        CompetitionSubmissionStatsMapper statsMapper = mock(CompetitionSubmissionStatsMapper.class);
        RankingService rankingService = mock(RankingService.class);
        UserFeignClient userClient = mock(UserFeignClient.class);
        ReflectionTestUtils.setField(service, "baseMapper", competitionMapper);
        ReflectionTestUtils.setField(service, "participationMapper", participationMapper);
        ReflectionTestUtils.setField(service, "statsMapper", statsMapper);
        ReflectionTestUtils.setField(service, "rankingService", rankingService);
        ReflectionTestUtils.setField(service, "userFeignClient", userClient);

        Competition competition = new Competition();
        competition.setEndTime(LocalDateTime.now().plusMinutes(30));
        competition.setFreezeMinute(frozen ? 60 : 0);
        when(competitionMapper.selectById(17)).thenReturn(competition);
        when(participationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(25L);
        List<Participation> registered = new ArrayList<>();
        for (int id = 1; id <= 25; id++) {
            Participation participant = new Participation();
            participant.setUserId(id);
            registered.add(participant);
        }
        List<Map<String, Object>> cachedRanks = new ArrayList<>();
        for (int id = 1; id <= cachedCount; id++) cachedRanks.add(Map.of("userId", id));
        when(rankingService.getRankList(17, 0, 19))
                .thenReturn(cachedRanks.subList(0, Math.min(20, cachedCount)));
        when(rankingService.getRankList(17, 20, 39))
                .thenReturn(cachedRanks.subList(Math.min(20, cachedCount), cachedCount));
        when(rankingService.getParticipantCount(17)).thenReturn((long) cachedCount);
        when(participationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(registered);
        when(userClient.getUserById(anyInt())).thenReturn(Map.of());
        CompetitionSubmissionStats storedStats = new CompetitionSubmissionStats();
        storedStats.setUserId(1);
        storedStats.setProblemId(99);
        storedStats.setIsAc(true);
        when(statsMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(storedStats));
        when(rankingService.getFreezeAttempts(eq(17), anyInt())).thenReturn(Map.of());
        if (frozen) when(rankingService.getFreezeAttempts(17, 21)).thenReturn(Map.of(1, 2));
        when(participationMapper.getRanklistPage(any(Page.class), eq(17))).thenAnswer(invocation -> {
            Page<Participation> page = invocation.getArgument(0);
            int start = (int) ((page.getCurrent() - 1) * page.getSize());
            page.setRecords(registered.subList(start, Math.min(start + (int) page.getSize(), registered.size())));
            page.setTotal(registered.size());
            return page;
        });

        Map<String, Object> first = service.getRanklist(17, 1, 20);
        Map<String, Object> second = service.getRanklist(17, 2, 20);

        assertEquals(25L, first.get("total"));
        assertEquals(20, ((List<?>) first.get("records")).size());
        Participation firstParticipant = (Participation) ((List<?>) first.get("records")).get(0);
        assertEquals(storedStats, firstParticipant.getSubmissionStats().get(99));
        assertEquals(25L, second.get("total"));
        assertEquals(5, ((List<?>) second.get("records")).size());
        assertEquals(frozen, first.get("isFrozen"));
        assertEquals(frozen, second.get("isFrozen"));
        assertEquals(frozen ? Map.of(21, Map.of(1, 2)) : Map.of(), second.get("frozenAttempts"));
    }
}
