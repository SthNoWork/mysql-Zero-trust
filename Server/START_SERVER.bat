@echo off
echo ========================================
echo  Starting E2EE Hospital Server
echo ========================================
echo.
echo Building...
call build.bat
echo.
echo Starting server on https://localhost:8000
echo Press Ctrl+C to stop
echo.
call run.bat
