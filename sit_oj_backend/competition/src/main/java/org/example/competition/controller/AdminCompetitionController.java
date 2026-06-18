package org.example.competition.controller;

import org.example.common.utils.Result;
import org.example.competition.entity.Competition;
import org.example.competition.service.CompetitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/competitions")
public class AdminCompetitionController {

    @Autowired
    private CompetitionService competitionService;

    /**
     * 创建比赛
     * URL: POST /admin/competitions/create
     */
    @PostMapping("/create")
    public Result createCompetition(@RequestBody Competition competition) {
        competition.setCreateTime(LocalDateTime.now());
        boolean saved = competitionService.save(competition);
        return saved ? Result.success(competition) : Result.error("创建失败");
    }

    /**
     * 为比赛批量添加题目
     * URL: POST /admin/competitions/problems/add
     */
    @PostMapping("/problems/add")
    public Result addProblemsToCompetition(@RequestBody Map<String, Object> data) {
        Integer competitionId = (Integer) data.get("competitionId");
        List<Integer> problemIds = (List<Integer>) data.get("problemIds");

        if (competitionId == null || problemIds == null) {
            return Result.error("参数错误");
        }

        boolean success = competitionService.addProblems(competitionId, problemIds);
        return success ? Result.success("关联题目成功") : Result.error("部分题目关联失败");
    }

    @PutMapping("/update")
    public Result updateCompetition(@RequestBody Competition competition) {
        if (competition.getCompetitionId() == null) {
            return Result.error("比赛ID不能为空");
        }
        // 创建后仅允许修改比赛名称，开始/结束/封榜时间不可修改
        boolean success = competitionService.lambdaUpdate()
                .eq(Competition::getCompetitionId, competition.getCompetitionId())
                .set(Competition::getCompetitionName, competition.getCompetitionName())
                .update();
        return success ? Result.success("更新成功") : Result.error("更新失败");
    }


    @DeleteMapping("/delete/{id}")
    public Result deleteCompetition(@PathVariable Integer id) {
        boolean success = competitionService.removeById(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }

    /**
     * 管理员手动解封比赛
     */
    @PostMapping("/{id}/unfreeze")
    public Result unfreezeCompetition(@PathVariable Integer id) {
        return competitionService.unfreeze(id);
    }

    /**
     * 导出比赛数据为 ICPC Resolver NDJSON 格式
     */
    @GetMapping("/{id}/export")
    public Result exportForResolver(@PathVariable Integer id) {
        String ndjson = competitionService.exportForResolver(id);
        if (ndjson == null) return Result.error("比赛不存在");
        return Result.success(ndjson);
    }
}