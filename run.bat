@echo off
echo =====================================
echo   Slay the Spire 后端启动脚本 (Windows)
echo   要求: 已安装 Maven (mvn 命令可用)
echo =====================================
echo.
cd backend
echo [1/2] 编译并启动后端...
echo.
call mvn spring-boot:run
if %errorlevel% neq 0 (
    echo 错误：启动失败，请检查 Maven 和 Java 环境。
    pause
    exit /b 1
)
