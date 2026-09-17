@echo off
setlocal
cd /d "%~dp0\.."

if not exist "target\rescue-net-1.0.0.jar" (
  echo A compilar RescueNet...
  call "%~dp0_mvn.bat" -q -DskipTests package
  if errorlevel 1 exit /b 1
)

if not defined JAVA_HOME (
  for /d %%j in ("C:\Program Files\Java\jdk-26*") do set "JAVA_HOME=%%j"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

java -cp "target\rescue-net-1.0.0.jar" rescuenet.drone.DroneServer %*
