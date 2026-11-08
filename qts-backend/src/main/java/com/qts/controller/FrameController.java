package com.qts.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qts.common.BizException;
import com.qts.common.HashUtil;
import com.qts.common.Result;
import com.qts.entity.Frame;
import com.qts.entity.InspectionRecord;
import com.qts.entity.LogisticsRecord;
import com.qts.entity.ProcessRecord;
import com.qts.entity.QrCodeRecord;
import com.qts.mapper.ChainTxRecordMapper;
import com.qts.mapper.FrameMapper;
import com.qts.mapper.InspectionRecordMapper;
import com.qts.mapper.LogisticsRecordMapper;
import com.qts.mapper.ProcessRecordMapper;
import com.qts.mapper.QrCodeRecordMapper;
import com.qts.service.QrService;
import com.qts.service.ReportService;
import com.qts.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/frames")
@RequiredArgsConstructor
public class FrameController {

    private final FrameMapper frameMapper;
    private final TraceService traceService;
    private final QrService qrService;
    private final ReportService reportService;
    private final ProcessRecordMapper processMapper;
    private final InspectionRecordMapper inspectionMapper;
    private final QrCodeRecordMapper qrMapper;
    private final LogisticsRecordMapper logisticsMapper;
    private final ChainTxRecordMapper chainTxMapper;

    private static final String STAFF = "hasAnyRole('ADMIN','OPERATOR','QC','DEALER')";

