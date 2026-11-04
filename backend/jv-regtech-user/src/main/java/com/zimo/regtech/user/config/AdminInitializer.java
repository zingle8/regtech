package com.zimo.regtech.user.config;

import com.zimo.regtech.user.mapper.UserMapper;
import com.zimo.regtech.user.model.UserSaveRequest;
import com.zimo.regtech.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 用户表为空时自动创建内置管理员（admin / Admin@123）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.countAll() > 0) {
            return;
        }
        UserSaveRequest request = new UserSaveRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");
        request.setNickname("平台管理员");
        request.setRole("ADMIN");
        request.setStatus(1);
        userService.create(request);
        log.info("已创建内置管理员：admin / Admin@123");
    }
}
