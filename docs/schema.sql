-- ============================================================
-- 记账系统 MySQL 建表语句
-- 生成时间: 2026-07-27
-- 字符集: utf8mb4
-- 引擎: InnoDB
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 账本表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `ledger`;
CREATE TABLE `ledger` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '账本ID',
    `name`              VARCHAR(100) NOT NULL COMMENT '账本名称',
    `description`       VARCHAR(500) DEFAULT NULL COMMENT '账本描述',
    `owner_id`          BIGINT UNSIGNED NOT NULL COMMENT '所有者用户ID',
    `type`              TINYINT      NOT NULL DEFAULT 1 COMMENT '账本类型：1-个人账本 2-家庭账本 3-团队账本',
    `allow_member_edit` TINYINT      NOT NULL DEFAULT 1 COMMENT '成员是否可修改他人账单：1-允许 0-仅改自己',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账本表';

-- ------------------------------------------------------------
-- 3. 账本成员表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `ledger_member`;
CREATE TABLE `ledger_member` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成员记录ID',
    `ledger_id`  BIGINT UNSIGNED NOT NULL COMMENT '账本ID',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `role`       TINYINT      NOT NULL DEFAULT 3 COMMENT '角色：1-所有者 2-管理员 3-普通成员',
    `joined_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ledger_user` (`ledger_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账本成员表';

-- ------------------------------------------------------------
-- 4. 分类表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `user_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '所属用户ID（预设分类为NULL）',
    `ledger_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '所属账本ID（NULL表示全局可见）',
    `name`       VARCHAR(50)  NOT NULL COMMENT '分类名称',
    `type`       TINYINT      NOT NULL COMMENT '分类类型：1-收入 2-支出',
    `is_preset`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否预设：0-自定义 1-预设',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_name_type` (`user_id`, `name`, `type`),
    KEY `idx_ledger_id` (`ledger_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类表';

-- ------------------------------------------------------------
-- 5. 账单表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `bill`;
CREATE TABLE `bill` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '账单ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `category_id` BIGINT UNSIGNED NOT NULL COMMENT '分类ID',
    `ledger_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '账本ID',
    `amount`      DECIMAL(12,2) NOT NULL COMMENT '金额',
    `type`        TINYINT       NOT NULL COMMENT '账单类型：1-收入 2-支出',
    `remark`      VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `bill_date`   DATE          NOT NULL COMMENT '账单日期',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_ledger_id` (`ledger_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_bill_date` (`bill_date`),
    KEY `idx_user_billdate` (`user_id`, `bill_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账单表';

-- ============================================================
-- 初始数据：预设分类
-- ============================================================

-- 收入类预设分类
INSERT INTO `category` (`name`, `type`, `is_preset`) VALUES
('工资',      1, 1),
('奖金',      1, 1),
('投资收益',  1, 1),
('兼职收入',  1, 1),
('其他收入',  1, 1);

-- 支出类预设分类
INSERT INTO `category` (`name`, `type`, `is_preset`) VALUES
('餐饮',      2, 1),
('交通',      2, 1),
('购物',      2, 1),
('娱乐',      2, 1),
('居住',      2, 1),
('医疗',      2, 1),
('教育',      2, 1),
('其他支出',  2, 1);

SET FOREIGN_KEY_CHECKS = 1;
