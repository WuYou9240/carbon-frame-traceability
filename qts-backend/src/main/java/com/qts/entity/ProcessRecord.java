package com.qts.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_record")
public class ProcessRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long frameId;
    /** CLOTH_SELECTION/AUTO_CUTTING/LAYUP/CURING/CNC/NDT */
    private String processType;
    private Long operatorId;
    /** 工序参数 JSON */
    private String recordData;
    private LocalDateTime recordTime;
    private String remark;
}
