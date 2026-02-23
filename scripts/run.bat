@echo off
REM Password Manager - Windows launcher
REM Looks for an embedded JRE in runtime\, falls back to system java.

set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%password-manager.jar

if not exist "%JAR%" (
    echo Error: password-manager.jar not found in %SCRIPT_DIR%
    exit /b 1
)

REM Use embedded JRE if available, otherwise system java
if exist "%SCRIPT_DIR%runtime\bin\java.exe" (
    set JAVA=%SCRIPT_DIR%runtime\bin\java.exe
) else (
    set JAVA=java
    where java >nul 2>&1
    if errorlevel 1 (
        echo Error: Java not found. Install Java 17+ or place a JRE in runtime\
        exit /b 1
    )
)

"%JAVA%" -jar "%JAR%" %*
