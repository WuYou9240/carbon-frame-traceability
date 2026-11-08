package com.qts.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("logistics_record")
public class LogisticsRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long frameId;
    private Long dealerId;
    /** STOCK_IN/STOCK_OUT/SALE */
    private String eventType;
    private String location;
    private LocalDateTime eventTime;
    private String remark;
}
