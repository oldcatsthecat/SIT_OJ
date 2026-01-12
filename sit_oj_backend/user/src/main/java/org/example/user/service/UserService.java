package org.example.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.user.entity.User;

import java.util.Map;

public interface UserService extends IService<User> {
    void register(Map<String, Object> params);
    String login(String username, String password);

    User tokenToUser(String token);

    void sendCode(String email, String type);

    void updateUserInfo(User user, String code);

    void resetPassword(Map<String, Object> params);
}