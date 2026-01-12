package org.example.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.example.user.entity.User;
import org.example.user.mapper.UserMapper;
import org.example.user.service.UserService;
import org.example.user.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Autowired
    private ObjectProvider<UserServiceImpl> selfProvider;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserInfo(User user, String code) {
        // 1. 获取数据库中当前用户的原始数据
        User currentUser = this.getById(user.getId());
        if (currentUser == null) throw new RuntimeException("用户不存在");

        // 2. 情况 A：请求中包含新邮箱，且与当前邮箱不同
        if (user.getEmail() != null && !user.getEmail().equals(currentUser.getEmail())) {
            validateAndHandleEmailChange(user.getEmail(), code, currentUser);
        }

        // 3. 情况 B：请求中包含新密码
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            validateAndHandlePasswordChange(user.getPassword(), code, currentUser);
        }

        // 4. 情况 C：处理普通信息更新（无论有无验证码，这些字段都可以直接改）
        if (user.getRealName() != null) currentUser.setRealName(user.getRealName());
        if (user.getStudentId() != null) currentUser.setStudentId(user.getStudentId());
        if (user.getGender() != null) currentUser.setGender(user.getGender());
        if (user.getAge() != null) currentUser.setAge(user.getAge());

        // 5. 统一执行更新
        this.updateById(currentUser);
    }

    // 私有方法：专门处理邮箱变更的冲突与校验
    private void validateAndHandleEmailChange(String newEmail, String code, User currentUser) {
        if (code == null || code.isEmpty()) throw new RuntimeException("修改邮箱必须提供验证码");

        // 查找占用该邮箱的临时记录（id=4的那种）
        User tempUser = this.getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, newEmail));
        if (tempUser == null) throw new RuntimeException("请先获取验证码");

        if (!code.equals(tempUser.getEmailCode())) throw new RuntimeException("邮箱验证码错误");

        // 验证通过，删除临时占位记录，释放唯一索引
        this.removeById(tempUser.getId());
        currentUser.setEmail(newEmail);
    }

    // 私有方法：专门处理密码变更校验
    private void validateAndHandlePasswordChange(String newPwd, String code, User currentUser) {
        if (code == null || code.isEmpty()) throw new RuntimeException("修改密码必须提供验证码");

        // 修改密码校验的是发给“当前用户已绑定邮箱”的验证码
        if (!code.equals(currentUser.getEmailCode())) throw new RuntimeException("密码重置验证码错误");

        currentUser.setPassword(passwordEncoder.encode(newPwd));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(Map<String, Object> params) {
        // 1. 提取参数
        String username = (String) params.get("username");
        String password = (String) params.get("password");
        String email = (String) params.get("email");
        String code = (String) params.get("code");

        // 2. 查找预存记录
        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new RuntimeException("请先获取验证码");
        }

        // 3. 校验验证码与有效期
        if (user.getEmailCode() == null || !user.getEmailCode().equals(code)) {
            throw new RuntimeException("验证码错误");
        }
        if (user.getCodeExpireTime() != null && LocalDateTime.now().isAfter(user.getCodeExpireTime())) {
            throw new RuntimeException("验证码已过期");
        }

        // 4. 检查用户名是否已被他人占用
        User nameConflict = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .ne(User::getEmail, email));
        if (nameConflict != null) {
            throw new RuntimeException("用户名已被占用");
        }

        // 5. & 6. 使用 UpdateWrapper 更新信息并强制清空验证码
        this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getUsername, username)
                .set(User::getPassword, passwordEncoder.encode(password))
                .set(User::getRole, "USER")
                .set(User::getEmailCode, null)         // 强制更新为 null
                .set(User::getCodeExpireTime, null)    // 强制更新为 null
        );
    }
    @Override
    public String login(String account, String password) {
        // 同时匹配 username 或 email
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or()
                .eq(User::getEmail, account));

        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtils.generateToken(user);
        return token;
    }

    @Override
    public User tokenToUser(String token)
    {
        // 1. 获取用户名
        String username = jwtUtils.getUsernameFromToken(token);

        // 2. 查询最新用户信息
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Map<String, Object> params) {
        String email = (String) params.get("email");
        String code = (String) params.get("code");
        String newPassword = (String) params.get("password");

        // 1. 根据邮箱查用户
        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null || user.getPassword() == null) {
            throw new RuntimeException("该邮箱未注册正式账号");
        }

        // 2. 校验验证码
        if (code == null || !code.equals(user.getEmailCode())) {
            throw new RuntimeException("验证码错误");
        }
        if (user.getCodeExpireTime() != null && LocalDateTime.now().isAfter(user.getCodeExpireTime())) {
            throw new RuntimeException("验证码已过期");
        }

        // 3. 使用 UpdateWrapper 更新密码并强制清空验证码
        this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getPassword, passwordEncoder.encode(newPassword))
                .set(User::getEmailCode, null)         // 强制更新为 null
                .set(User::getCodeExpireTime, null)    // 强制更新为 null
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendCode(String email, String type) {
        User existingUser = this.getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));

        if ("register".equals(type)) {
            if (existingUser != null && existingUser.getPassword() != null) {
                throw new RuntimeException("该邮箱已被注册，请直接登录");
            }
        }

        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));

        if (existingUser == null) {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setUsername("tmp_" + System.currentTimeMillis());
            newUser.setEmailCode(code);
            newUser.setCodeExpireTime(LocalDateTime.now().plusMinutes(5));
            newUser.setRole("USER");
            this.save(newUser);
        } else {
            existingUser.setEmailCode(code);
            existingUser.setCodeExpireTime(LocalDateTime.now().plusMinutes(5));
            this.updateById(existingUser);
        }

        // 调用本类中的异步方法发送邮件
        String content = "您的验证码为：" + code + "，有效期 5 分钟。请勿泄露给他人。";
        selfProvider.getIfAvailable().doSendMailAsync(email, "SIT-OJ验证码", content);
    }

    /**
     * 邮件发送异步执行体
     */
    @Async
    public void doSendMailAsync(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            System.out.println("DEBUG: [线程 " + Thread.currentThread().getName() + "] 邮件异步发送成功至: " + to);
        } catch (Exception e) {
            System.err.println("邮件发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void clearTemporaryUsers() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(User::getUsername, "tmp_")
                .lt(User::getCodeExpireTime, LocalDateTime.now()); // 只删过期时间早于现在的

        userMapper.delete(wrapper);
    }

}