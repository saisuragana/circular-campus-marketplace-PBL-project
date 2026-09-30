@echo off
REM ===========================================================
REM  Circular Campus Marketplace - deploy to Tomcat
REM
REM  Target : C:\xampp\tomcat\webapps\campus-marketplace-final
REM  URL    : http://localhost:8080/campus-marketplace-final/
REM
REM  This script NEVER touches C:\xampp\tomcat\webapps\campus-marketplace
REM ===========================================================
setlocal

set "PROJECT=%~dp0.."
pushd "%PROJECT%"
set "PROJECT=%CD%"
popd

set "APP=campus-marketplace-final"
set "TOMCAT=C:\xampp\tomcat"
set "TARGET=%TOMCAT%\webapps\%APP%"
set "WEBAPP=%PROJECT%\src\main\webapp"
set "CLASSES=%PROJECT%\build\classes"
set "LIB=%PROJECT%\lib"

if not exist "%TOMCAT%\webapps" (
  echo  [X] %TOMCAT%\webapps does not exist. Is XAMPP installed at C:\xampp ?
  exit /b 1
)

REM ---- safety check: never deploy over the old project ----
if /I "%APP%"=="campus-marketplace" (
  echo  [X] Refusing to deploy over the old application.
  exit /b 1
)

REM ---- 1. compile ----
call "%~dp0compile.bat"
if errorlevel 1 (
  echo  [X] Deployment stopped because compilation failed.
  exit /b 1
)

REM ---- 2. clean the target folder ----
echo.
echo  Deploying to %TARGET%
if exist "%TARGET%" rd /s /q "%TARGET%"
mkdir "%TARGET%"
mkdir "%TARGET%\WEB-INF"
mkdir "%TARGET%\WEB-INF\classes"
mkdir "%TARGET%\WEB-INF\lib"

REM ---- 3. static files (html, css, js, assets, WEB-INF\web.xml) ----
xcopy "%WEBAPP%\*" "%TARGET%\" /E /I /Y /Q >nul

REM ---- 4. compiled classes ----
xcopy "%CLASSES%\*" "%TARGET%\WEB-INF\classes\" /E /I /Y /Q >nul

REM ---- 5. JDBC driver only. servlet-api.jar must NOT be copied:
REM         Tomcat already provides it and a second copy breaks the app. ----
copy /Y "%LIB%\mysql-connector-j-8.0.33.jar" "%TARGET%\WEB-INF\lib\" >nul
if exist "%TARGET%\WEB-INF\lib\servlet-api.jar" del /q "%TARGET%\WEB-INF\lib\servlet-api.jar"
if exist "%TARGET%\WEB-INF\lib\jsp-api.jar" del /q "%TARGET%\WEB-INF\lib\jsp-api.jar"

echo.
echo  [OK] Deployed.
echo.
echo   1. Start MySQL and Tomcat from the XAMPP control panel
echo      (or run %TOMCAT%\bin\startup.bat)
echo   2. Open http://localhost:8080/%APP%/
echo.
endlocal
