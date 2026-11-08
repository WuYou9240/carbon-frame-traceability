package com.qts.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qts.common.Auths;
import com.qts.common.BizException;
import com.qts.common.Result;
import com.qts.entity.Frame;
import com.qts.entity.LogisticsRecord;
import com.qts.mapper.FrameMapper;
import com.qts.mapper.LogisticsRecordMapper;
import com.qts.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DEALER')")
public class LogisticsController {

    private final LogisticsRecordMapper logisticsMapper;
    private final FrameMapper frameMapper;
    private final TraceService traceService;

    private static final Set<String> EVENTS = Set.of("STOCK_IN", "STOCK_OUT", "SALE");

    @GetMapping
    public Result<List<LogisticsRecord>> list(@RequestParam(required = false) Long frameId) {
        LambdaQueryWrapper<LogisticsRecord> qw = new LambdaQueryWrapper<>();
        if (frameId != null) qw.eq(LogisticsRecord::getFrameId, frameId);
        qw.orderByDesc(LogisticsRecord::getId);
        return Result.ok(logisticsMapper.selectList(qw));
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<LogisticsRecord> create(@RequestBody LogisticsRecord record) {
        Frame frame = frameMapper.selectById(record.getFrameId());
        if (frame == null) {
            throw new BizException("车架不存在");
        }
        if (!EVENTS.contains(record.getEventType())) {
            throw new BizException("非法物流事件: " + record.getEventType());
        }
        record.setDealerId(Auths.me().id());
        record.setEventTime(LocalDateTime.now());
        logisticsMapper.insert(record);
        if ("SALE".equals(record.getEventType())) {
            frame.setStatus("SHIPPED");
            frameMapper.updateById(frame);
        }
        traceService.evictCacheSafely(frame.getSerialNo());
        return Result.ok(record);
    }
}
