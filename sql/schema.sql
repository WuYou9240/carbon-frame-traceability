-- ============================================================
-- 公路自行车碳纤维车架质量溯源系统 数据库设计 (MySQL 8.0)
-- 数据库: qts
-- ============================================================
USE qts;

SET NAMES utf8mb4;

-- 1. 用户表（五类角色：ADMIN 管理员 / OPERATOR 生产操作员 / QC 质检员 / DEALER 经销商 / CONSUMER 消费者）
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password      VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
    real_name     VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
    role          VARCHAR(20)  NOT NULL COMMENT '角色:ADMIN/OPERATOR/QC/DEALER/CONSUMER',
    phone         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0禁用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 原材料批次表（碳布批次）
DROP TABLE IF EXISTS material_batch;
CREATE TABLE material_batch (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    batch_no      VARCHAR(50)  NOT NULL COMMENT '批次号',
    material_name VARCHAR(100) NOT NULL COMMENT '材料名称(如T700碳纤维预浸料)',
    spec          VARCHAR(100) DEFAULT NULL COMMENT '规格(克重/幅宽/树脂含量)',
    supplier      VARCHAR(100) DEFAULT NULL COMMENT '供应商',
    incoming_date DATE         DEFAULT NULL COMMENT '入库日期',
    qc_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '来料检验:PENDING/PASSED/FAILED',
    remark        VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='原材料批次表';

-- 3. 车架档案表
DROP TABLE IF EXISTS frame;
CREATE TABLE frame (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    serial_no        VARCHAR(50)  NOT NULL COMMENT '车架唯一序列号',
    model            VARCHAR(50)  DEFAULT NULL COMMENT '型号(如Aero-R1)',
    size             VARCHAR(20)  DEFAULT NULL COMMENT '尺寸(如52cm)',
    batch_id         BIGINT       DEFAULT NULL COMMENT '碳布批次ID',
    workshop         VARCHAR(50)  DEFAULT NULL COMMENT '生产车间',
    production_date  DATE         DEFAULT NULL COMMENT '生产日期',
    status           VARCHAR(20)  NOT NULL DEFAULT 'IN_PROD' COMMENT '状态:IN_PROD在产/CURED已固化/INSPECTED已质检/SHIPPED已发货',
    current_hash     VARCHAR(64)  DEFAULT NULL COMMENT '当前链下关键数据SHA-256(十六进制)',
    hash_recorded    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已上链存证:1是 0否',
    hash_version     TINYINT      NOT NULL DEFAULT 1 COMMENT '哈希口径版本:1不含批次/2含批次',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_serial_no (serial_no),
    KEY idx_batch (batch_id),
    KEY idx_status (status),
    CONSTRAINT fk_frame_batch FOREIGN KEY (batch_id) REFERENCES material_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车架档案表';

-- 4. 生产工序记录表（碳布选型/自动裁布/人工铺层/高温固化/CNC精加工/无损检测）
DROP TABLE IF EXISTS process_record;
CREATE TABLE process_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    frame_id      BIGINT       NOT NULL COMMENT '车架ID',
    process_type  VARCHAR(30)  NOT NULL COMMENT '工序:CLOTH_SELECTION碳布选型/AUTO_CUTTING自动裁布/LAYUP人工铺层/CURING高温固化/CNC精加工/NDT无损检测',
    operator_id   BIGINT       DEFAULT NULL COMMENT '操作员ID',
    record_data   JSON         DEFAULT NULL COMMENT '工序参数(JSON,如铺层角度/温度曲线/压力等)',
    record_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    remark        VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_frame (frame_id),
    KEY idx_type (process_type),
    CONSTRAINT fk_proc_frame FOREIGN KEY (frame_id) REFERENCES frame (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产工序记录表';

-- 5. 质检记录表
DROP TABLE IF EXISTS inspection_record;
CREATE TABLE inspection_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    frame_id      BIGINT       NOT NULL COMMENT '车架ID',
    item          VARCHAR(30)  NOT NULL COMMENT '质检项目:APPEARANCE外观/DIMENSION尺寸/MECHANIC力学性能',
    result        VARCHAR(10)  NOT NULL COMMENT '结果:PASS/FAIL',
    defect_desc   VARCHAR(255) DEFAULT NULL COMMENT '缺陷描述',
    inspector_id  BIGINT       DEFAULT NULL COMMENT '质检员ID',
    report_no     VARCHAR(50)  DEFAULT NULL COMMENT '质检报告编号',
    inspect_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '质检时间',
    PRIMARY KEY (id),
    KEY idx_frame (frame_id),
    KEY idx_result (result),
    CONSTRAINT fk_insp_frame FOREIGN KEY (frame_id) REFERENCES frame (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检记录表';

-- 6. 物流/销售记录表
DROP TABLE IF EXISTS logistics_record;
CREATE TABLE logistics_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    frame_id      BIGINT       NOT NULL COMMENT '车架ID',
    dealer_id     BIGINT       DEFAULT NULL COMMENT '经销商ID',
    event_type    VARCHAR(20)  NOT NULL COMMENT '类型:STOCK_IN入库/STOCK_OUT出库/SALE销售',
    location      VARCHAR(100) DEFAULT NULL COMMENT '地点/仓库',
    event_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    remark        VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_frame (frame_id),
    CONSTRAINT fk_logi_frame FOREIGN KEY (frame_id) REFERENCES frame (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流记录表';

-- 7. 上链交易记录表
DROP TABLE IF EXISTS chain_tx_record;
CREATE TABLE chain_tx_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    frame_id      BIGINT       DEFAULT NULL COMMENT '车架ID',
    serial_no     VARCHAR(50)  NOT NULL COMMENT '车架序列号',
    tx_hash       VARCHAR(80)  NOT NULL COMMENT '交易哈希',
    block_number  BIGINT       DEFAULT NULL COMMENT '区块高度',
    onchain_hash  VARCHAR(80)  NOT NULL COMMENT '链上存证哈希(0x...)',
    data_version  VARCHAR(20)  DEFAULT NULL COMMENT '数据版本',
    gas_used      BIGINT       DEFAULT NULL COMMENT '交易开销',
    status        VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT '状态:SUCCESS/FAILED',
    tx_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上链时间',
    PRIMARY KEY (id),
    KEY idx_serial (serial_no),
    KEY idx_tx_hash (tx_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上链交易记录表';

-- 8. 操作日志表
DROP TABLE IF EXISTS operation_log;
CREATE TABLE operation_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       DEFAULT NULL COMMENT '用户ID',
    username      VARCHAR(50)  DEFAULT NULL COMMENT '操作人账号',
    operation     VARCHAR(100) NOT NULL COMMENT '操作描述',
    method        VARCHAR(200) DEFAULT NULL COMMENT '请求方法',
    params        TEXT         DEFAULT NULL COMMENT '请求参数',
    ip            VARCHAR(50)  DEFAULT NULL COMMENT '操作IP',
    result        VARCHAR(20)  DEFAULT NULL COMMENT '结果:SUCCESS/FAILED',
    log_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '日志时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_time (log_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- 9. 二维码记录表
DROP TABLE IF EXISTS qr_code_record;
CREATE TABLE qr_code_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    frame_id      BIGINT       NOT NULL COMMENT '车架ID',
    serial_no     VARCHAR(50)  NOT NULL COMMENT '车架序列号',
    qr_content    VARCHAR(255) NOT NULL COMMENT '二维码内容(溯源URL)',
    image_url     VARCHAR(255) DEFAULT NULL COMMENT '二维码图片访问地址',
    generated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    scan_count    INT          NOT NULL DEFAULT 0 COMMENT '累计扫码次数',
    PRIMARY KEY (id),
    UNIQUE KEY uk_frame (frame_id),
    KEY idx_serial (serial_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二维码记录表';

-- 初始化系统管理员（密码 Qts@2026 的 BCrypt 哈希，由后端启动时若表为空则自动创建）
