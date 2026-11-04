package com.zimo.regtech.user.controller;

import com.zimo.regtech.common.result.PageResult;
import com.zimo.regtech.common.result.R;
import com.zimo.regtech.user.entity.User;
import com.zimo.regtech.user.model.UserQuery;
import com.zimo.regtech.user.model.UserSaveRequest;
import com.zimo.regtech.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public R<PageResult<User>> page(UserQuery query) {
        return R.ok(PageResult.of(userService.page(query)));
    }

    @GetMapping("/{id}")
    public R<User> detail(@PathVariable Long id) {
        return R.ok(userService.getById(id));
    }

    @PostMapping
    public R<User> create(@Valid @RequestBody UserSaveRequest request) {
        return R.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        userService.update(id, request);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.changeStatus(id, body.get("status"));
        return R.ok();
    }

    @PutMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        userService.resetPassword(id, body == null ? null : body.get("password"));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }
}
