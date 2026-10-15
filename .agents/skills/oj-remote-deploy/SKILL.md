---
name: oj-remote-deploy
description: Connect to the SIT_OJ Ubuntu server or upload and deploy OJ frontend, backend, or database changes over SSH.
---

# OJ 远端部署

向远端服务器推送部署文件时，目标是 `ubuntu@106.54.237.248:22`，项目目录是 `/home/ubuntu/sit_oj/SIT_OJ`。本机登录私钥路径、PowerShell 连接命令和最近连接状态见 [server-connection.md](../../../docs/agents/server-connection.md)。先读该文件，再连接或上传。

发布前核对远端实际目录、运行中的 Compose 项目、文件挂载和数据库状态。涉及 `users.locked` 的升级按 [DATABASE-MIGRATION.md](../../../DATABASE-MIGRATION.md) 的顺序：完整备份、仅执行一次迁移、验证计数、更新 `oj-user` 和前端 `dist` 挂载。保留服务器现有 `.env` 与 `data/`。
