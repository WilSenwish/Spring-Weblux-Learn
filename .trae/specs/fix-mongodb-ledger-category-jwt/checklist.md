# Checklist

## MongoDB 初始数据同步
- [x] 重启后 MongoDB `bill_document` 集合数据与 MySQL `bill` 表一致（count 和字段值）

## 记账前置校验
- [x] 用户无账本时创建账单返回明确错误"请先创建账本"
- [x] 用户有默认账本且未指定 ledgerId 时自动使用默认账本

## 分类与账本绑定
- [x] `category` 表存在 `ledger_id` 列和索引
- [x] `Category.java` 实体包含 `ledgerId` 字段
- [x] `CategoryRequest.java` DTO 包含 `ledgerId` 字段
- [x] 预设分类的 `ledger_id` 为 NULL
- [x] 查询分类接口支持 `ledgerId` 参数，返回预设分类 + 该账本自定义分类
- [x] 创建自定义分类时 `ledger_id` 关联到账本

## 账本成员数据修改权限控制
- [x] `ledger` 表存在 `allow_member_edit` 列
- [x] `Ledger.java` 实体包含 `allowMemberEdit` 字段
- [x] `LedgerRequest.java` DTO 包含 `allowMemberEdit` 字段
- [x] `allow_member_edit=1` 时成员可修改他人账单
- [x] `allow_member_edit=0` 时普通成员只能修改自己的账单
- [x] `allow_member_edit=0` 时所有者/管理员仍可修改任何账单

## JWT 工具类提取到 accounting-common
- [x] `accounting-common` 中存在 `security/JwtUtil.java`，包含同步方法
- [x] Webflux `JwtUtil` 委托/继承公共类，保留 Mono 包装
- [x] WebMVC `JwtUtil` 委托/继承公共类
- [x] JWT 登录接口测试通过（双栈）

## 编译与运行验证
- [x] `mvn clean compile` 全项目编译通过
- [x] 双栈后端正常启动
- [x] MongoDB 同步验证通过
- [x] 所有接口功能正常
