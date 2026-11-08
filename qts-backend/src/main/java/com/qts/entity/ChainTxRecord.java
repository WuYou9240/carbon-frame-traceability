package com.qts.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chain_tx_record")
public class ChainTxRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long frameId;
    private String serialNo;
    private String txHash;
    private Long blockNumber;
    /** 链上存证哈希 0x... */
    private String onchainHash;
    private String dataVersion;
    private Long gasUsed;
    private String status;
    private LocalDateTime txTime;
}
