@echo off
echo ========================================
echo E2EE Hospital System - Key Generator
echo ========================================

echo.
echo Compiling KeyGen...
javac KeyGen.java

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

echo.
echo Generating RSA-2048 key pairs...
java KeyGen

echo.
pause
