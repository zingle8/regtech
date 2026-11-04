package com.zimo.regtech.user.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginVO {

    private String token;
    private Long id;
    private String username;
    private String nickname;
    private String role;
    private LocalDateTime loginTime;
    private LocalDateTime expireTime;
}
