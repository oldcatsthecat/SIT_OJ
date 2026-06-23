# SIT-OJ
## 部署

### 建议配置
运行内存4GB及以上
2核CPU

### 部署环境
Ubuntu20.04 LTS 64bit

### 安装依赖

```
sudo apt update && sudo apt-get install -y vim python3-pip curl git
pip3 install --upgrade pip
pip install --upgrade requests
sudo apt install -y openjdk-17-jdk
sudo apt install -y maven
```

### 安装docker

```
sudo curl -sSL https://get.daocloud.io/docker | sh
```
如果无法通过该方法安装docker，请查阅相关文档

### 安装docker compose 新版

```
# 1. 创建插件存放目录（如果不存在）
mkdir -p ~/.docker/cli-plugins/
# 2. 从官方 GitHub 下载最新版（以 v2.20.2 为例，建议根据官网查最新版本号）
curl -SL https://github.com/docker/compose/releases/download/v2.20.2/docker-compose-linux-x86_64 -o ~/.docker/cli-plugins/docker-compose
# 3. 给下载好的文件增加执行权限
chmod +x ~/.docker/cli-plugins/docker-compose
# 4. 将它同步到系统全局目录，这样 root 用户在任何位置都能识别
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo cp ~/.docker/cli-plugins/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
```
最后一步如果返回如果返回 Docker Compose version v2.x.x，说明安装成功。

### 开启swap

```
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
# 永久生效
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/hosts
```

### 安装部署

```
git clone https://github.com/oldcatsthecat/SIT_OJ.git
或者git clone https://gitee.com/xiang-jiating/sit_-oj.git
cd SIT_OJ
chmod +x deploy.sh
./deploy.sh
```

### 克隆并合并（防止克隆失效），适用于重新克隆

```
cd SIT_OJ
ls | grep -v "^data$" | xargs rm -rf
git clone https://github.com/oldcatsthecat/SIT_OJ.git ../SIT_OJ_TMP
或者git clone https://gitee.com/xiang-jiating/sit_-oj.git ../SIT_OJ_TMP
rsync -av --ignore-existing --exclude='.git' ../SIT_OJ_TMP/ ./
rm -rf ../SIT_OJ_TMP
```

# 注意docker compose部署只用这个命令，不要带横杠

```
docker compose up -d --build
```


## hint
```
暂时什么也没有，希望能做大做强--SIT算法竞赛社特供版，judgeServer用的是QingDaoOj的测评机后端，还没重写
```
