@echo off
REM 构建并运行脚本
REM 用法: build-and-run.bat [batch|realtime] [algorithms] [randomSeed] [runs]

chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo ========================================
echo CloudSim-Benchmark 构建并运行
echo ========================================
echo.

REM 构建项目
echo [1/2] 正在构建项目...
call gradle fatJar --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo 构建失败！
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] 正在运行实验...
echo.

REM 运行程序
set ARGS=
if not "%~1"=="" set ARGS=%~1
if not "%~2"=="" set ARGS=!ARGS! %~2
if not "%~3"=="" set ARGS=!ARGS! %~3
if not "%~4"=="" set ARGS=!ARGS! %~4

set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8
java -Dfile.encoding=UTF-8 -jar build\libs\cloudsim-benchmark-1.0.0-all.jar %ARGS%

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo 程序执行出错，错误代码: %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================
echo 实验完成！结果已保存到 results\ 文件夹
echo ========================================
pause

