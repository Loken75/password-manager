@echo off
REM Password Manager - Windows launcher
set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%..\target\password-manager.jar

if not exist "%JAR%" (
    echo JAR not found. Building...
    cd /d "%SCRIPT_DIR%.." && mvn clean package -q
)

java -jar "%JAR%"
