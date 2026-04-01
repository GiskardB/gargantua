@echo off
setlocal enabledelayedexpansion
REM ================================================================
REM FitCoach AI -- Full Docker Stack (JVM)
REM
REM Builds and starts ALL services in Docker:
REM   fitcoach (JVM) + Bifrost + MongoDB + Redis + Ollama
REM ================================================================

echo.
echo  ====================================
echo   FitCoach AI -- Full Docker (JVM)
echo   fitcoach + bifrost + mongo + redis + ollama
echo  ====================================
echo.

cd /d "%~dp0"

echo  [INFO] Building and starting all services (JVM)...
docker compose --profile full up -d --build

if %ERRORLEVEL% NEQ 0 (
    echo  [ERROR] Failed to start. Check Docker logs.
    pause
    exit /b 1
)

echo.
echo  ====================================
echo   All services running!
echo.
echo   FitCoach:  http://localhost:8081
echo   Swagger:   http://localhost:8081/swagger-ui
echo   Bifrost:   http://localhost:8090
echo   MongoDB:   localhost:27017
echo   Redis:     localhost:6379
echo   Ollama:    localhost:11434
echo.
echo   Stop with: stop.bat
echo  ====================================
echo.

pause
