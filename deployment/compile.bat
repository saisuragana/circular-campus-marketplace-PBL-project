@echo off
REM ===========================================================
REM  Circular Campus Marketplace - compile all Java sources
REM  Output: C:\campus-marketplace-final\build\classes
REM  No Maven, no Gradle. Plain javac.
REM ===========================================================
setlocal enabledelayedexpansion

set "PROJECT=%~dp0.."
pushd "%PROJECT%"
set "PROJECT=%CD%"
popd

set "SRC=%PROJECT%\src\main\java"
set "OUT=%PROJECT%\build\classes"
set "LIB=%PROJECT%\lib"

echo.
echo  Project : %PROJECT%
echo.

REM ---- 1. locate servlet-api.jar (Tomcat provides it, do NOT ship it) ----
set "SERVLET_API="
if exist "C:\xampp\tomcat\lib\servlet-api.jar" set "SERVLET_API=C:\xampp\tomcat\lib\servlet-api.jar"
if not defined SERVLET_API if exist "%LIB%\servlet-api.jar" set "SERVLET_API=%LIB%\servlet-api.jar"
if not defined SERVLET_API (
  echo  [X] servlet-api.jar not found.
  echo      Expected C:\xampp\tomcat\lib\servlet-api.jar
  echo      or copy it into %LIB%
  exit /b 1
)

REM ---- 2. locate the MySQL JDBC driver ----
set "MYSQL_JAR=%LIB%\mysql-connector-j-8.0.33.jar"
if not exist "%MYSQL_JAR%" (
  echo  [X] %MYSQL_JAR% not found.
  echo      Download mysql-connector-j-8.0.33.jar and put it in the lib folder.
  echo      See README.md, section "Things you download once".
  exit /b 1
)

REM ---- 3. check javac ----
where javac >nul 2>&1
if errorlevel 1 (
  echo  [X] javac is not on the PATH. Install a JDK and add its bin folder to PATH.
  exit /b 1
)

REM ---- 4. compile ----
if exist "%OUT%" rd /s /q "%OUT%"
mkdir "%OUT%"
dir /s /b "%SRC%\*.java" > "%PROJECT%\build\sources.txt"

echo  Compiling with Java 8 compatibility...
javac --release 8 -encoding UTF-8 -nowarn -cp "%SERVLET_API%;%MYSQL_JAR%" -d "%OUT%" "@%PROJECT%\build\sources.txt" 2>nul
if errorlevel 1 (
  echo  --release 8 not supported by this JDK, compiling with default settings...
  javac -encoding UTF-8 -nowarn -cp "%SERVLET_API%;%MYSQL_JAR%" -d "%OUT%" "@%PROJECT%\build\sources.txt"
  if errorlevel 1 (
    echo.
    echo  [X] Compilation failed. Read the messages above.
    exit /b 1
  )
)

echo.
echo  [OK] Classes written to %OUT%
dir /b "%OUT%\*.class"
endlocal
