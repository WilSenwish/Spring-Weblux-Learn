-- 获取旧测试用户 ID（用于清除关联数据，如果用户不存在则为 NULL，NULL 比较不会匹配任何记录，是安全的）
SET @old_admin_id = (SELECT id FROM user WHERE username = 'admin' ORDER BY id DESC LIMIT 1);
SET @old_testuser_id = (SELECT id FROM user WHERE username = 'testuser' ORDER BY id DESC LIMIT 1);

-- 清除旧测试数据（顺序：先删依赖表，再删被依赖表）
DELETE FROM bill WHERE user_id = @old_admin_id OR user_id = @old_testuser_id;
DELETE FROM ledger_member WHERE user_id = @old_admin_id OR user_id = @old_testuser_id;
DELETE FROM ledger_member WHERE ledger_id IN (SELECT id FROM ledger WHERE owner_id = @old_admin_id OR owner_id = @old_testuser_id);
DELETE FROM ledger WHERE owner_id = @old_admin_id OR owner_id = @old_testuser_id;
DELETE FROM user WHERE username IN ('admin', 'testuser');

-- 插入预设分类（使用 hex 编码避免 R2DBC 字符集问题；INSERT IGNORE 依赖唯一约束 uk_user_name_type 实现幂等，已存在则跳过，id 保持不变）
INSERT IGNORE INTO category (user_id, name, type, is_preset) VALUES
(0, 0xE5B7A5E8B584, 1, 1),
(0, 0xE5A596E98791, 1, 1),
(0, 0xE68A95E8B584E694B6E79B8A, 1, 1),
(0, 0xE585B6E4BB96E694B6E585A5, 1, 1),
(0, 0xE9A490E9A5AE, 2, 1),
(0, 0xE4BAA4E9809A, 2, 1),
(0, 0xE8B4ADE789A9, 2, 1),
(0, 0xE5A8B1E4B990, 2, 1),
(0, 0xE58CBBE79697, 2, 1),
(0, 0xE69599E882B2, 2, 1),
(0, 0xE4BD8FE688BF, 2, 1),
(0, 0xE585B6E4BB96E694AFE587BA, 2, 1);

-- 获取预设分类 ID（按插入顺序，用 OFFSET 避免 hex 字符串比较）
SET @cat_1 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 0);
SET @cat_2 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 1);
SET @cat_3 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 2);
SET @cat_4 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 3);
SET @cat_5 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 4);
SET @cat_6 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 5);
SET @cat_7 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 6);
SET @cat_8 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 7);
SET @cat_9 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 8);
SET @cat_10 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 9);
SET @cat_11 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 10);
SET @cat_12 = (SELECT id FROM category WHERE user_id = 0 ORDER BY id LIMIT 1 OFFSET 11);

-- 插入测试用户（密码为 123456，BCrypt 加密哈希）
INSERT INTO user (username, password) VALUES ('admin', '$2a$10$Ynlsb75VcYJWolJ.FD1Cg.zhJNyeB9ELrqSaILW8W87Z.Iopl58fC');
INSERT INTO user (username, password) VALUES ('testuser', '$2a$10$Ynlsb75VcYJWolJ.FD1Cg.zhJNyeB9ELrqSaILW8W87Z.Iopl58fC');

-- 获取新用户 ID
SET @admin_id = (SELECT id FROM user WHERE username = 'admin' ORDER BY id DESC LIMIT 1);
SET @testuser_id = (SELECT id FROM user WHERE username = 'testuser' ORDER BY id DESC LIMIT 1);

-- 插入默认账本（name/description 用 hex 编码）
INSERT INTO ledger (name, description, owner_id, type) VALUES (0xE9BB98E8AEA4E8B4A6E69CAC, 0xE7B3BBE7BB9FE887AAE58AA8E5889BE5BBBAE79A84E4B8AAE4BABAE8B4A6E69CAC, @admin_id, 1);
INSERT INTO ledger (name, description, owner_id, type) VALUES (0xE9BB98E8AEA4E8B4A6E69CAC, 0xE7B3BBE7BB9FE887AAE58AA8E5889BE5BBBAE79A84E4B8AAE4BABAE8B4A6E69CAC, @testuser_id, 1);

-- 获取默认账本 ID
SET @ledger_id_1 = (SELECT id FROM ledger WHERE owner_id = @admin_id ORDER BY id DESC LIMIT 1);
SET @ledger_id_2 = (SELECT id FROM ledger WHERE owner_id = @testuser_id ORDER BY id DESC LIMIT 1);

-- 插入账本成员（所有者）
INSERT INTO ledger_member (ledger_id, user_id, role) VALUES (@ledger_id_1, @admin_id, 1);
INSERT INTO ledger_member (ledger_id, user_id, role) VALUES (@ledger_id_2, @testuser_id, 1);

-- 插入测试账单数据（remark 用 hex 编码，ledger_id 关联默认账本，category_id 用变量）
INSERT INTO bill (user_id, category_id, ledger_id, amount, type, remark, bill_date) VALUES
(@admin_id, @cat_1, @ledger_id_1, 15000.00, 1, 0x32303236E5B9B431E69C88E5B7A5E8B584, '2026-01-10'),
(@admin_id, @cat_2, @ledger_id_1, 5000.00, 1, 0xE5B9B4E7BB88E5A596E98791, '2026-01-20'),
(@admin_id, @cat_3, @ledger_id_1, 2000.00, 1, 0xE882A1E7A5A8E694B6E79B8A, '2026-01-15'),
(@admin_id, @cat_5, @ledger_id_1, 2500.00, 2, 0xE9A490E9A5AE, '2026-01-01'),
(@admin_id, @cat_6, @ledger_id_1, 500.00, 2, 0xE4BAA4E9809AE8B4B9, '2026-01-01'),
(@admin_id, @cat_7, @ledger_id_1, 3000.00, 2, 0xE8B4ADE789A9, '2026-01-05'),
(@admin_id, @cat_8, @ledger_id_1, 800.00, 2, 0xE794B5E5BDB1E5A8B1E4B990, '2026-01-08'),
(@admin_id, @cat_9, @ledger_id_1, 300.00, 2, 0xE79C8BE79785, '2026-01-12'),
(@admin_id, @cat_10, @ledger_id_1, 1500.00, 2, 0xE4B9A6E7B18DE69599E69D90, '2026-01-18'),
(@admin_id, @cat_11, @ledger_id_1, 5000.00, 2, 0xE688BFE7A79F, '2026-01-01'),
(@testuser_id, @cat_1, @ledger_id_2, 8000.00, 1, 0xE5B7A5E8B584, '2026-01-10'),
(@testuser_id, @cat_5, @ledger_id_2, 1500.00, 2, 0xE9A490E9A5AE, '2026-01-01'),
(@testuser_id, @cat_6, @ledger_id_2, 300.00, 2, 0xE4BAA4E9809A, '2026-01-01'),
(@testuser_id, @cat_7, @ledger_id_2, 2000.00, 2, 0xE8B4ADE789A9, '2026-01-10'),
(@testuser_id, @cat_5, @ledger_id_2, 35.50, 2, 0xE58D88E9A490, '2026-01-15'),
(@testuser_id, @cat_6, @ledger_id_2, 15.00, 2, 0xE59CB0E99381E8B4B9, '2026-01-15'),
(@testuser_id, @cat_5, @ledger_id_2, 45.00, 2, 0xE6999AE9A490, '2026-01-15');
