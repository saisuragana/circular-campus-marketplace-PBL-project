@echo off
REM ===========================================================
REM  Removes ONLY campus-marketplace-final from Tomcat.
REM  The older campus-marketplace application is left alone.
REM ===========================================================
setlocal

set "TARGET=C:\xampp\tomcat\webapps\campus-marketplace-final"

if not exist "%TARGET%" (
  echo  Nothing to remove - %TARGET% does not exist.
  exit /b 0
)

echo  This will delete:
echo      %TARGET%
echo.
set /p CONFIRM=  Type YES to continue: 
if /I not "%CONFIRM%"=="YES" (
  echo  Cancelled. Nothing was deleted.
  exit /b 0
)

rd /s /q "%TARGET%"
echo  [OK] Removed. Restart Tomcat if it was running.
endlocal
