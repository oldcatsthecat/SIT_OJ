package org.example.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import org.example.common.entity.BaseEntity; // 引用公共基类
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("users")
public class User extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;
    private String password;
    private String role;
    private String gender;
    private String realName;
    private String studentId;
    private String email;
    private Integer age;

    @TableField("email_code")
    private String emailCode;

    @TableField("code_expire_time")
    private LocalDateTime codeExpireTime;

}