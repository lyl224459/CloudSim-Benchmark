@echo off
REM CloudSim-Benchmark Windows运行脚本
REM 这是对bash脚本的Windows包装器

REM 获取脚本目录
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."

REM 切换到项目目录
cd /d "%PROJECT_DIR%"

REM 设置控制台编码为UTF-8（解决中文乱码问题）
chcp 65001 >nul 2>&1

REM 设置Java编码参数
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8

REM 检查参数
if "%~1"=="help" goto :show_help
if "%~1"=="" goto :show_help
if "%~1"=="build" goto :build_project

REM 检查JAR文件
if not exist "build\libs\cloudsim-benchmark-1.0.0-all.jar" (
    echo [警告] 找不到 JAR 文件，正在自动构建...
    goto :build_project
)

REM 运行程序
echo ========================================
echo CloudSim-Benchmark 实验平台
echo ========================================
echo 平台: Windows
echo 时间: %DATE% %TIME%
echo 模式: %1
echo.

set ARGS=%*

java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 ^
     --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.util=ALL-UNNAMED ^
     -jar build\libs\cloudsim-benchmark-1.0.0-all.jar %ARGS%

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [错误] 程序执行失败，错误代码: %ERRORLEVEL%
    goto :error_exit
)

echo.
echo ========================================
echo 实验完成！结果已保存到 runs\ 文件夹
echo ========================================
goto :end

:build_project
echo ========================================
echo 构建 CloudSim-Benchmark
echo ========================================
echo 平台: Windows
echo 时间: %DATE% %TIME%
echo.

echo [构建] 执行 Gradle 构建...
call gradlew.bat fatJar --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 构建失败！
    goto :error_exit
)
echo [构建] 成功完成!
if "%~1"=="build" goto :end
REM 如果是通过自动构建进入的，继续运行
goto :run_program

:show_help
echo CloudSim-Benchmark 统一运行脚本
echo.
echo 自动检测平台: Windows
echo.
echo 用法: scripts\run.bat [mode] [options...]
echo.
echo 模式:
echo   batch          [批处理调度模式]
echo   realtime       [实时调度模式 ^(默认^)]
echo   batch-multi    [批量任务数实验 ^(批处理^)]
echo   realtime-multi [批量任务数实验 ^(实时^)]
echo   build          [构建项目]
echo   help           [显示此帮助]
echo.
echo 示例:
echo   scripts\run.bat batch PSO,WOA 42
echo   scripts\run.bat realtime PSO_REALTIME,WOA_REALTIME
echo   scripts\run.bat batch-multi 50,100,200,500 10 PSO,WOA
echo   scripts\run.bat build
echo.
echo 更多信息请查看 README.md
goto :end

:error_exit
pause
exit /b %ERRORLEVEL%

:end