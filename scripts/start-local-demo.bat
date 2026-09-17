@echo off
setlocal
cd /d "%~dp0\.."

if not exist "target\rescue-net-1.0.0.jar" (
  echo A compilar RescueNet...
  call "%~dp0_mvn.bat" -q -DskipTests package
  if errorlevel 1 exit /b 1
)

echo A abrir Command Center e 3 drones locais (JVMs separadas)...
start "RescueNet Central" cmd /k "%~dp0start-central.bat"
timeout /t 3 /nobreak >nul
start "DR-001 Maputo" cmd /k "%~dp0start-drone.bat" --id DR-001 --base Maputo --lat -25.9692 --lon 32.5732 --battery 90 --central 127.0.0.1:1099 --hostname 127.0.0.1
start "DR-002 Matola" cmd /k "%~dp0start-drone.bat" --id DR-002 --base Matola --lat -25.9622 --lon 32.4589 --battery 76 --central 127.0.0.1:1099 --hostname 127.0.0.1
start "DR-003 Marracuene" cmd /k "%~dp0start-drone.bat" --id DR-003 --base Marracuene --lat -25.7369 --lon 32.6744 --battery 95 --central 127.0.0.1:1099 --hostname 127.0.0.1
echo Pronto. Para simular falha, fecha a janela do drone em missao.
