# OJ 远端服务器连接

本机工作目录：`D:\SIT_OJ`。远端部署目录：`/home/ubuntu/sit_oj/SIT_OJ`。

## SSH 连接

在 Windows PowerShell 中执行：

```powershell
$ojKey = 'C:\Users\92484\.ssh\win_connect.pem'
$ojServer = '106.54.237.248'
$ojLogin = 'ubuntu'
$ojPort = 22
$ojDestination = "${ojLogin}@${ojServer}"
ssh -i "$ojKey" -p $ojPort $ojDestination
```

执行远端命令时可复用上述变量，例如：

```powershell
ssh -i "$ojKey" -p $ojPort $ojDestination 'cd /home/ubuntu/sit_oj/SIT_OJ && pwd'
```

上传文件时使用 `scp -i "$ojKey" -P $ojPort <本地文件> "${ojDestination}:/home/ubuntu/"`，其中 `scp` 的端口参数是大写 `-P`。先核对远端工作树、Compose 挂载及运行状态，再将文件放入项目目录。

## 发布范围与顺序

本次发布目标是 `sit_oj_backend/frontend/dist` 的前端文件挂载和 `oj-user` 服务。现有 Compose 文件将 `./sit_oj_backend/frontend/dist` 只读挂载到 `/usr/share/nginx/html`。数据库升级按仓库根目录的 [DATABASE-MIGRATION.md](../../DATABASE-MIGRATION.md) 执行：核对 `users.locked` 是否存在，备份现有 `sit_oj` 数据库，执行一次 `database/migrations/20261015_add_users_locked.sql`，验证计数后再发布 user 服务和前端文件。

私钥仅保留在本机路径，不上传到项目或服务器。服务器的 `.env` 和 `data/` 是运行数据，发布时保留现有内容。

## 最近一次连接与部署检查

2026-10-15：`106.54.237.248:22` 可达，已知 ED25519 主机指纹匹配 `SHA256:VjzuJpCP7/a2w6havb1bqPQx8HUYB3W8PWYSpHEazgg`；`ubuntu` 使用上述私钥成功登录。远端目录与前端只读挂载已核对。

同日迁移 `users.locked` 后，数据库计数为总用户 55、未禁用 55、禁用 0。迁移前备份位于服务器的 `incident-backups/sit_oj-before-locked-20261015-112709.sql`，更新前的相关代码与前端备份位于 `incident-backups/code-before-user-frontend-20261015.tar.gz`。新版 user 镜像已构建，前端文件已更新。

## 部署目录保留范围

- 保留 `.env`、`data/` 和权限为 `700` 的 `incident-backups/`。备份文件权限为 `600`；旧 `.env.before-upload-20261011-113326` 已移到该备份目录。
- 保留 `docker-compose.yml`、`deploy.sh`、`database/`、`ops/`、各后端模块的源码、POM、Dockerfile 与 `target/*-exec.jar`，以及 `sit_oj_backend/frontend/dist`、`nginx.conf`、`oj-proxy.conf`。当前 `deploy.sh` 在服务器运行 Maven，Compose 构建使用各模块的 JAR，这些文件用于后续重建。
- 服务器约 3.6 GiB 内存且没有 swap。执行 Maven 或 Docker 构建前先查看 `free -h` 和容器内存；2026-10-15 构建时先暂停 OJ 应用容器、保留 MySQL，构建后恢复并复核全部容器。
- 2026-10-15 已删除前端 `dist/assets` 中不属于本地当前构建的 70 个旧哈希资源（约 12.5 MB），以及 `/home/ubuntu/` 下本次与 2026-10-11 的上传压缩包、校验文件、临时脚本和 `.oj-upload/env.new`。保留现用配置、运行数据及备份。今后上传到家目录的临时文件在校验和发布后也应清理。

## 与本地版本的核对（2026-10-15）

对 `docker-compose.yml`、`deploy.sh`、`database/`、`ops/`、`sit_oj_backend/` 中除 Maven `target/` 外的部署文件逐项比较 SHA-256：远端 363 个文件与本地完全一致。本地另有 9 个 `.idea` 文件，未部署。前端 `index.html` 在宿主机和 Nginx 容器内哈希一致，Nginx 配置检查通过；12 个容器均运行，判题容器为 healthy。

| 服务 | 运行中的 `/app.jar` 与服务器 `target/*-exec.jar` |
| --- | --- |
| `oj-user`、`oj-problem` | 一致 |
| `oj-gateway`、`oj-submission`、`oj-competition`、`oj-judge-logic` | 不一致：运行镜像中的 JAR 不是服务器当前 `target/` 产物 |

本地没有可供逐项比较的已构建 JAR；源码一致不能证明旧运行镜像由当前源码构建。此次发布范围为前端和 `oj-user`，其余四个服务需在后续全服务发布时重建并复核。数据库现有表已迁移；`database/init.sql` 已与本地同步，但不得对现有数据库执行初始化脚本。
