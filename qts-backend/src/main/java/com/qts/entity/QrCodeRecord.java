package com.qts.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qr_code_record")
public class QrCodeRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long frameId;
    private String serialNo;
    private String qrContent;
    private String imageUrl;
    private LocalDateTime generatedAt;
    private Integer scanCount;
}
