package org.example.judge.controller;


import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/judge")
public class AdminJudgeController {

    public static Map<String, Object> lastStatus = new HashMap<>();

    //后续如果需要负载均衡，多个判题机，可以考虑修改这里，
    //防止判题机以为我们后端挂了然后一直重启，给他一个心跳地址
    @PostMapping("/judge_server_heartbeat")
    public Map<String, Object> handleHeartbeat(@RequestBody Map<String, Object> payload) {
        // 每次心跳，把最新的 CPU、内存、最后在线时间存起来
        payload.put("last_seen", System.currentTimeMillis());
        lastStatus = payload;

        Map<String, Object> response = new HashMap<>();
        response.put("error", null);
        response.put("data", "success");
        return response;
    }

    @GetMapping("/server_status")
    public Map<String, Object> getServerStatus() {
        return lastStatus;
    }

}
