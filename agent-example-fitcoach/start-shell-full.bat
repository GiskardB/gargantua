@echo off
setlocal enabledelayedexpansion
REM ================================================================
REM FitCoach AI -- Interactive Shell (Full Mode / Remote)
REM
REM Connects to a running FitCoach server via HTTP.
REM Prerequisites: start-infra.bat + start-app.bat first!
REM ================================================================

echo.
echo  ====================================
echo   FitCoach AI -- Shell (Remote)
echo   Connects to running agent server
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

set AGENT_URL=http://localhost:%SERVER_PORT%
if "%SERVER_PORT%"=="" set AGENT_URL=http://localhost:8080

REM Verify agent is running
curl -sf "%AGENT_URL%/health" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo  [WARN] Agent server not detected at %AGENT_URL%
    echo  [WARN] Start it first with: start-infra.bat + start-app.bat
    echo.
)

echo  [INFO] Starting FitCoach Shell in remote mode...
echo  [INFO] Connecting to %AGENT_URL%
echo  [INFO] Type 'chat' to start a conversation, '\help' for commands
echo.

cd /d "%~dp0.."

call mvn spring-boot:run ^
    -pl agent-shell ^
    -Dspring-boot.run.jvmArguments="-Dagent.shell.mode=remote -Dagent.shell.remote.url=%AGENT_URL%"

pause
