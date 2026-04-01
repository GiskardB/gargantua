@echo off
setlocal enabledelayedexpansion
REM ================================================================
REM FitCoach AI -- Interactive Shell (Embedded Mode)
REM
REM Runs the agent-shell CLI with the FitCoach agent engine
REM embedded in-process. No external database (in-memory stores).
REM Infrastructure: Ollama + Bifrost (via Docker)
REM
REM The shell loads the FitCoach application config so the engine
REM has all LLM, skill and tool configurations available.
REM ================================================================

echo.
echo  ====================================
echo   FitCoach AI -- Shell (Embedded)
echo   Interactive CLI + in-memory stores
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
docker compose up -d

if %ERRORLEVEL% NEQ 0 (
    echo  [ERROR] Failed to start infrastructure. Is Docker running?
    pause
    exit /b 1
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
echo  [INFO] Starting FitCoach Shell in embedded mode...
echo  [INFO] Type 'chat' to start a conversation, '\help' for commands
echo.

cd /d "%~dp0.."

REM Set Spring config to load from fitcoach resources (handles spaces in path)
set "SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:%~dp0src/main/resources/"
set "SPRING_PROFILES_ACTIVE=embedded"

REM Run agent-shell with embedded profile
REM -Dspring-boot.run.profiles ensures Spring picks up embedded even if env var is lost
call mvn spring-boot:run ^
    -pl agent-shell ^
    -Dspring-boot.run.profiles=embedded

pause
