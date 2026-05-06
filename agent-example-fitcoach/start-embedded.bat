@echo off
setlocal enabledelayedexpansion
REM ================================================================
REM FitCoach AI -- Embedded Mode
REM
REM Infrastructure: Ollama + Bifrost (via Docker)
REM App stores:     in-memory (no MongoDB, no Redis)
REM
REM Prerequisites:
REM   - Java 21+
REM   - Maven 3.8+
REM   - Docker running
REM   - .env with Bifrost/LLM config
REM ================================================================

echo.
echo  ====================================
echo   FitCoach AI -- Embedded Mode
echo   Ollama + Bifrost (in-memory stores)
echo  ====================================
echo.

cd /d "%~dp0"

REM Load .env variables
if exist ".env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
        if not "%%A"=="" set "%%A=%%B"
    )
    echo  [OK] .env loaded
) else (
    echo  [WARN] .env not found -- using defaults
)

echo  [INFO] Starting infrastructure (Ollama + Bifrost)...
docker compose up -d --remove-orphans

if %ERRORLEVEL% NEQ 0 (
    echo  [WARN] Initial start failed. Likely stale containers from a previous run.
    echo  [WARN] Cleaning up and retrying...
    docker compose down --remove-orphans >nul 2>&1
    docker compose up -d
    if !ERRORLEVEL! NEQ 0 (
        echo  [ERROR] Failed to start infrastructure. Is Docker running?
        pause
        exit /b 1
    )
)

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
echo  [INFO] Starting FitCoach AI in embedded mode...
echo.

cd /d "%~dp0.."

call mvn spring-boot:run ^
    -pl agent-example-fitcoach ^
    -Dspring-boot.run.profiles=embedded ^
    -Dspring-boot.run.jvmArguments="-DSERVER_PORT=%SERVER_PORT%"

pause
