@echo off
echo RescueNet — regras de entrada no firewall (executar como Administrador).
echo Central: TCP 1099 (registry) e 1100 (objeto FleetDirectory).
echo Drone:   TCP 1101 (e 1102/1103 se varios drones no mesmo PC).
echo.

netsh advfirewall firewall add rule name="RescueNet registry 1099" dir=in action=allow protocol=TCP localport=1099
if errorlevel 1 exit /b 1
netsh advfirewall firewall add rule name="RescueNet objeto 1100" dir=in action=allow protocol=TCP localport=1100
if errorlevel 1 exit /b 1
netsh advfirewall firewall add rule name="RescueNet drone 1101" dir=in action=allow protocol=TCP localport=1101
if errorlevel 1 exit /b 1
netsh advfirewall firewall add rule name="RescueNet drone 1102" dir=in action=allow protocol=TCP localport=1102
if errorlevel 1 exit /b 1
netsh advfirewall firewall add rule name="RescueNet drone 1103" dir=in action=allow protocol=TCP localport=1103
if errorlevel 1 exit /b 1

echo Regras criadas. No PC da central precisa de 1099 e 1100; em cada PC drone, 1101.
