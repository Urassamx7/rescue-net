@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
echo === RescueNet: testes unitarios ===
call "%~dp0_mvn.bat" test %*
exit /b %ERRORLEVEL%
