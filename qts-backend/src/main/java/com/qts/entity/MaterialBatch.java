package com.qts.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("material_batch")
public class MaterialBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private String materialName;
    private String spec;
    private String supplier;
    private LocalDate incomingDate;
    /** PENDING/PASSED/FAILED */
    private String qcStatus;
    private String remark;
    private LocalDateTime createdAt;
}
