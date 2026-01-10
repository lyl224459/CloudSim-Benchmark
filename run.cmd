@echo off
:: 强制控制台使用 UTF-8 编码
chcp 65001 >nul

set JAR_FILE=build/libs/cloudsim-benchmark-1.0.0-all.jar

if not exist "%JAR_FILE%" (
    echo [错误] 找不到 JAR 文件，请先运行: gradlew fatJar
    exit /b 1
)

:: 执行程序，同时强制 JVM 内部编码为 UTF-8
java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar "%JAR_FILE%" %*
