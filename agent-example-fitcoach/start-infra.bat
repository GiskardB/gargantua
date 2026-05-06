@echo off
setlocal enabledelayedexpansion
REM ================================================================
REM FitCoach AI -- Start Full Infrastructure
REM
REM Starts: Ollama + Bifrost + MongoDB + Redis
REM Use this when you want to run the app locally via Maven.
REM After this script, run: start-app.bat
REM ================================================================

echo.
echo  ==========================================
echo   FitCoach AI -- Full Infrastructure
echo   Ollama + Bifrost + MongoDB + Redis
echo  ==========================================
echo.

cd /d "%~dp0"

REM Load .env so we can echo the configured ports back to the user
if exist ".env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
        if not "%%A"=="" set "%%A=%%B"
    )
)
if "%BIFROST_PORT%"=="" set "BIFROST_PORT=8090"

echo  [INFO] Starting all infrastructure services...
docker compose --profile full up -d --remove-orphans

if %ERRORLEVEL% NEQ 0 (
    echo  [WARN] Initial start failed. Likely stale containers from a previous run.
    echo  [WARN] Cleaning up and retrying...
    docker compose --profile full down --remove-orphans >nul 2>&1
    docker compose --profile full up -d
    if !ERRORLEVEL! NEQ 0 (
        echo  [ERROR] Failed to start infrastructure. Is Docker running?
        pause
        exit /b 1
    )
)

echo.
echo  [INFO] Waiting for Ollama model pull...
docker compose logs -f ollama-init 2>nul

echo  [INFO] Waiting for Bifrost health check...
:wait_bifrost
docker compose ps bifrost --format "{{.Health}}" 2>nul | findstr /i "healthy" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    timeout /t 3 /nobreak >nul
    goto wait_bifrost
)
echo  [OK] Bifrost is ready

echo.
echo  ==========================================
echo   Infrastructure is ready^^!
echo.
echo   Bifrost:   localhost:%BIFROST_PORT%
echo   Ollama:    localhost:11434
echo   MongoDB:   localhost:27017
echo   Redis:     localhost:6379
echo.
echo   Now run: start-app.bat
echo  ==========================================
echo.

pause
