package com.qts.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qts.common.Auths;
import com.qts.common.BizException;
import com.qts.common.Result;
import com.qts.entity.Frame;
import com.qts.entity.InspectionRecord;
import com.qts.entity.ProcessRecord;
import com.qts.mapper.FrameMapper;
import com.qts.mapper.InspectionRecordMapper;
import com.qts.mapper.ProcessRecordMapper;
import com.qts.service.AttestService;
import com.qts.service.TraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生产工序记录 + 质检记录（质检全部通过后自动计算 SHA-256 并上链存证）
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecordController {

    private final ProcessRecordMapper processMapper;
    private final InspectionRecordMapper inspectionMapper;
    private final FrameMapper frameMapper;
    private final TraceService traceService;
    private final AttestService attestService;

    private static final Set<String> PROCESS_TYPES = Set.of(
            "CLOTH_SELECTION", "AUTO_CUTTING", "LAYUP", "CURING", "CNC", "NDT");

    // ================= 生产工序 =================

    @GetMapping("/frames/{frameId}/processes")
    public Result<List<ProcessRecord>> processes(@PathVariable Long frameId) {
        return Result.ok(processMapper.selectList(
                new LambdaQueryWrapper<ProcessRecord>().eq(ProcessRecord::getFrameId, frameId).orderByAsc(ProcessRecord::getId)));
    }

    /**
     * 录入工序。插入工序记录、状态流转、重置存证标记三步必须原子完成：
     * 中途失败会留下「工序已记但状态未变」或「状态变了但存证标记没重置」的不一致。
     */
    @PostMapping("/frames/{frameId}/processes")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Transactional(rollbackFor = Exception.class)
    public Result<ProcessRecord> addProcess(@PathVariable Long frameId, @RequestBody ProcessRecord record) {
        Frame frame = frameMapper.selectById(frameId);
        if (frame == null) {
            throw new BizException("车架不存在");
        }
        if (!PROCESS_TYPES.contains(record.getProcessType())) {
            throw new BizException("非法工序类型: " + record.getProcessType());
        }
        record.setFrameId(frameId);
        record.setOperatorId(Auths.me().id());
        record.setRecordTime(LocalDateTime.now());
        processMapper.insert(record);

        // 固化完成 → 状态流转
        if ("CURING".equals(record.getProcessType()) && "IN_PROD".equals(frame.getStatus())) {
            frame.setStatus("CURED");
            frameMapper.updateById(frame);
        }
        // 已上链的车架新增工序 → 数据变化，原存证失效，标记待重新上链
        markRechainIfNeeded(frame);
        traceService.evictCacheSafely(frame.getSerialNo());
        return Result.ok(record);
    }

    /** 已上链的车架链下数据发生变化 → 重置存证标记，需重新上链 */
    private void markRechainIfNeeded(Frame frame) {
        if (frame.getHashRecorded() != null && frame.getHashRecorded() == 1) {
            Frame mark = new Frame();
            mark.setId(frame.getId());
            mark.setHashRecorded(0);
            frameMapper.updateById(mark);
            frame.setHashRecorded(0);
        }
    }

    // ================= 质检记录 =================

    @GetMapping("/inspections")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','QC','DEALER')")
    public Result<List<InspectionRecord>> byFrame(@RequestParam(required = false) Long frameId) {
        LambdaQueryWrapper<InspectionRecord> qw = new LambdaQueryWrapper<>();
        if (frameId != null) qw.eq(InspectionRecord::getFrameId, frameId);
        qw.orderByDesc(InspectionRecord::getId);
        return Result.ok(inspectionMapper.selectList(qw));
    }

    /**
     * 录入质检。质检记录 + 存证标记重置在同一事务内；上链则等事务提交后再触发，
     * 原因有两个：① 区块链写入是秒级远程调用，不该占着数据库连接；
     * ② 上链前要读全部质检记录，必须能读到本次已提交的那条。
     */
    @PostMapping("/inspections")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    @Transactional(rollbackFor = Exception.class)
    public Result<InspectionRecord> addInspection(@RequestBody InspectionRecord record) {
        Frame frame = frameMapper.selectById(record.getFrameId());
        if (frame == null) {
            throw new BizException("车架不存在");
        }
        if (!AttestService.QC_ITEMS.contains(record.getItem())) {
            throw new BizException("非法质检项目: " + record.getItem());
        }
        if (!"PASS".equals(record.getResult()) && !"FAIL".equals(record.getResult())) {
            throw new BizException("质检结果只能为 PASS/FAIL");
        }
        record.setInspectorId(Auths.me().id());
        record.setInspectTime(LocalDateTime.now());
        if (record.getReportNo() == null || record.getReportNo().isBlank()) {
            record.setReportNo("QC" + System.currentTimeMillis());
        }
        inspectionMapper.insert(record);
        // 已上链的车架新增质检 → 数据变化，原存证失效（三项重新齐后自动重新存证）
        markRechainIfNeeded(frame);
        traceService.evictCacheSafely(frame.getSerialNo());

        // 三项质检全部完成 → 事务提交后自动上链存证
        if ("PASS".equals(record.getResult())) {
            triggerAttestAfterCommit(frame.getId());
        }
        return Result.ok(record);
    }

    /** 事务提交后再统计质检并上链，保证能读到本次插入的记录，也避免长事务 */
    private void triggerAttestAfterCommit(Long frameId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doAttest(frameId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doAttest(frameId);
            }
        });
    }

    private void doAttest(Long frameId) {
        try {
            Frame fresh = frameMapper.selectById(frameId);
            if (fresh != null) {
                attestService.autoRecordToChain(fresh);
            }
        } catch (Exception e) {
            // 自动上链失败不阻断业务，管理员可用「手动上链」兜底
            log.error("质检后自动上链失败 frameId={}", frameId, e);
        }
    }

    /** 修改质检结果（质检员/管理员纠错；若该车架已上链，标记为待重新上链） */
    @PutMapping("/inspections/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    @Transactional(rollbackFor = Exception.class)
    public Result<InspectionRecord> updateInspection(@PathVariable Long id, @RequestBody InspectionRecord body) {
        InspectionRecord record = inspectionMapper.selectById(id);
        if (record == null) {
            throw new BizException("质检记录不存在");
        }
        if (!"PASS".equals(body.getResult()) && !"FAIL".equals(body.getResult())) {
            throw new BizException("质检结果只能为 PASS/FAIL");
        }
        record.setResult(body.getResult());
        if (body.getDefectDesc() != null) {
            record.setDefectDesc(body.getDefectDesc());
        }
        inspectionMapper.updateById(record);

        Frame frame = frameMapper.selectById(record.getFrameId());
        if (frame != null) {
            // 已上链的车架数据发生变更 → 原存证不再代表当前数据，需重新上链
            if (frame.getHashRecorded() != null && frame.getHashRecorded() == 1) {
                frame.setHashRecorded(0);
                frameMapper.updateById(frame);
            }
            traceService.evictCacheSafely(frame.getSerialNo());
        }
        return Result.ok(record);
    }

    /** 手动触发上链（管理员兜底操作） */
    @PostMapping("/frames/{frameId}/record-onchain")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    public Result<Map<String, Object>> recordOnChain(@PathVariable Long frameId) throws Exception {
        Frame frame = frameMapper.selectById(frameId);
        if (frame == null) {
            throw new BizException("车架不存在");
        }
        return Result.ok(attestService.recordToChain(frame));
    }
}
