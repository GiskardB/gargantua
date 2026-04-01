@echo off
REM ================================================================
REM FitCoach AI -- Integration Test Suite
REM
REM Runs all framework feature tests against a running instance.
REM Prerequisites: Node.js 18+, FitCoach running on localhost:8080
REM ================================================================

echo.
echo  ====================================
echo   FitCoach AI -- Integration Tests
echo  ====================================
echo.

cd /d "%~dp0"

node --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo  [ERROR] Node.js not found. Install Node.js 18+
    pause
    exit /b 1
)

node test-suite.mjs %*

echo.
pause
