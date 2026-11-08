package com.qts.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.qts.entity.Frame;
import com.qts.entity.QrCodeRecord;
import com.qts.config.QrBaseUrlResolver;
import com.qts.mapper.FrameMapper;
import com.qts.mapper.QrCodeRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 二维码服务：一物一码，二维码内容 = 溯源页地址 + 车架序列号
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrService {

    private final QrCodeRecordMapper qrMapper;
    private final FrameMapper frameMapper;
    private final QrBaseUrlResolver qrBaseUrlResolver;

    /** 生成二维码 PNG（400x400） */
    public byte[] generatePng(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 400, 400,
                    Map.of(EncodeHintType.MARGIN, 2, EncodeHintType.CHARACTER_SET, "UTF-8"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("二维码生成失败", e);
        }
    }

    /** 获取（或首次创建）车架的二维码记录 */
    public QrCodeRecord getOrCreate(Long frameId) {
        QrCodeRecord record = qrMapper.selectOne(
                new LambdaQueryWrapper<QrCodeRecord>().eq(QrCodeRecord::getFrameId, frameId));
        if (record != null) {
            return record;
        }
        Frame frame = frameMapper.selectById(frameId);
        if (frame == null) {
            throw new IllegalArgumentException("车架不存在");
        }
        record = new QrCodeRecord();
        record.setFrameId(frameId);
        record.setSerialNo(frame.getSerialNo());
        record.setQrContent(qrBaseUrlResolver.base() + frame.getSerialNo());
        record.setImageUrl("/api/qr/" + frame.getSerialNo() + ".png");
        record.setGeneratedAt(LocalDateTime.now());
        record.setScanCount(0);
        qrMapper.insert(record);
        return record;
    }

    /** 重建二维码记录（qr-base-url 变更后可刷新内容） */
    public QrCodeRecord refresh(Long frameId) {
        QrCodeRecord record = getOrCreate(frameId);
        Frame frame = frameMapper.selectById(frameId);
        record.setQrContent(qrBaseUrlResolver.base() + frame.getSerialNo());
        qrMapper.updateById(record);
        return record;
    }

    /**
     * 启动时把历史二维码记录同步到当前基地址。
     *
     * 换网络、换端口、或者从「前端 5173 + 后端 18080」切到单 jar 部署之后，
     * 之前入库的 qr_content 还指向老地址，扫出来打不开。这里统一纠正一次，
     * 免得答辩现场一张张手动刷新。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncQrBaseUrl() {
        String base = qrBaseUrlResolver.base();
        List<QrCodeRecord> all = qrMapper.selectList(null);
        int fixed = 0;
        for (QrCodeRecord r : all) {
            String expected = base + r.getSerialNo();
            if (!expected.equals(r.getQrContent())) {
                r.setQrContent(expected);
                qrMapper.updateById(r);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("二维码跳转地址已同步为 {}，共更新 {} 条", base, fixed);
        }
    }
}
