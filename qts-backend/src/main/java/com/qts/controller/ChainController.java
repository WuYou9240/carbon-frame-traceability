package com.qts.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qts.common.Result;
import com.qts.entity.ChainTxRecord;
import com.qts.mapper.ChainTxRecordMapper;
import com.qts.service.ChainService;
import com.qts.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chain")
@RequiredArgsConstructor
public class ChainController {

    private final ChainTxRecordMapper chainTxMapper;
    private final ChainService chainService;
    private final TraceService traceService;

    @GetMapping("/records")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    public Result<Page<ChainTxRecord>> records(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String serialNo) {
        LambdaQueryWrapper<ChainTxRecord> qw = new LambdaQueryWrapper<>();
        if (serialNo != null && !serialNo.isBlank()) qw.like(ChainTxRecord::getSerialNo, serialNo);
        qw.orderByDesc(ChainTxRecord::getId);
        return Result.ok(chainTxMapper.selectPage(new Page<>(current, size), qw));
    }

    /** 链与合约状态 */
    @GetMapping("/info")
    public Result<Map<String, Object>> info() throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("group", "group0");
        info.put("blockNumber", chainService.blockNumber());
        info.put("contractAddress", chainService.contractAddress());
        info.put("account", chainService.accountAddress());
        return Result.ok(info);
    }
}
