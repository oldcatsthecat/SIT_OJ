# 请求审计、冒用提交排查与日志持久化

更新：2026-10-11。以下命令在 **Ubuntu 服务器上的 OJ 项目根目录**执行；本地 Windows 的项目路径并不是服务器路径。

## 1. 相同 auth、修改 userId 为什么不能再冒用

这里的 `auth` 指前端发送到 `Authorization` 请求头中的登录 JWT。

普通提交 `/api/submissions/submit` 现在先验证 JWT 的签名和有效期，从令牌读取用户 ID，再新建提交对象。请求体中只取题号、语言、代码等允许字段；用户 ID、提交时间由服务器决定，客户端提供的提交 ID、判题结果等不参与保存。

例如 auth 属于用户 77，请求体却写 `userId: 88`：如果这次提交通过题目权限、语言和频率检查，最终仍保存在用户 77 名下。**当前行为是忽略伪造的 userId，不是单独因为 ID 不一致就拒绝整个提交。**

比赛提交 `/api/competitions/{id}/submit` 同样从登录拦截器的已验证身份取用户 ID，并检查比赛报名、时间和题目归属。内部提交接口使用独立内部密钥，公网网关阻止直接访问。

以上属于服务端访问控制，不能通过修改浏览器请求绕过。新日志也在服务端生成，直接调用 API 的请求同样会留下记录，无需依赖前端主动上报。

**前提：所有服务必须使用未泄露的新 JWT 密钥。** 当前本地 `.env` 曾按要求恢复旧凭证供对照；上线前请按 [安全部署说明](SECURITY-DEPLOY.md)更换。若攻击者仍掌握正在使用的签名密钥，就能签发任意账号的有效令牌，日志中的“验签成功”也无法证明是真实用户登录。仅修复请求体中的 userId 信任问题不能替代密钥轮换。

## 2. 日志在哪里、哪些请求会记录

Compose 已将容器内 `/var/log/oj` 挂载到宿主机项目的 `data/logs/` 下：

| 宿主机相对路径 | 内容 |
| --- | --- |
| `data/logs/nginx/access.ndjson` | 到达 Nginx 的 HTTP 请求，包括页面、API、入口限流拒绝 |
| `data/logs/nginx/error.log` | Nginx 诊断日志 |
| `data/logs/gateway/audit.ndjson` | 到达网关的请求，包括内部接口拦截和未匹配路由 |
| `data/logs/user/audit.ndjson` | 用户服务请求，以及成功登录/刷新签发的令牌指纹与账号关联 |
| `data/logs/submission/audit.ndjson` | 提交服务请求、提交归属、成功保存的提交编号 |
| `data/logs/competition/audit.ndjson` | 比赛服务请求、实际账号与请求体中声称的账号 |
| `data/logs/problem/audit.ndjson` | 题目服务请求 |
| `data/logs/judge/audit.ndjson` | 判题逻辑服务请求 |
| 各 Java 服务目录的 `application.log` | 常规程序运行日志，与结构化审计记录分开 |

每个审计记录是一行 JSON。后端时间为 UTC（以 `Z` 结尾），北京时间需要加 8 小时；Nginx 时间自带时区偏移。

这些是宿主机文件，不依赖容器的 `docker logs`。普通重启、重建容器及 `docker compose down` 后仍保留；删除宿主机目录、超过轮转保留范围或更换项目挂载路径仍会影响历史日志。不要把 `data/logs/` 同步到公开仓库或静态网站。

未到达本机 Nginx 的 CDN/WAF 拦截、网络连接失败，不在这套 HTTP 请求日志范围内。Nginx 已拦截的请求不会产生下游记录，也没有已验证账号。

## 3. 关键字段如何理解

