package org.example.user.task; // 请根据你的实际包名修改

import org.example.user.entity.User;
import org.example.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UserCleanupTask {

    @Autowired
    private UserService userService;

    /**
     * 每天凌晨 3 点执行一次
     * cron 表达式含义：秒 分 时 天 月 周
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        // 逻辑：删除用户名以 tmp_ 开头 且 验证码已过期的用户
        boolean removed = userService.lambdaUpdate()
                .likeRight(User::getUsername, "tmp_")
                .lt(User::getCodeExpireTime, LocalDateTime.now())
                .remove();

        if (removed) {
            System.out.println("【定时任务】清理过期临时用户完成 - 执行时间：" + LocalDateTime.now());
        }
    }
}