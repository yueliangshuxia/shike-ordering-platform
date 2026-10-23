@echo off
cd /d %~dp0

echo.
echo   SHI KE KITCHEN - portable demo
echo   Starting, please wait...
echo.

if not exist "Shike.exe" (
  echo   Cannot find Shike.exe. Please extract the whole zip first.
  pause
  exit /b 1
)

start "Shike Service - close this window to stop" /min "%~dp0Shike.exe"
ping -n 16 127.0.0.1 >nul
start "" http://127.0.0.1:8080/

echo.
echo   Browser opened: http://127.0.0.1:8080/
echo   If it is not ready, refresh after a few seconds.
echo   Close the "Shike Service" window to stop the server.
echo.
pause
