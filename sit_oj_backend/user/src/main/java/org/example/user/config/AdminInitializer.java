package org.example.user.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.user.entity.User;
import org.example.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 1. 判断数据库中是否已经有名为 root 的账号
        long count = userService.count(new LambdaQueryWrapper<User>().eq(User::getUsername, "root"));

        if (count == 0) {
            // 2. 如果没有，则创建一个默认管理员
            User admin = new User();
            admin.setUsername("root");
            // 这里设置初始密码，例如 rootroot
            admin.setPassword(passwordEncoder.encode("rootroot"));
            admin.setRole("ADMIN");
            admin.setRealName("系统管理员");

            // 3. 存入数据库
            userService.save(admin);

            System.out.println("=======================================");
            System.out.println("检测到管理员账号不存在，已初始化：");
            System.out.println("账号：root");
            System.out.println("密码：rootroot");
            System.out.println("角色：ADMIN");
            System.out.println("=======================================");
        } else {
            System.out.println("管理员账号已存在，跳过初始化。");
        }
    }
}