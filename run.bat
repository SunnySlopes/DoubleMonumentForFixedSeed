@echo off
setlocal enabledelayedexpansion
rem Always run the newest build\libs\MultiMonumentFinder-*.jar so a stale jar
rem (e.g. an old -1.0 build) is never launched by accident.
set "JAR="
for /f "delims=" %%f in ('dir /b /o-d "build\libs\MultiMonumentFinder-*.jar" 2^>nul') do (
    if not defined JAR set "JAR=build\libs\%%f"
)
if not defined JAR (
    echo [ERROR] No jar under build\libs. Build it first: gradlew.bat buildAllJars
    pause
    exit /b 1
)
echo Running %JAR%
java -jar "%JAR%"
pause
