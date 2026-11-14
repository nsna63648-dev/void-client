@echo off
REM ต้องติดตั้ง JDK 21 และ Gradle 8.14+ ก่อน (หรือคัดลอก gradlew จาก template ของ Fabric)
gradle build
echo.
echo ไฟล์ jar อยู่ที่ build\libs\voidclient-1.0.0.jar
pause
