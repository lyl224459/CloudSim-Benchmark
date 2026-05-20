@echo off
setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
cd /d "%PROJECT_DIR%"

chcp 65001 >nul 2>&1
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
set "JAR_FILE=build\libs\cloudsim-benchmark-1.0.0-all.jar"
set "PROXY_GRADLE_ARGS="

call :configure_system_proxy

if "%~1"=="help" goto :show_help
if "%~1"=="" goto :show_help
if "%~1"=="build" goto :build_project

if not exist "%JAR_FILE%" (
    echo [WARN] Jar not found, building first...
    call :run_gradle_build
    if errorlevel 1 goto :error_exit
)

java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 ^
     --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.util=ALL-UNNAMED ^
     -jar "%JAR_FILE%" %*

if errorlevel 1 goto :error_exit
goto :end

:build_project
call :run_gradle_build
if errorlevel 1 goto :error_exit
if "%~1"=="build" goto :end
goto :end

:run_gradle_build
echo [Build] Running Gradle fatJar...
call gradlew.bat %PROXY_GRADLE_ARGS% fatJar --no-daemon --no-configuration-cache
if errorlevel 1 exit /b 1
echo [Build] Done.
exit /b 0

:show_help
echo CloudSim-Benchmark unified Windows runner
echo.
echo Usage: scripts\run.bat [command] [options...]
echo.
echo Commands:
echo   run
echo   list
echo   config
echo   build
echo   help
echo.
echo Examples:
echo   scripts\run.bat run --mode batch --algorithms PSO,WOA --seed 42
echo   scripts\run.bat run --config configs\examples\single_config_example.toml --profile batch_small
echo   scripts\run.bat run --config configs\examples\realtime_test.toml --profile realtime_test --dry-run
echo   scripts\run.bat list algorithms --mode batch
echo   scripts\run.bat config validate --config configs\examples\batch_test.toml
echo   scripts\run.bat build
goto :end

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

:error_exit
exit /b 1

:end
