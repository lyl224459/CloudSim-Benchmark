@echo off
:: CloudSim-Benchmark 运行脚本
:: 支持新的命名参数格式
:: 示例:
::   run.cmd batch --algorithms ALL
::   run.cmd realtime --algorithms ALL
::   run.cmd --help

:: 强制控制台使用 UTF-8 编码
chcp 65001 >nul

set JAR_FILE=build/libs/cloudsim-benchmark-1.0.0-all.jar

if not exist "%JAR_FILE%" (
    echo [错误] 找不到 JAR 文件，请先运行: gradlew fatJar
    exit /b 1
)

:: 执行程序
if "%1"=="podman" (
    shift
    echo [容器] 正在初始化工作空间...
    
    :: 在当前目录创建项目工作根目录（如果不存在）
    if not exist "benchmark_workspace" mkdir benchmark_workspace
    if not exist "runs" mkdir runs

    echo [容器] 正在通过 Podman 运行...
    :: 将四个目录挂载到容器内的 /app/benchmark_workspace 下
    :: 并设置容器的工作目录为该新建的根目录
    podman run --rm ^
        -v "%cd%\runs:/app/benchmark_workspace/runs" ^
        -v "%cd%\configs:/app/benchmark_workspace/configs" ^
        -v "%cd%\src:/app/benchmark_workspace/src" ^
        -v "%cd%\data:/app/benchmark_workspace/data" ^
        --workdir /app/benchmark_workspace ^
        cloudsim-benchmark:latest %*
    exit /b %errorlevel%
)

java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED -jar "%JAR_FILE%" %*
