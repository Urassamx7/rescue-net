@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
echo === RescueNet: testes e2e (RMI, selecao, failover) ===
call "%~dp0_mvn.bat" verify -DskipUnitTests %*
exit /b %ERRORLEVEL%
