@echo off
mvn clean package
if errorlevel 1 pause & exit /b 1
echo.
echo Build success: target\ftp-upload-javafx.jar
pause
