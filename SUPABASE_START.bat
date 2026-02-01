@echo off
echo ========================================
echo  E2EE Hospital - Supabase Setup
echo ========================================
echo.
echo [INFO] Using Supabase: xvlilgsawbqpedmrbdkv.supabase.co
echo.
echo [1/2] Make sure you ran this SQL in Supabase SQL Editor:
echo        Server/sql/postgresql.sql
echo        Server/sql/users.sql
echo.
echo Test login: doctor_bob / password123
echo.
pause
echo.
echo [2/2] Starting server on https://localhost:8000...
echo.
cd Server
call START_SERVER.bat
