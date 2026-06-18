#!/bin/bash

echo ">>>> [1/3] 进入后端目录并打包..."
cd sit_oj_backend
mvn clean package -DskipTests
cd ..

if [ $? -eq 0 ]; then
    echo ">>>> [2/3] 后端打包成功，启动 Docker 容器..."
    # 强制重新构建镜像以包含最新的 JAR 包
    docker compose up -d --build

    echo ">>>> [3/3] 清理虚悬镜像以释放磁盘空间..."
    docker image prune -f
    echo ">>>> 部署成功！"
else
    echo ">>>> [错误] Maven 打包失败，请检查后端代码。"
    exit 1
fi