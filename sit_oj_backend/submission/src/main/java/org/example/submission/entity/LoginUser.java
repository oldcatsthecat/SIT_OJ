package org.example.submission.entity;

import lombok.Data;


@Data
public class LoginUser {
    private Integer id;
    private String username;
    private String role;
}