| 字段 | 含义 |
| --- | --- |
| `requestId` | Nginx 生成的请求编号，向网关、业务服务及内部 Feign 请求传递，并在响应头 `X-Request-ID` 返回 |
| `tokenFingerprint` | 当前 Authorization 令牌的完整 SHA-256 指纹，不是令牌本身 |
| `authStatus` | 业务服务为 `VALID`、`INVALID`、`EXPIRED` 或 `MISSING`；网关不验签，只有 `NOT_VERIFIED` 或 `MISSING` |
| `actorUserId` / `actorUsername` / `actorRole` | 业务服务从签名有效、未过期令牌读取的账号 ID、用户名、角色；无法验证时为空 |
| `claimedUserId` | 提交请求体声称的用户 ID，只用于排查，不能当作实际操作账号 |
| `userIdMismatch` | 可验证的账号与请求体用户 ID 是否不同；缺少可比较身份时为空 |
| `problemId` / `competitionId` / `language` | 提交尝试涉及的题目、比赛、语言 |
| `submissionId` / `submittedUserId` | 成功保存后的提交编号、实际归属用户 |
| `issuedTokenFingerprint` / `issuedUserId` / `issuedUsername` | 成功登录或刷新令牌时记录的签发关联 |
| `internalAuthenticated` | 是否通过独立的服务内部密钥认证，不代表某个用户已登录 |
| `httpStatus` / `businessCode` | HTTP 状态码与接口返回的业务码；部分旧接口 HTTP 200 仍可能代表业务失败，需要结合查看 |
| `clientIp` / `peerIp` / `userAgent` | 入口来源地址、业务服务直接连接端地址、浏览器/客户端标识 |

比赛内部转交的提交记录可能没有 `actorUserId`，但会有 `submittedUserId`。用相同 `requestId` 关联比赛入口的验签记录，可以还原提交来源。

用户名是签发令牌时的值，排查时以账号 ID 为主。不是每条日志都有全部字段，例如登录成功关联使用 `issuedUserId`，网关不填写已验证账号。

审计日志不主动记录完整 auth、密码、邮箱验证码、提交代码、完整请求体或 URL 查询字符串。客户端可控制路径和 User-Agent，因此这些字段依然是不可信输入，JSON 转义防止换行伪造记录；不应把它们当作可信指令。常规诊断日志不属于这份审计字段白名单。

`clientIp` 使用 Nginx 覆盖后的来源地址；当前未盲目信任客户端的转发 IP 头。若前面有 Cloudflare/CDN，这个地址可能是代理节点，需要结合 CDN 日志及正确的可信代理配置，不能直接认定为攻击者真实 IP。

## 4. 如何通过提交、auth 或账号查询

查询工具只依赖 Python 3 标准库，可读取当前文件、Logback 压缩归档和 Nginx logrotate 归档。默认最多返回 1000 条，可用 `--limit` 调整。输出按日志文件扫描顺序排列，分析时间线时请结合 `timestamp`。

优先从异常提交编号开始，例如 551（替换为实际编号）：

```bash
sudo python3 ops/find_audit.py --submission-id 551
```

结果会包含实际保存的账号和请求编号。比赛提交可继续按该请求编号查整个调用链：

```bash
sudo python3 ops/find_audit.py --request-id '替换为日志里的requestId'
```

查询某个账号的记录：

```bash
sudo python3 ops/find_audit.py --user-id 77
```

这里匹配实际验证/签发/保存的用户 ID，不会将 `claimedUserId` 中的受害者误当作请求发起者。

手里有完整 auth 时，执行下面的命令，再在隐藏输入提示中粘贴 auth，可带或不带 `Bearer `：

```bash
sudo python3 ops/find_audit.py --auth
```

工具在本机计算 SHA-256，匹配请求令牌指纹以及成功登录/刷新的签发指纹。它不会把原始令牌写入日志或输出。不要把 auth 直接写在命令参数里，也不要发送到外部 JWT 解码网站。

手里只有指纹时：

```bash
sudo python3 ops/find_audit.py --fingerprint '替换为64位指纹'
```

查看最新的提交审计：

```bash
sudo tail -n 30 data/logs/submission/audit.ndjson
sudo tail -n 30 data/logs/competition/audit.ndjson
```

判定一次冒用尝试时，查看 `authStatus=VALID`、`actorUserId`、`claimedUserId`、`userIdMismatch=true`，再结合返回状态与保存编号。`userIdMismatch` 本身是排查线索，也可能来自前端缓存错误，不应仅凭这一字段认定具体人恶意攻击。

