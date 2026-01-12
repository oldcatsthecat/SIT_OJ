FROM eclipse-temurin:17-jre-alpine

LABEL authors="XJT"
LABEL description="SIT-OJ-problem"
LABEL version="1.0"

RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 声明临时卷
VOLUME /tmp

ARG JAR_FILE=target/*.jar

COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]