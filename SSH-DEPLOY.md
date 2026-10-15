# Windows 通过 SSH 上传 OJ 到 Ubuntu

适用于本机 `D:\SIT_OJ`，不需要将代码推送到 GitHub。SSH 密钥负责服务器登录，与 OJ 的 JWT 密钥、数据库密码相互独立。

## 1. 公钥放服务器，私钥放自己的电脑

- **私钥**保存在 Windows 本机，例如 `C:\Users\92484\.ssh\oj-server.pem`。实际名称可以不同，也可以没有扩展名；后续 `-i` 指向此文件。
- **公钥**需要授权给服务器上的具体登录用户。例如用 `ubuntu` 登录，默认应在 `/home/ubuntu/.ssh/authorized_keys`；用 `root` 则是 `/root/.ssh/authorized_keys`。
- 云控制台生成密钥后，还需确认它已绑定/安装到这台实例及对应用户。仅生成密钥文件不代表服务器已授权。
- 如果你是在服务器终端里生成的用户密钥对，先通过已有的云控制台文件下载能力或可信现有连接，把私钥保存到本机。不需要再将私钥作为项目文件传回服务器。不要使用 `/etc/ssh/ssh_host_*` 作为用户登录密钥。

如果尚未安装公钥，可以通过现有云控制台终端，切换到准备登录的那个用户后执行：

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
touch ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
nano ~/.ssh/authorized_keys
```

在末尾**新增一行完整公钥**（常见以 `ssh-ed25519` 或 `ssh-rsa` 开头），保留原有行，不要粘贴私钥。在 root 终端直接执行这些命令只会配置 root；如需配置 ubuntu，可先执行 `sudo -iu ubuntu`。

服务器安全组需允许你的电脑连接 SSH 端口（通常 22）；不需要为上传开放 OJ 的网页端口。保持现有云控制台连接，等新 SSH 登录测试成功后再关闭。

## 2. 在 Windows PowerShell 验证连接

以下变量都在 **Windows PowerShell** 中设置。请替换私钥路径、服务器实际公网 IP、登录用户名和端口；此处 `ubuntu`、22 是示例。

```powershell
$ojKey = 'C:\Users\92484\.ssh\oj-server.pem'
$ojServer = '替换为服务器实际公网IP'
$ojLogin = 'ubuntu'
$ojPort = 22
$ojDestination = "${ojLogin}@${ojServer}"

