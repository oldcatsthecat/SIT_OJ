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

上次发布目标是 `sit_oj_backend/frontend/dist` 的前端文件挂载和 `oj-user` 服务。现有 Compose 文件将 `./sit_oj_backend/frontend/dist` 只读挂载到 `/usr/share/nginx/html`。数据库升级按仓库根目录的 [DATABASE-MIGRATION.md](../../DATABASE-MIGRATION.md) 执行：核对 `users.locked` 是否存在，备份现有 `sit_oj` 数据库，执行一次 `database/migrations/20261015_add_users_locked.sql`，验证计数后再发布 user 服务和前端文件。

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

本地没有可供逐项比较的已构建 JAR；源码一致不能证明旧运行镜像由当前源码构建。上次发布范围为前端和 `oj-user`，其余四个服务需在后续全服务发布时重建并复核。数据库现有表已迁移；`database/init.sql` 已与本地同步，但不得对现有数据库执行初始化脚本。


## 2026-10-15 判题可靠性发布

GitHub PR #4 的代码已部署到服务器。迁移前 `judge_outbox` 不存在，786 条提交中待判题记录为 0。执行 `database/migrations/20261015_add_judge_outbox.sql` 前的完整数据库备份保存在 `incident-backups/sit_oj-before-judge-outbox-20261015-194001.sql`（3,212,048 字节）；更新前代码备份保存在 `incident-backups/code-before-judge-reliability-20261015-194413.tar.gz`。迁移后 outbox 行数、未发布行数和待判题提交数均为 0。

已更新前端挂载文件，重建并重启 `oj-user`、`oj-problem`、`oj-submission`、`oj-competition`、`oj-judge-logic` 和 `oj-judge-server`，同时重启网关与前端。服务器 Docker Hub 代理返回 502，Java 服务使用原有应用镜像作为本地基础镜像，替换本机 Maven 构建并通过测试的 JAR；旧镜像保留了 `pre-20261015-judge-reliability` 标签。上线后五个 Java 容器中的 `/app.jar` 与宿主机 `target/*-exec.jar` 哈希一致，判题服务器健康检查通过，Nginx 配置检查通过，首页和题目列表接口返回 200；12 个容器均运行。RabbitMQ 的新 `judge.request.queue.v2` 队列为空，既有死信队列保留 80 条消息，未在本次部署中清理。

## 2026-10-15 Pending 提交修复

提交 1014–1017 的请求已经发布到 `judge.request.queue.v2`，但判题逻辑的两个消费者卡在读取判题机 `/judge` 响应，RabbitMQ 30 分钟确认超时后消费者数降为 0。判题机使用多线程 Gunicorn，而原补丁通过默认 `fork` 创建判题子进程；此次将子进程池改为 `spawn`，并为 Java 到判题机的 HTTP 请求设置 5 秒连接超时、10 分钟读取超时。

修复前代码备份为 `incident-backups/code-before-pending-fix-20261015-221655.tar.gz`。旧镜像分别保留为 `sit_oj-judge-server:pre-pending-fix-20261015` 与 `sit_oj-oj-judge-logic:pre-pending-fix-20261015`。更新了判题机与判题逻辑镜像并重启这两个容器；判题机子进程烟测通过，运行中的判题逻辑 `/app.jar` 与本地构建产物 SHA-256 一致。

重启交接期间 1014、1015 曾因连接中断被写为 `SE`，随后仅将这两条连接错误恢复为 `Pending` 并重置对应 outbox 发布标记重新判题。复核结果：1014–1016 为 `TLE`，1017 为 `AC`；后续新提交 1018 为 `WA`。当前待判题提交数与 v2 队列待消费数均为 0，v2 队列消费者数为 2。旧死信队列 80 条未处理。

## 2026-10-16 题目点击延迟修复

仅更新了前端只读挂载目录 `sit_oj_backend/frontend/dist`，没有重建或重启后端容器。发布前的前端文件备份为 `incident-backups/frontend-before-click-fix-20261016.tar.gz`。详情路由把 Monaco 编辑器拆为异步资源后，冷启动浏览器点击第一道题，在 5 秒检查窗口内进入 `/problem/1` 并发出详情接口请求；编辑器随后完成初始化。Nginx 配置检查通过。旧哈希资源暂留服务器，以免已打开旧版页面的客户端在切换期间加载失败。

## 2026-10-16 简易交题编辑器发布

仅更新了前端挂载目录，没有改动数据库或重启服务。更新前前端备份为 `incident-backups/frontend-before-editor-fallback-20261016.tar.gz`，权限为 `600`。高级编辑器加载时右侧显示转圈；资源失败时自动切换到可交题的简易输入框；加载超过 30 秒也先显示简易版，若随后加载成功仍可切回高级版。线上浏览器检查验证了正常编辑器就绪、暂停加载时的转圈提示、阻断资源后的简易版和可用提交按钮。Nginx 配置检查通过；旧哈希资源暂留供已打开页面的客户端使用。
