@echo off
setlocal enabledelayedexpansion
REM ================================================================
REM FitCoach AI -- Start App Locally (Full Mode)
REM
REM Prerequisites: run start-infra.bat first!
REM Runs the Spring Boot app via Maven against Docker infra.
REM ================================================================

echo.
echo  ====================================
echo   FitCoach AI -- Full Mode (local)
echo   App via Maven + Docker infra
echo  ====================================
echo.

REM Load .env variables
if exist "%~dp0.env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%~dp0.env") do (
        if not "%%A"=="" set "%%A=%%B"
    )
    echo  [OK] .env loaded
) else (
    echo  [ERROR] .env not found! Copy .env.example and fill in your config.
    pause
    exit /b 1
)

REM Verify infra is running
docker compose -f "%~dp0docker-compose.yml" --profile full ps --status running 2>nul | findstr /i "mongo" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo  [WARN] Infrastructure not detected. Run start-infra.bat first!
    echo.
)

cd /d "%~dp0.."

echo  [INFO] Starting FitCoach AI in full mode...
echo  [INFO] MongoDB: %MONGODB_URI%
echo  [INFO] Redis:   %REDIS_URL%
echo  [INFO] Bifrost: %LLM_PRIMARY_ENDPOINT%
echo  [INFO] Ollama:  %LLM_ROUTING_ENDPOINT%
echo.

call mvn spring-boot:run ^
    -pl agent-example-fitcoach ^
    -Dspring-boot.run.jvmArguments="-DMONGODB_URI=%MONGODB_URI% -DREDIS_URL=%REDIS_URL% -DSERVER_PORT=%SERVER_PORT% -DROUTING_STRATEGY=%ROUTING_STRATEGY% -DROUTING_THRESHOLD=%ROUTING_THRESHOLD% -DSPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%"

pause
