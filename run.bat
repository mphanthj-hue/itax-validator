@echo off
REM iTax Validator CLI Launcher
REM Usage: run.bat <itax-home> <xml-file|directory> [more files...]

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "TARGET_DIR=%SCRIPT_DIR%target"

REM Find the validator JAR
for %%f in ("%TARGET_DIR%\itax-validator-*.jar") do set "JAR_FILE=%%f"

if not defined JAR_FILE (
    echo ERROR: Validator JAR not found. Run "mvn package" first.
    exit /b 2
)

if "%1"=="" (
    echo iTax Validator CLI
    echo Usage: %~n0 ^<itax-home^> ^<xml-file^|directory^> [more files...]
    echo.
    echo   ^<itax-home^>   Path to iTaxViewer installation (containing data/, certstore/)
    echo   ^<xml-file^>    XML file(s) or folder(s) to validate
    echo.
    echo Exit codes: 0=all valid, 1=some invalid, 2=error
    exit /b 2
)

java -jar "%JAR_FILE%" %*
exit /b %ERRORLEVEL%
