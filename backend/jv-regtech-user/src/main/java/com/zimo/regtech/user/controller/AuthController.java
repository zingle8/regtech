package com.zimo.regtech.user.controller;

import com.zimo.regtech.common.context.UserContext;
import com.zimo.regtech.common.result.R;
import com.zimo.regtech.user.entity.User;
import com.zimo.regtech.user.model.LoginRequest;
import com.zimo.regtech.user.model.LoginVO;
import com.zimo.regtech.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(userService.login(request));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        userService.logout(UserContext.getUserId());
        return R.ok();
    }

    @GetMapping("/me")
    public R<User> me() {
        return R.ok(userService.getById(UserContext.getUserId()));
    }
}
