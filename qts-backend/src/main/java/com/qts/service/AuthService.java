package com.qts.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qts.common.BizException;
import com.qts.common.JwtUtil;
import com.qts.entity.User;
import com.qts.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public Map<String, Object> login(String username, String password) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被禁用");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        user.setPassword(null);
        resp.put("user", user);
        return resp;
    }

    /** 首次启动自动初始化演示账号 */
    @Override
    public void run(String... args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        seed("admin", "Qts@2026", "系统管理员", "ADMIN");
        seed("operator", "Qts@2026", "张生产", "OPERATOR");
        seed("qc", "Qts@2026", "李质检", "QC");
        seed("dealer", "Qts@2026", "王经销", "DEALER");
        System.out.println(">>> 已初始化演示账号: admin/operator/qc/dealer，密码均为 Qts@2026");
    }

    private void seed(String username, String pwd, String realName, String role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(pwd));
        u.setRealName(realName);
        u.setRole(role);
        u.setStatus(1);
        userMapper.insert(u);
    }
}
