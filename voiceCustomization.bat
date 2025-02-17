@echo off
cd /d "target\" || exit
if not exist voiceCustomization-1.0-SNAPSHOT.jar (
    echo 错误：未找到jar文件！
    pause
    exit /b 1
)
java -jar voiceCustomization-1.0-SNAPSHOT.jar
if %errorlevel% neq 0 (
    echo 程序异常退出，代码：%errorlevel%
)