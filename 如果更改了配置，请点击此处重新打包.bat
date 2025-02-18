@echo off
echo 正在打包项目...
call mvn clean package
if %errorlevel% neq 0 (
    echo 错误：项目打包失败！
    pause
    exit /b 1
)