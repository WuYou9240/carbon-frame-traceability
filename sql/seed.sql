-- ============================================================
-- 质量溯源系统 · 初始化种子数据
-- 在 schema.sql 之后执行：mysql -u qts -p qts < seed.sql
--
-- 说明：
--   1. 五个演示账号，密码统一为 Qts@2026（BCrypt 已哈希，勿手改明文）
--   2. 一条标准碳布原料批次 T700-2026-1026，车架录入时直接选用
--   3. 车架 / 工序 / 质检 / 物流 / 上链记录不预置 —— 这些需要真实上链，
--      换环境时链上数据无法迁移，请在页面现场新建，或跑 test_e2e.py 生成
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 演示账号 ----------
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role`, `phone`, `status`) VALUES
(1, 'admin',    '$2a$10$DWV9v2WLRjHKLRT./b.SkePGrogUyUJLrQ71r86mPgpaPLhz6/1AK', '系统管理员', 'ADMIN',    NULL, 1),
(2, 'operator', '$2a$10$g6B2RK4w61KnbsoZ7JBhkeokgtD1QS8Lmcj1ZAiav44HnUaKkMwk2', '张生产',     'OPERATOR', NULL, 1),
(3, 'qc',       '$2a$10$CA9UIQGvVvxfpd9Gi8FSjeViarjgmJ6xfw1BXgYi6yFHa/xeMaTqi', '李质检',     'QC',       NULL, 1),
(4, 'dealer',   '$2a$10$S2h9YGm/bjSGht2z3IGij.cawdzldBeJRZNzXAdJZKLoChQXEw0Im', '王经销',     'DEALER',   NULL, 1),
(5, 'xfz',      '$2a$10$5F/RLtABB61/tfwE7hT2Je2hfZEeFo.MoMX7xdgzYXZg1vhWZT1wq', '消费者',     'CONSUMER', NULL, 1)
ON DUPLICATE KEY UPDATE `password` = VALUES(`password`), `role` = VALUES(`role`);

-- ---------- 标准原料批次 ----------
INSERT INTO `material_batch` (`id`, `batch_no`, `material_name`, `spec`, `supplier`, `incoming_date`, `qc_status`, `remark`) VALUES
(1, 'T700-2026-1026', 'T700 碳纤维预浸料', '12K/200g/树脂含量32%', '东丽中国', '2026-10-20', 'PASSED', '标准演示批次')
ON DUPLICATE KEY UPDATE `batch_no` = VALUES(`batch_no`);
