@echo off
echo Compiling Java files...

REM Create bin directory if it doesn't exist
if not exist bin mkdir bin
if not exist bin\certs mkdir bin\certs
if not exist bin\model mkdir bin\model
if not exist bin\repository mkdir bin\repository
if not exist bin\server mkdir bin\server
if not exist bin\service mkdir bin\service
if not exist bin\util mkdir bin\util
if not exist bin\web mkdir bin\web

REM Compile all Java files
javac -encoding UTF-8 -d bin -cp "lib/*;src" src\*.java src\model\*.java src\repository\*.java src\server\*.java src\service\*.java src\util\*.java 2>&1

if %errorlevel% neq 0 (
    echo.
    echo Compilation failed! Press any key to exit.
    pause
    exit /b %errorlevel%
)

echo Compilation successful!
echo Running WebMain...
echo.

REM Run WebMain
java -cp "bin;lib/*" WebMain

pause
