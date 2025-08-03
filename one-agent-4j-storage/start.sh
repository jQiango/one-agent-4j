#!/bin/bash

echo "启动 one-agent-4j-storage 模块..."

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请确保已安装JDK 17+"
    exit 1
fi

# 检查Maven环境
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到Maven环境，请确保已安装Maven 3.6+"
    exit 1
fi

# 检查Java版本
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "错误: Java版本过低，需要JDK 17+"
    exit 1
fi

echo "正在编译项目..."
mvn clean compile -DskipTests

if [ $? -ne 0 ]; then
    echo "编译失败，请检查错误信息"
    exit 1
fi

echo "正在启动应用..."
mvn spring-boot:run -Dspring-boot.run.profiles=storage 