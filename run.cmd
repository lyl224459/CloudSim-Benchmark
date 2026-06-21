@echo off
setlocal EnableDelayedExpansion

chcp 65001 >nul
set "PACKAGED_JAR=cloudsim-benchmark-all.jar"
call :resolve_jar_file
set "PROXY_GRADLE_ARGS="

call :configure_system_proxy

if "%~1"=="build" (
    call :build_project
    exit /b %errorlevel%
)

if "%~1"=="podman" (
    shift
    if not exist "benchmark_workspace" mkdir benchmark_workspace
    if not exist "runs" mkdir runs
    podman run --rm ^
        -v "%cd%\runs:/app/runs" ^
        cloudsim-benchmark:latest %*
    exit /b %errorlevel%
)

if not exist "%JAR_FILE%" (
    echo [WARN] Jar not found, building first...
    call :build_project
    if errorlevel 1 exit /b %errorlevel%
    call :resolve_jar_file
)

java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dlogback.configurationFile=cloudsim-benchmark-logback.xml --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED -jar "%JAR_FILE%" %*
exit /b %errorlevel%

:configure_system_proxy
set "SYSTEM_PROXY="
set "PROXY_ENABLE="
for /f "tokens=3" %%p in ('reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable 2^>nul') do set "PROXY_ENABLE=%%p"
if /i not "%PROXY_ENABLE%"=="0x1" exit /b 0
for /f "tokens=2,*" %%a in ('reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyServer 2^>nul') do set "SYSTEM_PROXY=%%b"
if not defined SYSTEM_PROXY exit /b 0
for /f "tokens=1 delims=;" %%p in ("%SYSTEM_PROXY%") do set "SYSTEM_PROXY=%%p"
for /f "tokens=1,2 delims==" %%a in ("%SYSTEM_PROXY%") do if not "%%b"=="" set "SYSTEM_PROXY=%%b"
for /f "tokens=1,2 delims=:" %%a in ("%SYSTEM_PROXY%") do (
    set "PROXY_HOST=%%a"
    set "PROXY_PORT=%%b"
)
if defined PROXY_HOST if defined PROXY_PORT (
    set "GRADLE_OPTS=!GRADLE_OPTS! -Dhttp.proxyHost=!PROXY_HOST! -Dhttp.proxyPort=!PROXY_PORT! -Dhttps.proxyHost=!PROXY_HOST! -Dhttps.proxyPort=!PROXY_PORT!"
    set "JAVA_TOOL_OPTIONS=!JAVA_TOOL_OPTIONS! -Dhttp.proxyHost=!PROXY_HOST! -Dhttp.proxyPort=!PROXY_PORT! -Dhttps.proxyHost=!PROXY_HOST! -Dhttps.proxyPort=!PROXY_PORT!"
    set "PROXY_GRADLE_ARGS=-Dhttp.proxyHost=!PROXY_HOST! -Dhttp.proxyPort=!PROXY_PORT! -Dhttps.proxyHost=!PROXY_HOST! -Dhttps.proxyPort=!PROXY_PORT!"
    echo [Proxy] Using system proxy: !PROXY_HOST!:!PROXY_PORT!
)
exit /b 0

:build_project
echo [Build] Running Gradle fatJar...
call gradlew.bat %PROXY_GRADLE_ARGS% fatJar --no-daemon
exit /b %errorlevel%

:resolve_jar_file
set "BUILD_JAR="
for %%j in (build\libs\cloudsim-benchmark-*-all.jar) do if exist "%%~fj" set "BUILD_JAR=%%~fj"
if exist "%PACKAGED_JAR%" (
    set "JAR_FILE=%PACKAGED_JAR%"
) else if defined BUILD_JAR (
    set "JAR_FILE=%BUILD_JAR%"
) else (
    set "JAR_FILE=build\libs\cloudsim-benchmark-*-all.jar"
)
exit /b 0
