@echo off
echo Generating server.p12...
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore server.p12 -validity 3650 -storepass password -dname "CN=localhost"
echo.
echo Done! Place server.p12 in Server/certs/
pause