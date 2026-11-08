package com.qts.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("frame")
public class Frame {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String serialNo;
    private String model;
    private String size;
    private Long batchId;
    private String workshop;
    private LocalDate productionDate;
    /** IN_PROD在产/CURED已固化/INSPECTED已质检/SHIPPED已发货 */
    private String status;
    /** 当前链下关键数据 SHA-256（十六进制） */
    private String currentHash;
    /**
     * 哈希口径版本：1=早期公式（不含碳布批次）；2=含批次（当前）。
     * 校验时必须按该车架存证时的版本重算，否则公式升级会把历史数据全部误判为「已篡改」。
     */
    private Integer hashVersion;
    /** 是否已上链存证 */
    private Integer hashRecorded;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