指纹只能用于关联，不能反向恢复原始令牌。无法验证的令牌不会被记录成已认证账号；如果此前没有同指纹的已验证请求或签发记录，不能凭空查出账号。即便查到账号，也只能证明使用了该账号的令牌，令牌被盗或共享时不能据此确认现实中的操作者。

新日志从部署生效后开始，无法补出此前没有记录的 auth、账号或 IP。

## 5. 如何部署并确认持久化

将本次后端源码、`docker-compose.yml`、`ops/`、前端目录中的 `nginx.conf` 和 `oj-proxy.conf` 同步到服务器。日志功能不要求额外修改 Vue 请求。

**仍未完成旧凭证迁移：** 先按 [SECURITY-DEPLOY.md](SECURITY-DEPLOY.md)修改 `.env`，保留旧容器，再执行 `bash deploy.sh`。部署脚本会创建私有日志目录并应用挂载。不要为了加日志先 `down`，以免影响旧站密码迁移。

**已完成安全迁移、这次只更新日志功能：** 在原项目目录执行：

```bash
sudo python3 -c 'from ops.secure_deploy import prepare_log_directories; prepare_log_directories()'
mvn clean package -f sit_oj_backend/pom.xml
```

只有上面的构建成功后，再执行：

```bash
docker compose config --quiet
docker compose up -d --build oj-gateway oj-user oj-problem oj-submission oj-competition oj-judge-logic oj-frontend
docker compose exec -T oj-frontend nginx -t
```

修改挂载配置后需要重新创建容器，单独 `docker compose restart` 不会添加新挂载。发布时保留已关闭的公网入口，先本机验证：

```bash
curl -sS -o /dev/null -D - http://127.0.0.1/api/competitions/list
sudo tail -n 3 data/logs/nginx/access.ndjson
sudo tail -n 3 data/logs/gateway/audit.ndjson
sudo tail -n 3 data/logs/competition/audit.ndjson
docker inspect oj-submission --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
```

确认文件有新增记录、三层 `requestId` 一致，提交服务挂载包含 `data/logs/submission -> /var/log/oj`。再用自己的测试账号登录并正常提交一次，按新提交编号查询，确认能找到 `actorUserId` 或通过 `requestId` 关联到比赛入口身份。

Java 日志配置位于 `ops/logback-spring.xml`：每个服务的审计文件按天或约 20 MB 轮转、压缩，保留至多 90 天且归档总量上限 500 MB；常规程序日志保留至多 14 天、归档总量上限 200 MB。超过空间上限会提前清理旧归档，不保证保满天数。审计使用追加写入与即时刷新，容器重建不会截断当前文件；磁盘故障、磁盘满、宿主机失陷仍可能导致证据丢失，需要另行备份重要事件日志。

## 6. Nginx 文件轮转

Nginx 文件已持久化，但不会由 Java 的 Logback 轮转。服务器可用 `logrotate`；项目提供 `ops/nginx-logrotate.conf.example`：

```bash
sudo install -m 0644 ops/nginx-logrotate.conf.example /etc/logrotate.d/sit-oj
sudo nano /etc/logrotate.d/sit-oj
```

把模板中两处 `/ABSOLUTE/OJ/ROOT` 替换成服务器项目根目录的真实绝对路径（可先执行 `pwd -P` 查看），并确认 Docker 可执行文件是 `/usr/bin/docker`。然后只检查配置，不实际轮转：

```bash
sudo logrotate --debug /etc/logrotate.d/sit-oj
```

确认服务器已安装 logrotate，系统定时任务或 `logrotate.timer` 正常运行。模板按天及 100 MB 阈值检查，保留至多 90 份归档。空间阈值只有在定时任务执行时检查，并非即时硬上限；入口流量大时应缩短系统检查间隔并关注磁盘余量。这项系统配置需要在服务器安装，本次本地代码修改没有自动设置服务器定时任务。

模板重命名旧文件后向 Nginx 发 `USR1` 重新打开日志，避免使用复制再清空带来的日志窗口损失；行为见 [Nginx 官方日志轮转说明](https://nginx.org/en/docs/control.html#logs)。
