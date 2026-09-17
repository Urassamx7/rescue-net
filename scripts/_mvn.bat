@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
if not exist "mvnw.cmd" (
  echo mvnw.cmd nao encontrado. Corre os scripts a partir do repositorio RescueNet.
  exit /b 1
)
call mvnw.cmd %*
exit /b %ERRORLEVEL%
