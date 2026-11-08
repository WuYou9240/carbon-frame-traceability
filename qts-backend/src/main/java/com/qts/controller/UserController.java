package com.qts.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qts.common.Auths;
import com.qts.common.BizException;
import com.qts.common.Result;
import com.qts.entity.User;
import com.qts.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    private static final List<String> ROLES = List.of("ADMIN", "OPERATOR", "QC", "DEALER", "CONSUMER");

    @GetMapping
    public Result<Page<User>> list(@RequestParam(defaultValue = "1") long current,
                                   @RequestParam(defaultValue = "10") long size,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String role) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(User::getUsername, keyword).or().like(User::getRealName, keyword));
        }
        if (role != null && !role.isBlank()) {
            qw.eq(User::getRole, role);
        }
        qw.orderByDesc(User::getId);
        Page<User> page = userMapper.selectPage(new Page<>(current, size), qw);
        page.getRecords().forEach(u -> u.setPassword(null));
        return Result.ok(page);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> create(@RequestBody Map<String, String> body) {
        User u = new User();
        u.setUsername(body.get("username"));
        u.setRealName(body.get("realName"));
        u.setRole(body.get("role"));
        u.setPhone(body.get("phone"));
        if (!ROLES.contains(u.getRole())) {
            throw new BizException("非法角色: " + u.getRole());
        }
        if (u.getUsername() == null || u.getUsername().isBlank()) {
            throw new BizException("用户名不能为空");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, u.getUsername())) > 0) {
            throw new BizException("用户名已存在");
        }
        String pwd = body.getOrDefault("password", "Qts@2026");
        u.setPassword(passwordEncoder.encode(pwd));
        u.setStatus(1);
        userMapper.insert(u);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException("用户不存在");
        }
        if (body.containsKey("realName")) u.setRealName(body.get("realName"));
        if (body.containsKey("phone")) u.setPhone(body.get("phone"));
        if (body.containsKey("role") && ROLES.contains(body.get("role"))) u.setRole(body.get("role"));
        if (body.containsKey("status")) u.setStatus(Integer.parseInt(body.get("status")));
        userMapper.updateById(u);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        if (id.equals(Auths.me().id())) {
            throw new BizException("不能删除自己");
        }
        userMapper.deleteById(id);
        return Result.ok();
    }
}
