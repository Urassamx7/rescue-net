@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
echo === RescueNet: verificar Maven Wrapper ===
call "%~dp0_mvn.bat" -v
if errorlevel 1 (
  echo mvnw falhou. Confirma JDK 26 em JAVA_HOME.
  exit /b 1
)
echo.
echo JAVA_HOME=%JAVA_HOME%
exit /b 0