    @GetMapping
    @PreAuthorize(STAFF)
    public Result<Page<Frame>> list(@RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size,
                                    @RequestParam(required = false) String serialNo,
                                    @RequestParam(required = false) String model,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) Integer hashRecorded) {
        if (current < 1) current = 1;
        if (size < 1 || size > 100) size = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<Frame> qw = new LambdaQueryWrapper<>();
        if (serialNo != null && !serialNo.isBlank()) qw.like(Frame::getSerialNo, serialNo);
        if (model != null && !model.isBlank()) qw.like(Frame::getModel, model);
        if (status != null && !status.isBlank()) qw.eq(Frame::getStatus, status);
        if (hashRecorded != null) qw.eq(Frame::getHashRecorded, hashRecorded);
        qw.orderByDesc(Frame::getId);
        return Result.ok(frameMapper.selectPage(new Page<>(current, size), qw));
    }

    @GetMapping("/{id}")
    @PreAuthorize(STAFF)
    public Result<Map<String, Object>> detail(@PathVariable Long id) throws Exception {
        Frame frame = frameMapper.selectById(id);
        if (frame == null) {
            throw new BizException("车架不存在");
        }
        return Result.ok(traceService.traceBySerial(frame.getSerialNo(), false));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Frame> create(@RequestBody Frame frame) {
        if (frame.getModel() == null || frame.getModel().isBlank()) {
            throw new BizException("型号不能为空");
        }
        if (frame.getSize() == null || frame.getSize().isBlank()) {
            throw new BizException("尺寸不能为空");
        }
        if (frame.getSerialNo() != null) frame.setSerialNo(frame.getSerialNo().trim());
        if (frame.getSerialNo() == null || frame.getSerialNo().isBlank()) {
            // 未指定序列号时自动生成
            frame.setSerialNo("FR" + System.currentTimeMillis());
        }
        if (frameMapper.selectCount(new LambdaQueryWrapper<Frame>().eq(Frame::getSerialNo, frame.getSerialNo())) > 0) {
            throw new BizException("序列号已存在: " + frame.getSerialNo());
        }
        frame.setStatus(frame.getStatus() == null ? "IN_PROD" : frame.getStatus());
        frame.setHashRecorded(0);
        frameMapper.insert(frame);
        return Result.ok(frame);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@PathVariable Long id, @RequestBody Frame frame) {
        Frame db = frameMapper.selectById(id);
        if (db == null) {
            throw new BizException("车架不存在");
        }
        frame.setId(id);
        frame.setSerialNo(db.getSerialNo()); // 序列号不可修改
        frameMapper.updateById(frame);
        // 已上链的车架数据发生变更 → 原存证不再代表当前数据，标记待重新上链
        if (db.getHashRecorded() != null && db.getHashRecorded() == 1) {
            Frame mark = new Frame();
            mark.setId(id);
            mark.setHashRecorded(0);
            frameMapper.updateById(mark);
        }
        traceService.evictCacheSafely(db.getSerialNo());
        return Result.ok();
    }

    /** 演示：模拟链下数据被篡改（只改数据，不重置存证标记 → 实时哈希与链上不一致，验真报警 TAMPERED） */
    @PutMapping("/{id}/tamper")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> tamper(@PathVariable Long id) {
        Frame f = frameMapper.selectById(id);
        if (f == null) {
            throw new BizException("车架不存在");
        }
        if (f.getHashRecorded() == null || f.getHashRecorded() != 1) {
            throw new BizException("该车架尚未上链存证，请先上链后再演示篡改");
        }
        if (!f.getModel().contains("-TAMPERED")) {
            Frame upd = new Frame();
            upd.setId(id);
            upd.setModel(f.getModel() + "-TAMPERED");
            frameMapper.updateById(upd);
        }
        traceService.evictCacheSafely(f.getSerialNo());
        return Result.ok();
    }

    /** 演示：恢复被篡改的链下数据（链上存证本来就是原始数据 → 恢复后验真重新 VALID） */
    @PutMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> restore(@PathVariable Long id) {
        Frame f = frameMapper.selectById(id);
        if (f == null) {
            throw new BizException("车架不存在");
        }
        if (f.getModel() != null && f.getModel().contains("-TAMPERED")) {
            Frame upd = new Frame();
            upd.setId(id);
            upd.setModel(f.getModel().replace("-TAMPERED", ""));
            frameMapper.updateById(upd);
        }
        traceService.evictCacheSafely(f.getSerialNo());
        return Result.ok();
    }

    /** 更新生产状态 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (!List.of("IN_PROD", "CURED", "INSPECTED", "SHIPPED").contains(status)) {
            throw new BizException("非法状态: " + status);
        }
        Frame frame = frameMapper.selectById(id);
        frame.setStatus(status);
        frameMapper.updateById(frame);
        traceService.evictCacheSafely(frame.getSerialNo());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        Frame frame = frameMapper.selectById(id);
        if (frame == null) {
            throw new BizException("车架不存在");
        }
        if (frame.getHashRecorded() == 1) {
            throw new BizException("该车架已上链存证，不允许删除（保证链上链下一致性）");
        }
        // 级联清理关联记录，避免孤儿数据
        Long fid = frame.getId();
        processMapper.delete(new LambdaQueryWrapper<ProcessRecord>().eq(ProcessRecord::getFrameId, fid));
        inspectionMapper.delete(new LambdaQueryWrapper<InspectionRecord>().eq(InspectionRecord::getFrameId, fid));
        qrMapper.delete(new LambdaQueryWrapper<QrCodeRecord>().eq(QrCodeRecord::getFrameId, fid));
        logisticsMapper.delete(new LambdaQueryWrapper<LogisticsRecord>().eq(LogisticsRecord::getFrameId, fid));
        chainTxMapper.delete(new LambdaQueryWrapper<com.qts.entity.ChainTxRecord>().eq(com.qts.entity.ChainTxRecord::getFrameId, fid));
        frameMapper.deleteById(fid);
        traceService.evictCacheSafely(frame.getSerialNo());
        return Result.ok();
    }

    /** 重新计算并返回当前链下哈希（调试/预览用） */
    @GetMapping("/{id}/hash")
    @PreAuthorize(STAFF)
    public Result<Map<String, Object>> currentHash(@PathVariable Long id) throws Exception {
        Frame frame = frameMapper.selectById(id);
        if (frame == null) {
            throw new BizException("车架不存在");
        }
        return Result.ok(traceService.verify(frame));
    }

    /** 获取（或创建）车架二维码 */
    @GetMapping("/{id}/qrcode")
    @PreAuthorize(STAFF)
    public Result<Object> qrcode(@PathVariable Long id) {
        return Result.ok(qrService.getOrCreate(id));
    }

    /**
     * 生成车架质检报告（A4 可打印 HTML）。
     * 前端用 axios 取回文本后以 Blob 打开，避免新标签页丢失 Authorization 头。
     */
    @GetMapping(value = "/{id}/report", produces = "text/html;charset=UTF-8")
    @PreAuthorize("hasAnyRole('ADMIN','QC','OPERATOR')")
    public ResponseEntity<String> report(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(reportService.buildHtml(id));
    }
}
