@echo off
setlocal EnableDelayedExpansion
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

set "FX="
for %%j in (target\lib\javafx-base-*-win.jar target\lib\javafx-graphics-*-win.jar target\lib\javafx-controls-*-win.jar) do (
  if exist "%%j" set "FX=!FX!;%%j"
)
if "!FX!"=="" (
  for %%j in (target\lib\javafx-*.jar) do set "FX=!FX!;%%j"
)
if "!FX!"=="" (
  echo Nao encontrei os JARs JavaFX em target\lib. Corre: scripts\check-mvnw.bat e depois mvnw.cmd package
  exit /b 1
)
set "FX=!FX:~1!"

java --module-path "!FX!" --add-modules javafx.controls,javafx.graphics,javafx.base -cp "target\rescue-net-1.0.0.jar" rescuenet.gui.CommandCenterApp %*
