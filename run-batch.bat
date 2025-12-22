@echo off
REM 批处理模式运行脚本
REM 用法: run-batch.bat [algorithms] [randomSeed] [runs]
REM 示例: run-batch.bat PSO,WOA 42 10

chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8

if not exist "build\libs\cloudsim-benchmark-1.0.0-all.jar" (
    echo 错误: 找不到 JAR 文件，请先运行 gradle fatJar 构建项目
    pause
    exit /b 1
)

set ARGS=batch
if not "%~1"=="" set ARGS=!ARGS! %~1
if not "%~2"=="" set ARGS=!ARGS! %~2
if not "%~3"=="" set ARGS=!ARGS! %~3

echo ========================================
echo 批处理调度模式
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
echo 实验完成！结果已保存到 results\ 文件夹
echo ========================================
pause