ssh -i "$ojKey" -p $ojPort $ojDestination
```

首次连接会显示服务器指纹。可以通过云控制台运行 `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`，核对对应 ED25519 指纹再接受；如果连接展示其他算法，应核对对应的主机公钥。这里显示的是服务器主机指纹，与用于登录的用户公钥不同。

看到 Ubuntu 命令提示符即表示登录成功。执行 `exit` 返回 Windows，之后再上传。

若私钥设置了口令，`Enter passphrase for key` 要求的是私钥口令，不是 OJ 密码。

## 3. 上传已整理的更新包

本次准备的文件：

```text
D:\SIT_OJ\.secrets\uploads\SIT_OJ-ssh-update-20261011.tar.gz
D:\SIT_OJ\.secrets\uploads\SIT_OJ-ssh-update-20261011.tar.gz.sha256
```

包内包含后端源码、构建配置、运维脚本、前端 `dist`、Nginx 配置及部署文档。它**不包含 `.env`、`.secrets/`、SSH 私钥、`data/`、Git 历史、Maven 编译目录或前端依赖目录**。无需上传整个 `.secrets` 目录，只传这两个指定文件。

它是本次代码状态的快照；以后再修改代码，需要重新整理更新包。后端在服务器构建，所以服务器需具备 Java 17、Maven、Docker Compose 和 Python 3；已有编译好的 Vue 文件，无需在服务器安装 Node.js 来构建前端。

在刚才的 **Windows PowerShell** 窗口执行：

```powershell
$ojBundle = 'D:\SIT_OJ\.secrets\uploads\SIT_OJ-ssh-update-20261011.tar.gz'
scp -i "$ojKey" -P $ojPort "$ojBundle" "${ojBundle}.sha256" "${ojDestination}:"
```

目标末尾的冒号表示上传到登录用户的家目录：ubuntu 通常是 `/home/ubuntu/`，root 通常是 `/root/`。`ssh` 指定端口使用小写 `-p`，`scp` 使用大写 `-P`。`-i` 都指定私钥。[OpenSSH scp 官方说明](https://man.openbsd.org/scp)

## 4. 在服务器检查并解压到原项目目录

重新登录：

```powershell
ssh -i "$ojKey" -p $ojPort $ojDestination
```

下面全部是 **Ubuntu 服务器命令**。先验证文件传输完整性：

```bash
cd ~
sha256sum -c SIT_OJ-ssh-update-20261011.tar.gz.sha256
```

必须显示 `OK` 才继续；不匹配应重新上传。

如果不确定原项目目录，可以查看现有容器的 Compose 目录标签：

```bash
docker inspect oj-mysql --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}'
```

无 Docker 权限时为该命令加 `sudo`。如果标签为空或目录已迁移，手动找到**现有 `docker-compose.yml` 和原 `data/` 所在的目录**；不要新建空目录后当成旧站目录部署。

下面 `/home/ubuntu/SIT_OJ` 仅为示例，必须替换成刚确认的实际目录：

```bash
oj_project='/home/ubuntu/SIT_OJ'
cd -- "$oj_project"
pwd
ls -ld docker-compose.yml data
```

确认无误后备份当前代码和配置。该备份排除体积较大的运行数据；数据库备份由安全部署脚本单独处理：

```bash
sudo install -d -m 700 /var/backups/sit-oj
oj_backup="/var/backups/sit-oj/code-before-upload-$(date +%Y%m%d-%H%M%S).tar.gz"
sudo tar --exclude='./data' --exclude='./.git' --exclude='./.secrets' --exclude='*/target' --exclude='*/node_modules' --exclude='*/.idea' -czf "$oj_backup" .
sudo chmod 600 "$oj_backup"
```

备份成功后，将新包解压到当前目录：

```bash
sudo tar --no-same-owner --no-same-permissions -xzf "$HOME/SIT_OJ-ssh-update-20261011.tar.gz" -C "$oj_project"
```

该包直接以 `docker-compose.yml`、`sit_oj_backend/` 等路径开头，不会再多套一层 `SIT_OJ`。解压覆盖同名代码文件，保留服务器原有 `.env` 和 `data/`，不会删除旧文件。

## 5. 沿用换好的 .env，再部署

**如果服务器已保存你更换后的 `.env`，无需再次上传或生成密钥。** 不要用本地旧对照文件覆盖它。

如果你只在 Windows 修改了 `.env`、服务器还没有新配置，先确认本地确实是最终新值，再从 Windows 单独上传到临时私有目录，不直接覆盖运行配置：

```powershell
ssh -i "$ojKey" -p $ojPort $ojDestination 'umask 077; mkdir -p .oj-upload; chmod 700 .oj-upload'
scp -i "$ojKey" -P $ojPort 'D:\SIT_OJ\.env' "${ojDestination}:.oj-upload/env.new"
```

回到服务器确认这个临时文件是准备使用的新配置后，备份旧文件并安装新文件：

```bash
cd -- "$oj_project"
sudo cp -p .env ".env.before-upload-$(date +%Y%m%d-%H%M%S)"
sudo install -m 600 "$HOME/.oj-upload/env.new" .env
```

这只是传输配置，**不等于已有 MySQL/RabbitMQ/Nacos 账号已实际改密**；具体迁移由 [SECURITY-DEPLOY.md](SECURITY-DEPLOY.md)说明的流程处理。

上传完成后，保持原容器，不要先 `docker compose down`。在原项目目录执行部署：

```bash
cd -- "$oj_project"
sudo bash deploy.sh
```

使用已具备对应权限的 root 终端时，也可以直接 `bash deploy.sh`。部署脚本会编译后端、运行测试、处理现有账号配置及重建容器。执行前确认 Java 17、Maven、Docker Compose、Python 3 在这个执行用户的环境中可用。

完成后检查：

```bash
sudo docker compose ps
sudo docker compose exec -T oj-frontend nginx -t
sudo docker compose logs --tail=50 oj-gateway oj-user oj-submission oj-competition
```

按 [请求审计说明](AUDIT-LOGGING.md)验证日志、登录与提交。上传、解压本身不会使运行中的后端自动更新；只有构建并重建容器后才生效。

## 6. 常见连接问题

| 提示 | 检查内容 |
| --- | --- |
| `Permission denied (publickey)` | 登录用户名是否正确，公钥是否安装在这个用户下，私钥是否与公钥配对 |
| `Connection timed out` | 实际公网 IP、SSH 端口、安全组和服务器防火墙；与 OJ 管理员权限无关 |
| `Connection refused` | SSH 服务是否启动、端口是否正确 |
| 私钥 `UNPROTECTED PRIVATE KEY FILE` / `bad permissions` | 在 Windows 私钥文件属性的“安全→高级”中关闭继承，移除其他普通用户/Everyone 的读取权限，保留当前用户及必要的 SYSTEM/Administrators 权限 |
| `Load key ... invalid format` | `-i` 是否误选公钥、私钥复制是否完整；`.ppk` 需通过 PuTTYgen 导出为 OpenSSH 私钥后供 Windows OpenSSH 使用 |
| 上传目录 `Permission denied` | 先上传到自己的家目录，再在服务器用 `sudo` 解压到项目目录 |

SSH 私钥不需要发给他人，也不应放入项目更新包。有关 Windows SSH 密钥使用方式可参考 [Microsoft 官方说明](https://learn.microsoft.com/en-us/windows-server/administration/openssh/openssh_keymanagement)。
