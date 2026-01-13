package org.example.competition.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.common.utils.Result;
import org.example.competition.entity.Competition;
import org.example.competition.entity.Participation;
import org.example.competition.service.CompetitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/competitions")
public class CompetitionController {

    @Autowired
    private CompetitionService competitionService;

    /**
     * 获取所有比赛列表
     * 不需要登录即可查看
     */
    @GetMapping("/list")
    public Result getCompetitionList(HttpServletRequest request) {

        Integer userId = (Integer) request.getAttribute("userId");

        // 2. 调用你写好的 Service 方法
        List<Competition> list = competitionService.getListWithRegisterStatus(userId);

        return Result.success(list);
    }

    /**
     * 获取比赛详情
     * @param id 比赛ID
     * @param userId 通过请求拦截器获取的当前登录用户ID (可选)
     */
    @GetMapping("/{id}")
    public Result getCompetitionDetail(
            @PathVariable Integer id,
            @RequestAttribute(value = "userId" , required = false) Integer userId) {

        Competition detail = competitionService.getCompetitionDetail(id, userId);
        if (detail == null) {
            return Result.error("该比赛不存在");
        }
        return Result.success(detail);
    }


    /**
     * 获取比赛排名 (Standings)
     * ACM 模式：解题数与罚时排名
     */
    @GetMapping("/{id}/rank")
    public Result getRanklist(@PathVariable Integer id) {
        List<Participation> ranklist = competitionService.getRanklist(id);
        return Result.success(ranklist);
    }

    @PostMapping("/{id}/submit")
    public Result submitProblem(
            @PathVariable("id") Integer competitionId,
            @RequestBody Map<String, Object> submitData,
            HttpServletRequest request) { // 1. 改为直接接收 request 避开注入时的类型转换

        Object userIdObj = request.getAttribute("userId");
        if (userIdObj == null) return Result.error("未登录");
        Integer userId = Integer.valueOf(String.valueOf(userIdObj));

        try {
            // 使用 String.valueOf 防止前端传的是数字或字符串，再解析为 Integer
            Integer problemId = Integer.valueOf(String.valueOf(submitData.get("problemId")));
            String code = String.valueOf(submitData.get("codeContent"));
            String language = String.valueOf(submitData.get("language"));

            // 4. 调用 Service
            return competitionService.handleSubmission(userId, competitionId, problemId, code, language);

        } catch (Exception e) {
            System.out.println("error！！！！！！！！！！" + e.getMessage());
            return Result.error("提交数据格式错误，请检查题目ID等参数");
        }
    }

    @GetMapping("/{id}/stats")
    public Result getStats(@PathVariable Integer id) {
        return competitionService.getProblemStats(id);
    }

    // CompetitionController.java
    @PostMapping("/internal/updateStats")
    public Result updateRankStats(@RequestParam Integer userId,
                                                                 @RequestParam Integer competitionId,
                                                                 @RequestParam Integer problemId,
                                                                 @RequestParam String status) {
        // 执行你原来的 updateAcmStats 逻辑
        competitionService.updateAcmStats(userId, competitionId, problemId, status);
        return Result.success();
    }

}