@echo off
REM ================================================================
REM FitCoach AI -- Stop All Docker Services
REM ================================================================

echo.
echo  ====================================
echo   FitCoach AI -- Stopping all services
echo  ====================================
echo.

cd /d "%~dp0"

docker compose --profile full down

echo.
echo  [OK] All services stopped.
echo.
echo  To also remove volumes (data): docker compose --profile full down -v
echo.

pause
