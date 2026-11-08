package com.qts.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qts.common.Result;
import com.qts.entity.Frame;
import com.qts.entity.QrCodeRecord;
import com.qts.mapper.FrameMapper;
import com.qts.service.AntiCounterfeitService;
import com.qts.service.QrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 二维码图片与防伪校验（公开接口，扫码即用）
 */
@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
public class QrController {

    private final QrService qrService;
    private final FrameMapper frameMapper;
    private final AntiCounterfeitService antiCounterfeitService;

    @GetMapping("/{serialNo}.png")
    public ResponseEntity<byte[]> png(@PathVariable String serialNo) {
        Frame frame = frameMapper.selectOne(
                new LambdaQueryWrapper<Frame>().eq(Frame::getSerialNo, serialNo));
        if (frame == null) {
            return ResponseEntity.notFound().build();
        }
        QrCodeRecord record = qrService.getOrCreate(frame.getId());
        byte[] png = qrService.generatePng(record.getQrContent());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    /**
     * 二维码防伪校验：四层校验（序列号 / 二维码内容 / 上链状态 / 数据完整性）
     * 公开接口，消费者扫码后由溯源页调用。
     */
    @GetMapping("/{serialNo}/verify")
    public Result<Map<String, Object>> verify(@PathVariable String serialNo) throws Exception {
        return Result.ok(antiCounterfeitService.verifyBySerial(serialNo));
    }
}
