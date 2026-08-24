@echo off
.\mvnw.cmd clean verify
if errorlevel 1 pause & exit /b 1
echo.
echo Build success: target\ftp-synchronization.jar
pause
