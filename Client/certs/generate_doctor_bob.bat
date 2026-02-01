@echo off
echo Generating doctor_bob.p12...
keytool -genkeypair -alias doctor_bob -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore doctor_bob.p12 -validity 3650 -storepass clientpass -dname "CN=doctor_bob,OU=doctor"
echo.
echo Done! Give doctor_bob.p12 to the client.
echo Client places it in Client/certs/doctor_bob.p12
pause