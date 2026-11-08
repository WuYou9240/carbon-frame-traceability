package com.qts.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qts.common.Auths;
import com.qts.common.Result;
import com.qts.entity.User;
import com.qts.mapper.UserMapper;
import com.qts.service.AuthService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Validated LoginReq req) {
        return Result.ok(authService.login(req.getUsername(), req.getPassword()));
    }

    @GetMapping("/me")
    public Result<User> me() {
        User user = userMapper.selectById(Auths.me().id());
        if (user != null) {
            user.setPassword(null);
        }
        return Result.ok(user);
    }

    /** 修改密码 */
    @PostMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        User user = userMapper.selectById(Auths.me().id());
        if (user == null || !passwordEncoder.matches(oldPwd, user.getPassword())) {
            return Result.error(400, "原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPwd));
        userMapper.updateById(user);
        return Result.ok();
    }
}
