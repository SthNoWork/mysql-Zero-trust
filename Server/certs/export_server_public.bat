@echo off
echo Exporting server public cert...
keytool -exportcert -alias server -keystore server.p12 -storepass password -file server.cer
echo.
echo Done! Give server.cer to all clients.
echo Clients place it in Client/certs/server.cer
pause