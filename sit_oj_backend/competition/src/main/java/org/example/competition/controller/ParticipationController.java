package org.example.competition.controller;

import org.example.common.utils.Result;
import org.example.competition.service.ParticipationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/competitions")
public class ParticipationController {

    @Autowired
    private ParticipationService participationService;

    /**
     * 比赛报名
     * @param cid 比赛ID
     * @param userId 这里建议从请求头/Token中获取，暂时作为参数或从Attribute获取
     */
    @PostMapping("/{cid}/register")
    public Result<String> register(@PathVariable("cid") Integer cid,
                                   @RequestAttribute("userId") Integer userId) {
        return participationService.registerUser(cid, userId);
    }

    /**
     * 获取报名状态
     */
    @GetMapping("/{cid}/status")
    public Result<Boolean> getStatus(@PathVariable("cid") Integer cid,
                                     @RequestAttribute("userId") Integer userId) {
        boolean isRegistered = participationService.checkRegistration(cid, userId);
        return Result.success(isRegistered);
    }
}