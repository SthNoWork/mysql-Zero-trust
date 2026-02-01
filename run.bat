@echo off
echo ========================================
echo E2EE Hospital System - Server Launcher
echo ========================================

if not exist bin\WebMain.class (
    echo.
    echo ERROR: Project not compiled!
    echo Run build.bat first.
    pause
    exit /b 1
)

echo.
echo Starting E2EE Server...
echo.

set DB_TYPE=%1
if "%DB_TYPE%"=="" set DB_TYPE=mysql

java -cp bin WebMain %DB_TYPE%
