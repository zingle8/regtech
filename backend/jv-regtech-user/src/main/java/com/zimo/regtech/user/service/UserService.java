package com.zimo.regtech.user.service;

import com.zimo.regtech.common.exception.BizException;
import com.zimo.regtech.common.result.Page;
import com.zimo.regtech.common.result.ResultCode;
import com.zimo.regtech.common.util.JwtUtil;
import com.zimo.regtech.user.entity.User;
import com.zimo.regtech.user.mapper.UserMapper;
import com.zimo.regtech.user.model.LoginRequest;
import com.zimo.regtech.user.model.LoginVO;
import com.zimo.regtech.user.model.UserQuery;
import com.zimo.regtech.user.model.UserSaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    public LoginVO login(LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null || !ENCODER.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "账号或密码错误");
        }
        if (Objects.equals(user.getStatus(), 0)) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已禁用，请联系管理员");
        }

        LocalDateTime now = LocalDateTime.now();
        userMapper.updateLoginSuccess(user.getId(), now);

        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getTokenVersion());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setLoginTime(now);
        vo.setExpireTime(now.plusHours(8));
        return vo;
    }

    public void logout(Long userId) {
        if (userId != null) {
            userMapper.bumpTokenVersion(userId);
        }
    }

    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "账号不存在");
        }
        return user;
    }

    public Page<User> page(UserQuery query) {
        long total = userMapper.countByCondition(query.getKeyword(), query.getRole(), query.getStatus());
        List<User> records = total == 0
                ? List.of()
                : userMapper.selectPage(query.getKeyword(), query.getRole(), query.getStatus(),
                query.getOffset(), query.getLimit());
        return Page.of(records, total, query.getCurrent(), query.getLimit());
    }

    @Transactional
    public User create(UserSaveRequest request) {
        if (userMapper.countByUsername(request.getUsername(), null) > 0) {
            throw new BizException("账号已存在：" + request.getUsername());
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(ENCODER.encode(StringUtils.hasText(request.getPassword())
                ? request.getPassword() : "Regtech@123"));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(StringUtils.hasText(request.getRole()) ? request.getRole() : "ANALYST");
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        userMapper.insert(user);
        return user;
    }

    @Transactional
    public void update(Long id, UserSaveRequest request) {
        User exists = getById(id);
        if (userMapper.countByUsername(request.getUsername(), id) > 0) {
            throw new BizException("账号已存在：" + request.getUsername());
        }
        User user = new User();
        user.setId(exists.getId());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        userMapper.update(user);

        if (StringUtils.hasText(request.getPassword())) {
            userMapper.updatePassword(id, ENCODER.encode(request.getPassword()));
            // 密码变更后使旧令牌失效
            userMapper.bumpTokenVersion(id);
        }
    }

    public void changeStatus(Long id, Integer status) {
        getById(id);
        userMapper.updateStatus(id, status == null ? 1 : status);
    }

    public void resetPassword(Long id, String password) {
        getById(id);
        String raw = StringUtils.hasText(password) ? password : "Regtech@123";
        userMapper.updatePassword(id, ENCODER.encode(raw));
        userMapper.bumpTokenVersion(id);
    }

    public void delete(Long id) {
        getById(id);
        userMapper.deleteById(id);
    }
}
