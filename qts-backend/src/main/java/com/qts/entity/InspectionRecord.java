package com.qts.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_record")
public class InspectionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long frameId;
    /** APPEARANCE外观/DIMENSION尺寸/MECHANIC力学性能 */
    private String item;
    /** PASS/FAIL */
    private String result;
    private String defectDesc;
    private Long inspectorId;
    private String reportNo;
    private LocalDateTime inspectTime;
}
