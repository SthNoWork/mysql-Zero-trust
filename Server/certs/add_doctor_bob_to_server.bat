@echo off
echo Exporting doctor_bob public cert...
keytool -exportcert -alias doctor_bob -keystore doctor_bob.p12 -storepass clientpass -file doctor_bob.cer

echo Adding to server truststore...
keytool -importcert -alias doctor_bob -keystore truststore.p12 -storepass password -file doctor_bob.cer -noprompt

echo.
echo Done! Place truststore.p12 in Server/certs/
pause