@echo off
setlocal enabledelayedexpansion
echo.
echo ===============================================
echo  E2EE Hospital Records - Setup Script
echo ===============================================
echo.

REM Check if files exist
if not exist "Server\certs\generate_server.bat" (
    echo ERROR: generate_server.bat not found in Server\certs\
    pause
    exit /b 1
)

echo STEP 1: Generate Server Certificate
echo =====================================
cd Server\certs
call generate_server.bat
cd ..\..\

if not exist "Server\certs\server.p12" (
    echo ERROR: server.p12 was not created!
    pause
    exit /b 1
)

echo.
echo STEP 2: Export Server Public Certificate
echo ==========================================
cd Server\certs
call export_server_public.bat
cd ..\..\

if not exist "Server\certs\server.cer" (
    echo ERROR: server.cer was not created!
    pause
    exit /b 1
)

echo.
echo STEP 3: Copy server.cer to Client
echo ===================================
if not exist "Client\certs" mkdir Client\certs
copy "Server\certs\server.cer" "Client\certs\server.cer"
echo ✓ Copied server.cer to Client\certs\

echo.
echo STEP 4: Generate Client Certificates
echo ====================================
if exist "Client\certs\generate_doctor_bob.bat" (
    REM Delete existing keystore to regenerate
    if exist "Client\certs\doctor_bob.p12" (
        echo Deleting existing doctor_bob.p12...
        del "Client\certs\doctor_bob.p12"
    )
    
    echo Running: Client\certs\generate_doctor_bob.bat
    cd Client\certs
    call generate_doctor_bob.bat
    cd ..\..\
    
    echo.
    echo NOTE: Client should now have doctor_bob.p12 in Client\certs\
    echo.
    echo Exporting doctor_bob.cer for server truststore...
    cd Client\certs
    keytool -exportcert -alias doctor_bob -keystore doctor_bob.p12 -storepass clientpass -file doctor_bob.cer
    cd ..\..\
    
    if exist "Client\certs\doctor_bob.cer" (
        copy "Client\certs\doctor_bob.cer" "Server\certs\doctor_bob.cer"
        echo ✓ doctor_bob.cer copied to Server\certs\
    )
    pause
) else (
    echo SKIP: No client .bat file found (generate_[name].bat)
    echo       Admin should create client certs via Admin\index.html
    pause
)

echo.
echo STEP 5: Add Client Certificate to Server Truststore
echo =====================================================
if exist "Server\certs\add_doctor_bob_to_server.bat" (
    if exist "Server\certs\doctor_bob.cer" (
        cd Server\certs
        REM Delete existing truststore to start fresh
        if exist "truststore.p12" (
            echo Resetting truststore.p12...
            del "truststore.p12"
        )
        call add_doctor_bob_to_server.bat
        cd ..\..\
        if exist "Server\certs\truststore.p12" (
            echo ✓ Client certificate added to truststore
        )
    ) else (
        echo ERROR: doctor_bob.cer not found in Server\certs\
        echo Run STEP 4 again to export the certificate
    )
) else (
    echo SKIP: No add_[name]_to_server.bat file found
    echo       Admin should create via Admin\index.html
)

echo.
echo ===============================================
echo  Setup Complete!
echo ===============================================
echo.
echo Next Steps:
echo   1. Verify config.properties in Server\
echo   2. Run users.sql in your database
echo   3. Distribute Client\ folder to users
echo   4. Users load their private keys in Client\index.html
echo.
pause