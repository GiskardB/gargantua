@echo off
setlocal enabledelayedexpansion
REM ================================================================
REM FitCoach AI -- Full Docker Stack (GraalVM Native)
REM
REM Builds and starts ALL services in Docker:
REM   fitcoach-native (GraalVM) + Bifrost + MongoDB + Redis + Ollama
REM
REM NOTE: Native image build takes 5-10 minutes the first time.
REM ================================================================

echo.
echo  ================================================
echo   FitCoach AI -- Full Docker (GraalVM Native)
echo   fitcoach-native + bifrost + mongo + redis + ollama
echo   NOTE: First build may take 5-10 minutes
echo  ================================================
echo.

cd /d "%~dp0"

echo  [INFO] Building and starting all services (GraalVM native)...
docker compose --profile full --profile native up -d --build

if %ERRORLEVEL% NEQ 0 (
    echo  [ERROR] Failed to start. Check Docker logs.
    pause
    exit /b 1
)

echo.
echo  ================================================
echo   All services running (native)!
echo.
echo   FitCoach:  http://localhost:8081
echo   Swagger:   http://localhost:8081/swagger-ui
echo   Bifrost:   http://localhost:8090
echo   MongoDB:   localhost:27017
echo   Redis:     localhost:6379
echo   Ollama:    localhost:11434
echo.
echo   Stop with: stop.bat
echo  ================================================
echo.

pause
