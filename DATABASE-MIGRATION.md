# 现有数据库迁移：用户账户禁用字段

此迁移适用于已经运行、`sit_oj.users` 表尚无 `locked` 字段的 MySQL 8 数据库。先迁移数据库，再部署包含新字段的用户服务。不要在现有数据库上执行 `database/init.sql`：它以 `DROP DATABASE` 开始，会删除数据。

## 1. 备份现有数据库

在服务器项目目录执行：

```bash
mkdir -p incident-backups
docker exec -it oj-mysql sh
mysqldump -uroot -p --single-transaction --routines --triggers sit_oj > /tmp/sit_oj-before-locked.sql
exit
docker cp oj-mysql:/tmp/sit_oj-before-locked.sql incident-backups/sit_oj-before-locked.sql
```

`mysqldump` 会提示输入**数据库当前实际使用的** root 密码。确认备份文件非空，并妥善保管。`incident-backups/` 已被 Git 忽略。

## 2. 执行迁移

仍在服务器项目目录，执行：

```bash
docker cp database/migrations/20261015_add_users_locked.sql oj-mysql:/tmp/add_users_locked.sql
docker exec -it oj-mysql mysql -uroot -p sit_oj
```

输入数据库密码后，在 MySQL 提示符执行：

```sql
SOURCE /tmp/add_users_locked.sql;
```

迁移 SQL 增加 `locked TINYINT(1) NOT NULL DEFAULT 0`。MySQL 会将已有用户全部设为 `0`（即 `false`），新用户默认也是 `false`。SQL 末尾会输出 `total_users`、`unlocked_users`、`locked_users`；迁移刚完成时应满足 `total_users = unlocked_users` 且 `locked_users = 0`。

这份 SQL **只执行一次**。如果提示 `Duplicate column name 'locked'`，先用 `SHOW COLUMNS FROM users LIKE 'locked';` 检查当前表结构，不要重复执行。

## 3. 发布和检查

迁移成功后部署新版用户服务和 `sit_oj_backend/frontend/dist`。登录管理员页面，检查用户状态均显示“正常”，然后选一个测试账户执行“禁用”：该账户应无法登录，也不能用原邮箱重新注册；“启用”后应能再次登录。不要用真实管理员账户测试。

若发布失败，先停止新版用户服务并排查；需要回退数据时使用第 1 步的完整备份。不要运行初始化脚本或删除用户记录来回退。
