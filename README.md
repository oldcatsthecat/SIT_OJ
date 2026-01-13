# SIT-OJ
## 部署
建议配置
运行内存4GB及以上
安装依赖
```
sudo apt update && sudo apt-get install -y vim python3-pip curl git
pip3 install --upgrade pip
pip install --upgrade requests
sudo apt install -y openjdk-17-jdk
sudo apt install -y maven
```

安装docker
```
sudo curl -sSL https://get.daocloud.io/docker | sh
```
如果无法安装docker，请查阅相关文档

安装docker compose 新版
```
# 1. 给下载好的文件增加执行权限
chmod +x ~/.docker/cli-plugins/docker-compose

# 2. 将它同步到系统全局目录，这样 root 用户在任何位置都能识别
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo cp ~/.docker/cli-plugins/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose

# 3. 验证版本（必须显示 v2.x.x）
docker compose version
```

开启swap
```
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
# 永久生效
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/hosts
```

安装部署
```
git clone https://github.com/oldcatsthecat/SIT_OJ.git
cd SIT_OJ
chmod +x deploy.sh
./deploy.sh
```

克隆并合并（防止克隆失效）
```
cd SIT_OJ
ls | grep -v "^data$" | xargs rm -rf
git clone https://github.com/oldcatsthecat/SIT_OJ.git ../SIT_OJ_TMP
rsync -av --ignore-existing --exclude='.git' ../SIT_OJ_TMP/ ./
rm -rf ../SIT_OJ_TMP
```

# 以后只用这个命令，不要带横杠
docker compose up -d --build
```
## hint
暂时什么也没有，希望能做大做强--SIT算法竞赛社特供版，judgeServer用的是QingDaoOj的测评机后端，还没重写

