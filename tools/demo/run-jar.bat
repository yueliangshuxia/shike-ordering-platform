@echo off
cd /d %~dp0

echo.
echo   SHI KE KITCHEN - local demo
echo   Starting, please wait...
echo.

if not exist "shike-ordering.jar" (
  echo   Cannot find shike-ordering.jar. Please extract the whole zip first.
  pause
  exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
  echo   Java was not found. Install Java 21, or use the portable version.
  pause
  exit /b 1
)

start "Shike Service - close this window to stop" /min java -Dfile.encoding=UTF-8 -jar shike-ordering.jar --spring.profiles.active=demo
ping -n 16 127.0.0.1 >nul
start "" http://127.0.0.1:8080/

echo.
echo   Browser opened: http://127.0.0.1:8080/
echo   If it is not ready, refresh after a few seconds.
echo   Close the "Shike Service" window to stop the server.
echo.
pause
