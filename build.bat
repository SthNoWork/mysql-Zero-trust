@echo off
echo ========================================
echo E2EE Hospital System - Build Script
echo ========================================

echo.
echo Step 1: Creating bin directory...
if not exist bin mkdir bin

echo.
echo Step 2: Compiling Java files...
javac -d bin src\util\*.java src\model\*.java src\repository\*.java src\server\*.java src\service\*.java src\view\*.java src\*.java 2>nul

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Compilation failed!
    echo Make sure you have Java 17+ installed.
    pause
    exit /b 1
)

echo.
echo Step 3: Copying web files...
xcopy /Y /I /Q src\web\* bin\web\ >nul 2>&1
if not exist bin\web mkdir bin\web
copy /Y src\web\*.html bin\web\ >nul 2>&1
copy /Y src\web\*.js bin\web\ >nul 2>&1
copy /Y src\web\*.css bin\web\ >nul 2>&1

echo.
echo ========================================
echo Build Complete!
echo.
echo To run the server:
echo   java -cp bin WebMain
echo.
echo Or with database option:
echo   java -cp bin WebMain mysql
echo   java -cp bin WebMain supabase
echo ========================================
