@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
echo === RescueNet: testes unitarios + e2e ===
call "%~dp0_mvn.bat" verify %*
exit /b %ERRORLEVEL%
