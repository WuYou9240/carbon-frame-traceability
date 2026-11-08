package com.qts.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qts.common.Result;
import com.qts.entity.MaterialBatch;
import com.qts.mapper.MaterialBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public class MaterialController {

    private final MaterialBatchMapper batchMapper;

    @GetMapping
    public Result<Page<MaterialBatch>> list(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String batchNo) {
        LambdaQueryWrapper<MaterialBatch> qw = new LambdaQueryWrapper<>();
        if (batchNo != null && !batchNo.isBlank()) {
            qw.like(MaterialBatch::getBatchNo, batchNo);
        }
        qw.orderByDesc(MaterialBatch::getId);
        return Result.ok(batchMapper.selectPage(new Page<>(current, size), qw));
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<MaterialBatch> create(@RequestBody MaterialBatch batch) {
        batchMapper.insert(batch);
        return Result.ok(batch);
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@PathVariable Long id, @RequestBody MaterialBatch batch) {
        batch.setId(id);
        batchMapper.updateById(batch);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        batchMapper.deleteById(id);
        return Result.ok();
    }
}
