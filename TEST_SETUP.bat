@echo off
echo ========================================
echo  E2EE Hospital - Connection Test
echo ========================================
echo.

echo [1/4] Checking if MySQL is accessible...
mysql -u root -e "SHOW DATABASES;" >nul 2>&1
if errorlevel 1 (
    echo [X] MySQL not accessible. Check if MySQL is running.
    pause
    exit /b 1
)
echo [OK] MySQL accessible

echo.
echo [2/4] Checking if hospital database exists...
mysql -u root -e "USE hospital; SHOW TABLES;" >nul 2>&1
if errorlevel 1 (
    echo [!] Database not found. Creating...
    mysql -u root < Server\sql\mysql.sql
    mysql -u root < Server\sql\users.sql
    echo [OK] Database created
) else (
    echo [OK] Database exists
)

echo.
echo [3/4] Checking users table...
mysql -u root hospital -e "SELECT username, role FROM users;" 2>nul
if errorlevel 1 (
    echo [X] Users table missing. Run: mysql -u root < Server\sql\users.sql
    pause
    exit /b 1
)

echo.
echo [4/4] Starting server...
echo Server will run on https://localhost:8000
echo.
echo NEXT STEPS:
echo 1. Open https://localhost:8000 in browser and accept cert warning
echo 2. Open Client\index.html
echo 3. Login with: doctor_bob / password123
echo.
pause
cd Server
call START_SERVER.bat
