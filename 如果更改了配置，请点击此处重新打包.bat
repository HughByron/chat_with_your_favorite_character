@echo off
echo package start...
call mvn clean package
if %errorlevel% neq 0 (
    echo error：package failed!
    pause
    exit /b 1
)
pause
