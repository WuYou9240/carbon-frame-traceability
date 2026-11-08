package com.qts.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qts.common.Result;
import com.qts.entity.Frame;
import com.qts.mapper.FrameMapper;
import com.qts.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 消费者扫码溯源查询（公开接口，无需登录）
 */
@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;
    private final FrameMapper frameMapper;

    /** 管理端"消费者扫码页"入口：返回一台真实车架（优先已上链） */
    @GetMapping("/demo-frame")
    public Result<Map<String, Object>> demoFrame() {
        Frame f = frameMapper.selectOne(new LambdaQueryWrapper<Frame>()
                .eq(Frame::getHashRecorded, 1).orderByDesc(Frame::getId).last("LIMIT 1"));
        if (f == null) {
            f = frameMapper.selectOne(new LambdaQueryWrapper<Frame>()
                    .orderByAsc(Frame::getId).last("LIMIT 1"));
        }
        return Result.ok(Map.of("serialNo", f != null ? f.getSerialNo() : ""));
    }

    @GetMapping("/{serialNo}")
    public Result<Map<String, Object>> trace(@PathVariable String serialNo) throws Exception {
        return Result.ok(traceService.traceBySerial(serialNo, true));
    }
}
