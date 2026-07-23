-- 创建数据库
CREATE DATABASE IF NOT EXISTS webflux_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE webflux_db;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '加密密码(BCrypt)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 分类表
CREATE TABLE IF NOT EXISTS category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT DEFAULT 0 COMMENT '用户ID（预设分类为0）',
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    type TINYINT NOT NULL COMMENT '1-收入 2-支出',
    is_preset TINYINT DEFAULT 0 COMMENT '0-自定义 1-预设',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_type (type),
    UNIQUE KEY uk_user_name_type (user_id, name, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

-- 账本表
CREATE TABLE IF NOT EXISTS ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '账本名称',
    description VARCHAR(255) COMMENT '账本描述',
    owner_id BIGINT NOT NULL COMMENT '创建者用户ID',
    type TINYINT NOT NULL DEFAULT 1 COMMENT '1-个人 2-共享',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_owner_id (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账本表';

-- 账本成员表
CREATE TABLE IF NOT EXISTS ledger_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ledger_id BIGINT NOT NULL COMMENT '账本ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role TINYINT NOT NULL DEFAULT 3 COMMENT '1-所有者 2-管理员 3-普通成员',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ledger_id (ledger_id),
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_ledger_user (ledger_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账本成员表';

-- 账单表
CREATE TABLE IF NOT EXISTS bill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    ledger_id BIGINT COMMENT '账本ID',
    amount DECIMAL(10,2) NOT NULL COMMENT '金额',
    type TINYINT NOT NULL COMMENT '1-收入 2-支出',
    remark VARCHAR(255) COMMENT '备注',
    bill_date DATE NOT NULL COMMENT '账单日期',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_date (user_id, bill_date),
    INDEX idx_user_category (user_id, category_id),
    INDEX idx_user_type (user_id, type),
    INDEX idx_ledger_id (ledger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账单表';

-- ===== 数据库表结构升级（兼容已有数据库，幂等执行） =====

-- user 表：移除已废弃的 salt 列（BCrypt 自带盐值）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'salt');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE `user` DROP COLUMN salt', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- category 表：将 user_id 默认值改为 0，仅清理历史脏数据（user_id IS NULL），保留 user_id=0 的预设分类（由 data.sql 用 INSERT IGNORE 幂等初始化）
ALTER TABLE category MODIFY COLUMN user_id BIGINT DEFAULT 0 COMMENT '用户ID（预设分类为0）';
DELETE FROM category WHERE user_id IS NULL;

-- bill 表：添加 ledger_id 列（如果不存在）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bill' AND column_name = 'ledger_id');
SET @sql = IF(@col_exists > 0, 'SELECT 1', 'ALTER TABLE bill ADD COLUMN ledger_id BIGINT COMMENT ''账本ID'' AFTER category_id');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- bill 表：添加 idx_ledger_id 索引（如果不存在）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bill' AND index_name = 'idx_ledger_id');
SET @sql = IF(@idx_exists > 0, 'SELECT 1', 'ALTER TABLE bill ADD INDEX idx_ledger_id (ledger_id)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- category 表：添加 ledger_id 列（如果不存在），用于分类与账本绑定
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'category' AND column_name = 'ledger_id');
SET @sql = IF(@col_exists > 0, 'SELECT 1', 'ALTER TABLE category ADD COLUMN ledger_id BIGINT COMMENT ''账本ID（NULL表示全局可见）'' AFTER user_id');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- category 表：添加 idx_ledger_id 索引（如果不存在）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'category' AND index_name = 'idx_ledger_id');
SET @sql = IF(@idx_exists > 0, 'SELECT 1', 'ALTER TABLE category ADD INDEX idx_ledger_id (ledger_id)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ledger 表：添加 allow_member_edit 列（如果不存在）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ledger' AND column_name = 'allow_member_edit');
SET @sql = IF(@col_exists > 0, 'SELECT 1', 'ALTER TABLE ledger ADD COLUMN allow_member_edit TINYINT DEFAULT 1 COMMENT ''成员是否可修改他人账单（1-允许 0-仅改自己）'' AFTER type');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
