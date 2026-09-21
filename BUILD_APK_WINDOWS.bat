@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat assembleDebug
if errorlevel 1 (
    pause
    exit /b 1
)
echo.
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
