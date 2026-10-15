# SIT-OJ

Vue 前端、Spring Boot 微服务和 QingdaoU JudgeServer 组成的在线评测系统。

## 安全部署

**2026-10-10 之前公开过的 JWT、数据库、判题和邮箱凭证不能继续使用。**
请先阅读 [安全部署与旧站迁移](SECURITY-DEPLOY.md)，完成凭证轮换后再开放公网。

请求审计已挂载到宿主机 `data/logs/`。按提交编号、账号或 auth 指纹排查的命令见 [请求审计与持久化日志](AUDIT-LOGGING.md)；日志记录经过验签的账号以及尝试冒用的用户 ID，不保存完整登录令牌。

不使用 GitHub 发布时，参见 [Windows 通过 SSH 上传与部署](SSH-DEPLOY.md)，保留服务器现有 `.env` 和 `data/`。

环境：Ubuntu、Java 17、Maven、Docker Engine 与 Docker Compose v2、Python 3。开发前端另需 Node.js。

新安装或本地开发可运行：

```bash
node ops/generate-secrets.mjs
```

它生成被 Git 忽略的私有 `.env`，不会覆盖已有文件。已有部署先按[密钥修改与迁移说明](SECURITY-DEPLOY.md)准备最终的新值；当前本地 `.env` 已按要求恢复旧凭证作对照，不能原样用于安全部署，新随机值保存在私有 `.secrets/` 备份中。QQ 邮箱授权码必须自行撤销旧值并重新申请，默认关闭发信。

保留旧容器，不要先执行 `docker compose down`。完成 `.env` 修改后，在服务器的项目目录执行：

```bash
chmod 600 .env
bash deploy.sh
```

旧站部署会先备份当前日志和数据库，再更新已有数据库与队列中的密码。不要删除 `data/` 或使用重建数据库的方法更换密码。发布包需包含 `sit_oj_backend/frontend/dist`；修改前端后需重新构建并同步这个目录。

现有用户表增加账户禁用字段时，请先按[数据库迁移说明](DATABASE-MIGRATION.md)备份并迁移，再部署新版用户服务。

## 回归验证

```bash
mvn test -f sit_oj_backend/pom.xml
python3 -m unittest discover -s ops -p 'test_secure_deploy.py'
```

前端源码在 `sit_oj_frontend`，部署产物在 `sit_oj_backend/frontend/dist`。两者均纳入版本管理；`node_modules` 和前端临时 `dist` 不提交。
