@echo off
REM CloudSim-Benchmark 运行脚本 (Windows)
REM 用法: run.bat [batch|realtime] [algorithms] [randomSeed] [runs]

chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

REM 设置编码
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8

REM 检查 JAR 文件是否存在
if not exist "build\libs\cloudsim-benchmark-1.0.0-all.jar" (
    echo 错误: 找不到 JAR 文件，请先运行 gradle fatJar 构建项目
    pause
    exit /b 1
)

REM 构建参数
set ARGS=

REM 解析参数
if not "%~1"=="" set ARGS=%~1
if not "%~2"=="" set ARGS=!ARGS! %~2
if not "%~3"=="" set ARGS=!ARGS! %~3
if not "%~4"=="" set ARGS=!ARGS! %~4

REM 运行程序
echo ========================================
echo CloudSim-Benchmark 实验平台
echo ========================================
echo.
java -Dfile.encoding=UTF-8 -jar build\libs\cloudsim-benchmark-1.0.0-all.jar %ARGS%

REM 检查执行结果
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

