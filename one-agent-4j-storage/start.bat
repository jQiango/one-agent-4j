@echo off
echo 启动 one-agent-4j-storage 模块...

REM 检查Java环境
java -version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到Java环境，请确保已安装JDK 17+
    pause
    exit /b 1
)

REM 检查Maven环境
mvn -version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到Maven环境，请确保已安装Maven 3.6+
    pause
    exit /b 1
)

echo 正在编译项目...
call mvn clean compile -DskipTests

if errorlevel 1 (
    echo 编译失败，请检查错误信息
    pause
    exit /b 1
)

echo 正在启动应用...
call mvn spring-boot:run -Dspring-boot.run.profiles=storage

pause 