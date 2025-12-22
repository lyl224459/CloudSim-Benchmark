@echo off
REM 批量任务数实验模式运行脚本
REM 用法: run-batch-multi.bat [cloudletCounts] [algorithms] [randomSeed]
REM 示例: run-batch-multi.bat 50,100,200,500
REM 示例: run-batch-multi.bat 50,100,200 PSO,WOA
REM 示例: run-batch-multi.bat 50,100,200 PSO,WOA 42

chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8

if not exist "build\libs\cloudsim-benchmark-1.0.0-all.jar" (
    echo 错误: 找不到 JAR 文件，请先运行 gradle fatJar 构建项目
    pause
    exit /b 1
)

set ARGS=batch-multi
if not "%~1"=="" set ARGS=!ARGS! %~1
if not "%~2"=="" set ARGS=!ARGS! %~2
if not "%~3"=="" set ARGS=!ARGS! %~3

echo ========================================
echo 批量任务数实验模式
echo ========================================
echo.

java -Dfile.encoding=UTF-8 -jar build\libs\cloudsim-benchmark-1.0.0-all.jar %ARGS%

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo 程序执行出错，错误代码: %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================
echo 批量实验完成！结果已保存到 results\ 文件夹
echo ========================================
pause

