# SIT-OJ 密钥修改、旧站迁移与部署说明

更新：2026-10-11。适用于本项目当前的 `docker-compose.yml`、`deploy.sh` 和 `ops/secure_deploy.py`。

请求审计与运行日志现已挂载到宿主机 `data/logs/`，部署包需包含 `ops/logback-spring.xml`、`sit_oj_backend/frontend/nginx.conf` 和 `oj-proxy.conf`。账号追踪、日志查询、升级验证与 Nginx 轮转设置见 [请求审计与持久化日志](AUDIT-LOGGING.md)。旧 JWT 密钥更换之前，不能把日志中的验签结果作为可靠的账号归因依据。

## 1. 能否直接 down，改完 .env 后再 up？

**不能用这个办法完成本次全部密码更换，但也不必逐项手动修改：准备好新的 `.env`，保留旧容器，执行 `bash deploy.sh`，让脚本完成现有账号的密码迁移。**

`docker compose down` 会移除容器和网络。项目的 MySQL、RabbitMQ、Redis 数据通过 `./data/...` 挂载在宿主机上，普通 `down` 不会把这些目录清空。因此重新创建容器时，服务仍会读取原有数据及账号密码。[Docker 官方说明](https://docs.docker.com/reference/cli/docker/compose/down/)

例如：数据库里 root 的密码原来是旧值，只把 `.env` 中的 `MYSQL_ROOT_PASSWORD` 改成新值，再重新创建 MySQL 容器，已有数据库中的 root 密码仍然是旧值。MySQL 镜像的初始化变量只在初始化空数据目录时起作用。[MySQL 镜像说明](https://hub.docker.com/_/mysql)

另外，`docker compose restart` 只是重启已有容器，**不会更新容器的环境变量**。修改 `.env` 后需要通过 `up` 重建受影响的容器；本次迁移由部署脚本统一处理。[Docker restart 说明](https://docs.docker.com/reference/cli/docker/compose/restart/)

不要通过删除 `data/`、删除数据库、执行 `down -v` 来“让新密码生效”。`down -v` 删除的是相应命名卷和匿名卷，不是修改密码的命令；清空数据库目录会危及账号、题目、比赛和提交记录。

## 2. 当前本地 .env 的状态

按要求，原来能够从项目配置中找回的 JWT、数据库、RabbitMQ、判题及邮箱凭证，已经写回本地 `.env`，供对照修改。历史远程测试数据同步账号和密码也作为参考写入。

**当前 `.env` 含有已公开过的旧值，不能原样作为本次安全部署的最终配置。**

刚生成的随机新密钥已完整备份到本机：

```text
.secrets/env.generated-before-restore-20261010-230654.env
```

可以从该文件逐项复制需要的新值到 `.env`，也可以自行生成。不要简单在旧密码末尾加数字或重复使用同一密码。

旧版本没有配置内部接口密钥、Redis 密码及本次新增的 Nacos 鉴权凭证，因此这些项目保留了新生成值。`DB_USERNAME` 保留 `oj_app`，不要改成 `root`；应用账号与数据库管理员账号应分开。`MAIL_ENABLED` 保持 `false`。

`.env`、`.secrets/` 和 `incident-backups/` 已被 Git 忽略。它们仍然是敏感文件，手工压缩或上传整个目录时也应留意；不要放到公开仓库或网站静态目录。通过 SSH/SFTP 私下传输最终的 `.env`，不要将其内容写进本说明。

## 3. 每个字段怎么改、怎么生效

下表说明的是**本项目当前配置**，并非所有软件部署方式的通用规则。

| `.env` 字段 | 用途 | 只修改配置并重建是否足够 | 本次处理方式 |
| --- | --- | --- | --- |
| `JWT_SECRET` | 用户登录令牌签名 | 是，所有使用它的服务都必须重建 | 脚本重建业务服务，旧签名令牌失效，用户重新登录 |
| `INTERNAL_API_KEY` | 微服务内部接口认证 | 是，调用方与接收方必须同步 | 脚本统一更新业务服务 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 管理员密码 | 否，已有数据库内的 root 账号必须改密 | 脚本更新所有现有 root 主机条目 |
| `DB_USERNAME` / `DB_PASSWORD` | OJ 连接数据库的账号 | 否，已有数据库需创建或更新账号 | 保持 `oj_app`；脚本设置密码及 `sit_oj` 的增删改查权限 |
| `RABBITMQ_PASSWORD` | 消息队列 `oj_admin` 账号 | 否，已有 RabbitMQ 用户必须改密 | 脚本调用 `rabbitmqctl change_password` |
| `REDIS_PASSWORD` | Redis 认证 | 是，本项目通过启动参数设置 | 脚本重建 Redis 及使用它的业务服务，保留数据目录 |
| `JUDGE_TOKEN` | 判题机与后端认证 | 是，两端必须同步 | 脚本重建判题机及判题逻辑服务 |
| `NACOS_PASSWORD` | Nacos 的 `nacos` 账号密码 | 否，现有 Nacos 用户记录必须改密 | 脚本登录并调用改密接口，同时更新各服务连接配置 |
| `NACOS_AUTH_TOKEN` | Nacos 服务端令牌签名 | 是，重建 Nacos | 它不是 `nacos` 账号的登录密码 |
| `NACOS_AUTH_IDENTITY_VALUE` | Nacos 服务身份凭证 | 是，重建 Nacos | 使用独立随机值 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | 发信邮箱及 SMTP 授权码 | 否，旧授权码必须在 QQ 邮箱撤销 | 用户申请新授权码，填写后再重建用户服务 |
| `OJ_TESTCASE_REMOTE_PASSWORD` | 历史远程同步 SSH 账号密码 | 否，改本地文件不会修改远程服务器账号 | 若账号仍有效，在对应服务器改密；远程同步当前保持关闭 |
| `RABBITMQ_HOSTNAME` | RabbitMQ 持久化数据对应的节点主机名 | 不是密码 | 脚本从旧容器保存，不要随意改动 |

RabbitMQ 的默认用户配置用于初始化，并不替代现有用户的改密操作。[RabbitMQ 配置说明](https://www.rabbitmq.com/docs/configure)

### 新值的格式

当前部署脚本要求以下 9 项均为**64 个小写十六进制字符，且彼此不同**：

```text
JWT_SECRET
INTERNAL_API_KEY
MYSQL_ROOT_PASSWORD
DB_PASSWORD
RABBITMQ_PASSWORD
REDIS_PASSWORD
JUDGE_TOKEN
NACOS_PASSWORD
NACOS_AUTH_IDENTITY_VALUE
```

这是当前项目部署脚本的校验规则，不代表 MySQL 等软件本身只能使用这种密码。每次执行下面命令会生成一个符合要求的新值；每个字段应单独生成一次：

```bash
python3 -c 'import secrets; print(secrets.token_hex(32))'
```

`NACOS_AUTH_TOKEN` 使用 Base64 编码的随机字节，可以这样生成：

```bash
python3 -c 'import base64, secrets; print(base64.b64encode(secrets.token_bytes(48)).decode())'
```

编辑 `.env` 时采用 `KEY=value` 格式，不加引号、不在值后加行尾注释；说明文字单独写在以 `#` 开头的行。脚本对 `.env` 的读取比完整 dotenv 语法更简单。

现有 `ops/generate-secrets.mjs` 遇到已经存在的 `.env` 会拒绝覆盖，所以现在不需要再次运行它。可以直接使用上面的新值备份或逐项生成。

## 4. 推荐操作：自动修改密码并部署

以下命令在 **Ubuntu 服务器的原项目根目录**执行，即能看到 `docker-compose.yml` 的目录。当前本机没有线上服务器连接，本文命令尚未在线上执行。

### 第一步：保持公网关闭，保留旧容器

继续用云安全组限制公网入口，保留自己的 SSH 连接。不要先运行 `docker compose down`。

检查原容器是否还存在：

```bash
docker ps -a --format 'table {{.Names}}\t{{.Status}}'
```

如果只是停止了容器，且下面三个容器都仍存在，可以启动原有基础服务：

```bash
docker start oj-mysql oj-rabbitmq oj-nacos
```

需要旧 MySQL 和 RabbitMQ 正常运行，脚本才能连接它们更新已有账号。容器已经被删除时，先看第 6 节。

### 第二步：上传修复文件，准备最终 .env

上传修改后的后端源码、`docker-compose.yml`、`deploy.sh`、`ops/`、前端配置文件和 `sit_oj_backend/frontend/dist`。保留服务器原有的 `data/`，不要用本机空目录覆盖它。

在服务器项目根目录编辑最终配置：

```bash
nano .env
chmod 600 .env
```

按第 3 节填写新密钥；使用 `DB_USERNAME=oj_app`。暂时不处理邮件时，保留 `MAIL_ENABLED=false`。

不要把当前供对照使用的旧值误当作新值。旧值通常不符合当前校验规则，脚本会停止；校验通过也不代表一个值从未泄露，仍须确保使用真正的新随机值。

### 第三步：确认环境并检查配置

服务器需要 Java 17、Maven、Docker Engine、Docker Compose v2 和 Python 3。Compose 必须支持 `up --wait`。脚本的 Python 部分只依赖标准库，不需要安装本地测试用的 fakeredis。

```bash
java -version
mvn -version
docker compose version
python3 --version
```

下面的检查只验证 `.env` 内容，不改线上密码，也不打印密钥：

```bash
python3 -c 'from ops.secure_deploy import read_env; read_env(); print("配置格式检查通过")'
```

不要将完整 `docker inspect` 或不带 `--quiet` 的 `docker compose config` 输出发到公开场合，它们可能包含环境变量里的密钥。

### 第四步：执行一次迁移部署

```bash
bash deploy.sh
```

**正常情况下，你不需要逐个进入 MySQL、RabbitMQ、Nacos 手动改密码。**脚本按以下顺序处理：

1. 检查新密钥，保存 RabbitMQ 原容器的主机名到 `.env`，验证 Compose 配置。
2. 编译后端并运行测试；失败时不会继续部署旧 JAR。
3. 尝试用旧 MySQL 容器中的凭证连接；无法连接时，在终端提示输入当前 root 密码，输入不会回显。
4. 将现有 OJ 容器日志保存到私有 `incident-backups/<UTC时间>/`，暂停业务容器，再备份 `sit_oj` 数据库。数据库备份失败会停止改密。
5. 为 `oj_app` 设置 `DB_PASSWORD`，设置应用权限；为所有已存在的 root 主机条目设置 `MYSQL_ROOT_PASSWORD`；修改 RabbitMQ 的 `oj_admin` 密码。
6. 迁移原 Nacos 数据目录（若目标目录不存在），用新配置启动基础服务。用新密码、默认旧密码或手工提供的当前密码登录 Nacos，更新并验证 `nacos` 账号密码。
7. 重建并启动业务容器，使所有服务使用相同的最新配置。

脚本不会修改云安全组，也不会替你申请 QQ 邮箱授权码、修改远程 SSH 账号或 OJ 用户的登录密码。基础服务账号改密与 OJ 网站用户改密是不同操作。

如果提示 `Current MySQL root password`，填**数据库现在实际使用的密码**；如果提示 `Current Nacos password`，填当前 Nacos 登录密码，不是正在准备的新密码。

### 第五步：检查运行状态

```bash
docker compose ps
docker compose logs --tail=100 oj-user oj-problem oj-submission oj-competition oj-judge-logic oj-gateway
```

检查是否有数据库拒绝连接、Redis 认证失败、Nacos 登录失败、内部接口 403 等错误。业务容器显示 Running 还不等于所有业务功能正常，继续完成第 7 节验证。

## 5. 如果要手动改密码，具体怎么做？

**这一节用于理解原理或排障，不需要在自动迁移后再重复执行。**第一次迁移仍推荐第 4 节，因为脚本会同步处理备份、权限、主机条目和服务配置。

### 5.1 手动操作的准备

先准备好最终 `.env`，保持公网关闭。停业务容器，保留数据库、RabbitMQ、Redis、Nacos：

```bash
docker stop oj-frontend oj-gateway oj-user oj-problem oj-submission oj-competition oj-judge-logic oj-judge-server
```

改密前保存容器日志和数据库备份。如果没有运行过自动备份，可在项目根目录创建一个新的私有备份目录：

```bash
umask 077
oj_backup_dir="incident-backups/manual-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$oj_backup_dir"
for name in oj-frontend oj-gateway oj-user oj-problem oj-submission oj-competition oj-judge-logic oj-judge-server oj-mysql oj-rabbitmq oj-nacos; do
  docker logs --timestamps "$name" > "$oj_backup_dir/$name.log" 2>&1
done
docker exec -it oj-mysql sh -c 'umask 077; mysqldump -uroot -p --single-transaction --routines --triggers --databases sit_oj > /tmp/oj-before-credential-change.sql'
```

输入当前 MySQL root 密码，确认导出成功，再复制到宿主机：

```bash
docker cp oj-mysql:/tmp/oj-before-credential-change.sql "$oj_backup_dir/sit_oj.sql"
test -s "$oj_backup_dir/sit_oj.sql"
```

导出报错或检查失败时，先解决备份问题，不要继续改密。以上是业务数据库备份，不是整个服务器或所有服务的完整灾备副本。

还需保存原 RabbitMQ 主机名：

```bash
docker inspect --format '{{.Config.Hostname}}' oj-rabbitmq
```

将输出原样填写到 `.env` 的 `RABBITMQ_HOSTNAME=...`。重建时要继续使用这个名称，否则可能读到另一个节点的数据目录。

如果旧 Nacos 未挂载数据目录，且项目内 `data/nacos` 确实还不存在，先停 Nacos 再复制原数据：

```bash
docker stop oj-nacos
docker cp oj-nacos:/home/nacos/data ./data/nacos
docker start oj-nacos
```

**如果 `data/nacos` 已存在，不要执行上面的复制命令覆盖或套嵌目录**，先核对现有挂载和数据来源。

### 5.2 MySQL：创建应用账号并修改 root 密码

进入 MySQL，输入当前 root 密码：

```bash
docker exec -e MYSQL_HISTFILE=/dev/null -it oj-mysql mysql -uroot -p
```

先查看账号主机条目：

```sql
SELECT User, Host FROM mysql.user WHERE User IN ('root', 'oj_app');
```

将下面中文占位符换成最终 `.env` 里的真实值，再执行。不要把占位符本身当成密码：

```sql
CREATE USER IF NOT EXISTS 'oj_app'@'%' IDENTIFIED BY '替换为DB_PASSWORD的值';
ALTER USER 'oj_app'@'%' IDENTIFIED BY '替换为DB_PASSWORD的值';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'oj_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sit_oj.* TO 'oj_app'@'%';
ALTER USER 'root'@'localhost' IDENTIFIED BY '替换为MYSQL_ROOT_PASSWORD的值';
```

root 的主机条目必须以上面查询结果为准。如果查询结果中还有 `root@%`，在当前会话中继续执行：

```sql
ALTER USER 'root'@'%' IDENTIFIED BY '替换为MYSQL_ROOT_PASSWORD的值';
```

如果存在其他 root 主机条目，同样逐项修改，不要只改 localhost 而留下其他条目的旧密码。这里使用 `ALTER USER`，无需通过直接修改系统表或额外 `FLUSH PRIVILEGES` 生效。[MySQL 账号修改生效规则](https://dev.mysql.com/doc/refman/8.0/en/privilege-changes.html)

退出后使用新 root 密码重新登录验证：

```bash
docker exec -it oj-mysql mysql -uroot -p -e 'SELECT 1;'
```

同时保持 `.env` 中 `DB_USERNAME=oj_app`，`DB_PASSWORD` 与应用账号实际密码一致，`MYSQL_ROOT_PASSWORD` 与 root 实际密码一致。两种密码不应相同。

### 5.3 RabbitMQ：修改 oj_admin

本项目使用 RabbitMQ 3.12，可执行下面命令，然后在提示中输入最终 `.env` 的 `RABBITMQ_PASSWORD`：

```bash
docker exec -it oj-rabbitmq rabbitmqctl change_password oj_admin
```

该命令直接修改已有用户记录；无需知道用户旧密码，但需要你有容器管理权限。不要把新密码直接写进命令参数或公开截图。[RabbitMQ 3.12 命令实现](https://github.com/rabbitmq/rabbitmq-server/blob/v3.12.14/deps/rabbitmq_cli/lib/rabbitmq/cli/ctl/commands/change_password_command.ex)

若提示用户不存在，先检查当前节点及数据目录，避免在错误的新节点中随意创建账号。保持原 `RABBITMQ_HOSTNAME`。

### 5.4 Nacos：账号密码与签名密钥分开修改

`NACOS_PASSWORD` 是 `nacos` 账号密码；`NACOS_AUTH_TOKEN` 和 `NACOS_AUTH_IDENTITY_VALUE` 是 Nacos 服务配置。仅修改后两者，不会自动替换已有账号密码。[Nacos 鉴权说明](https://nacos.io/en/docs/v2/guide/user/auth/)

在已完成 MySQL、RabbitMQ 改密，保存 RabbitMQ 主机名及 Nacos 数据后，启动新配置的基础服务：

```bash
docker compose up -d oj-mysql oj-redis oj-rabbitmq oj-nacos
```

手动修改 Nacos 密码可使用控制台。由于 8848 仅绑定服务器本地地址，在你自己的电脑上建立 SSH 隧道（替换用户名和服务器地址）：

```bash
ssh -N -L 18848:127.0.0.1:8848 SSH用户名@实际服务器地址
```

打开 `http://127.0.0.1:18848/nacos/`，使用当前有效的 Nacos 管理员账号登录，在用户管理中将 `nacos` 的密码改成 `.env` 中的 `NACOS_PASSWORD`，再用新密码重新登录确认。不要把 8848 对公网开放。

如果当前管理员密码不是已知值，不要通过清空 `data/nacos` 来重置；先按现有账号和数据情况恢复访问。推荐脚本会提示输入当前密码。

### 5.5 QQ 邮箱：先在 QQ 撤销旧授权码，再填配置

在 QQ 邮箱「设置 → 账号与安全 → 设备管理 → 授权码管理」撤销旧授权码，重新申请 SMTP 授权码。`MAIL_PASSWORD` 填的是新授权码，**不是 QQ 登录密码，也不是随意生成的随机字符串**。[QQ 邮箱说明](https://help.mail.qq.com/detail/106/985)

然后在 `.env` 设置：

```dotenv
MAIL_ENABLED=true
MAIL_USERNAME=你的实际发信邮箱
MAIL_PASSWORD=QQ邮箱新生成的授权码
```

暂时不恢复发信，保留 `MAIL_ENABLED=false` 即可；已有账号仍可登录，但需要发验证码的注册、找回密码等流程暂时不可用。

### 5.6 SSH 密码和 OJ 用户密码

修改 `.env` 的历史 `OJ_TESTCASE_REMOTE_PASSWORD` 不会修改远程 SSH 账号。确认对应账号仍有效且归你管理后，登录对应服务器，通过 `passwd` 修改该账号密码；密钥登录则需要替换对应授权公钥。当前远程同步保持关闭。

MySQL root、RabbitMQ 的 `oj_admin`、Nacos 的 `nacos`、OJ 网站管理员账号是不同的账号。修改基础服务密码不会修改 OJ 用户密码。网站管理员密码通过 OJ 的账号设置或受控的管理员改密入口更新，并核对是否存在异常新增的管理员账号。不要直接把明文写入 users 表的 password 字段。

### 5.7 手动改密后的重建

在基础服务实际密码与最终 `.env` 完全一致后，编译成功再重建业务服务：

```bash
mvn clean package -f sit_oj_backend/pom.xml
```

确认上一条命令成功，然后执行：

```bash
docker compose up -d --build
```

这一步会根据当前配置重建需要更新的容器，不需要先执行 `down`。如果只是在完成迁移后单独更新 QQ 授权码，可用 `docker compose up -d --force-recreate oj-user` 更新用户服务配置。

## 6. 如果已经执行过 docker compose down

先区分“容器停止”与“容器删除”：

```bash
docker ps -a --format 'table {{.Names}}\t{{.Status}}'
ls -ld data/mysql data/rabbitmq data/nacos
```

容器还在、状态为 Exited 时，可以用 `docker start` 启动原容器，然后按第 4 节迁移。不要用 `up` 提前替换仍有用的旧容器信息。

容器已经被删除时，普通 `down` 通常仍保留本项目的宿主机数据目录，但容器中的日志、原环境变量以及没有挂载的目录未必能够恢复。现有部署脚本发现旧 MySQL 数据目录存在却找不到旧容器，会停止，避免误当成全新安装。

恢复顺序如下：

1. 确认在**原项目目录**，检查 `data/mysql/mysql` 是否存在。保留现有目录及备份；目录缺失或路径不确定时不要启动空数据库代替旧数据库。
2. 确认旧 MySQL 数据完整、版本仍匹配本项目 MySQL 8.0 后，可仅执行 `docker compose up -d oj-mysql`，重新挂载原数据。此时 `.env` 即使写了新密码，数据库实际仍使用原密码；不要把容器中的环境变量误当作改密成功的证据。
3. RabbitMQ 必须先确定原主机名，再重建。优先使用此前保存的 `RABBITMQ_HOSTNAME`。若没有保存，可只读检查 `data/rabbitmq/mnesia/` 下的节点目录和已有备份：常见目录名为 `rabbit@原主机名`。有多个候选、无法确定或存在自定义节点名时，不要猜测；先核实实际使用的节点数据。不要删除目录、重置队列来绕过。
4. 将确认的原主机名填入 `.env`，再执行 `docker compose up -d oj-rabbitmq`，检查原账号和队列是否仍在。
5. 检查 `data/nacos`。如果旧 Nacos 没有挂载持久化目录、也没有备份，其旧配置和用户记录可能已随容器删除而丢失；这与 MySQL 业务数据库是两回事，需按实际使用情况恢复。不要声称旧 Nacos 数据已经保留。
6. 基础服务恢复后，执行 `bash deploy.sh`。如果 MySQL 提示输入当前密码，输入旧数据库实际密码；脚本随后才将其更新成 `.env` 的新值。

由于现有脚本不能自动判断删除容器后遗留的多个 RabbitMQ 节点目录，保留旧容器并直接迁移会简单得多。

## 7. 部署后验证及失败处理

恢复公网前确认：

- 基础服务正常，业务日志没有数据库、Redis、Nacos、内部接口认证错误。
- 使用正常账号重新登录，旧 JWT 不再可用；所有业务服务均已更新，未遗留使用旧签名密钥的副本。
- 退出登录和普通账号都无法直接读取隐藏题；未开赛或未报名访问比赛时不返回题面、标题、SPJ 源码，公开排行榜仍可访问。
- 已报名且比赛开始的选手可以读题并提交；不能替其他人提交，不能提交不属于该比赛的题。
- 普通账号不能操作题目、比赛、判题后台；公网无法访问内部接口或直接判题接口。
- C++、Python、Java 正常判题；原有时间/空间限制、封榜和 Resolver 奖项导出规则正常。
- 启用新邮箱授权码后，仅用自己能收信的邮箱验证一次；验证码错误最多尝试五次，成功使用后不可重复使用。
- 核查网站管理员角色、题面、SPJ、测试数据和比赛设置是否被改动。题号 22–33 按已泄露处理，修复不能撤回已被读取的内容。

部署后，Compose 只发布前端 80 到所有网卡；8001、3307、8848、9848、15672 仅绑定服务器回环地址。外部 HTTPS 或反向代理仍以你服务器的实际配置为准。

迁移失败时保持公网关闭，保留最终 `.env`、备份和现有容器。不要直接把 `.env` 改回旧值后重启：脚本可能已完成部分服务改密，回填旧值会造成连接不一致。先定位错误阶段，确认各服务当前有效密码，再使用相同的目标 `.env` 重新运行脚本；它必要时会提示输入当前 MySQL/Nacos 密码。

本地已完成代码和测试验证，但没有线上 Docker/SSH 会话，真实容器启动、实际密码迁移、邮件投递及业务联调仍需在服务器验证。

## 8. 以后普通更新怎么部署？

完成本次密码迁移后，如果以后只是更新业务代码且没有更换持久化服务账号密码，可编译成功后执行 `docker compose up -d --build`，无需每次先 `down`。只改 `.env` 也需要重建相应容器，不能只用 `restart`。

以后再次更换 MySQL、RabbitMQ 或 Nacos 账号密码时，仍需实际更新服务账号，可继续用 `bash deploy.sh` 协调迁移。QQ 授权码、SSH 凭证仍需在对应系统中更换。

删除公开文件或清理 Git 历史不能撤销已经泄露的凭证；必须先让旧值在对应服务中失效。
