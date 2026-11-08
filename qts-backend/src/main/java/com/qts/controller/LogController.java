package com.qts.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qts.common.Result;
import com.qts.entity.OperationLog;
import com.qts.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LogController {

    private final OperationLogMapper logMapper;

    @GetMapping
    public Result<Page<OperationLog>> list(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) String username) {
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) qw.like(OperationLog::getUsername, username);
        qw.orderByDesc(OperationLog::getId);
        return Result.ok(logMapper.selectPage(new Page<>(current, size), qw));
    }
}
