# Ligar duas máquinas físicas

Dois (ou mais) computadores reais, na mesma LAN. Um corre a Central. Cada um dos outros corre um drone. `127.0.0.1` e `scripts\start-local-demo.bat` **não** servem aqui — isso fica no mesmo PC.

Substitui os IPs de exemplo pelos que o `ipconfig` mostrar **nessa** máquina.

| Máquina física | Papel | IP de exemplo |
|---|---|---|
| PC A | Command Center (servidor) | `192.168.1.10` |
| PC B | drone DR-001 | `192.168.1.11` |
| PC C | drone DR-002 (opcional) | `192.168.1.12` |

`--central` = IPv4 do **PC A**.
`--hostname` = IPv4 **deste** computador (PC B, C, …). Se puseres `127.0.0.1`, a Central tenta falar consigo própria e o drone fica OFFLINE.

## Tráfego entre os PCs

A Central escuta em duas portas. Cada drone escuta numa terceira, para a Central o poder chamar de volta.

```
PC B (drone)                                     PC A (Central)
------------                                     --------------
                                                 TCP 1099  registry RescueNet
                                                 TCP 1100  objeto FleetDirectory
TCP 1101  objeto DroneService

1. lookup  rmi://192.168.1.10:1099/RescueNet  --------------->  1099 no PC A
2. join(snapshot, stub deste PC)              --------------->  1100 no PC A
3. getSnapshot / assignMission / abort        <---------------  1101 no PC B
```

| Porta TCP | Que máquina a abre | Que máquina liga | Uso |
|---|---|---|---|
| **1099** | PC A | PC B, C, … | encontrar `RescueNet` |
| **1100** | PC A | PC B, C, … | `join` / `leave` |
| **1101** | cada PC drone | PC A | heartbeat e missões |

Se só a 1099 estiver aberta no PC A, o drone encontra o registry e o heartbeat morre: o PC A não consegue entrar na 1101 do PC B.

## 0. Em cada máquina física

1. JDK 26 instalado (`C:\Program Files\Java\jdk-26*`).
2. Cópia do projecto **nessa** máquina (pen, git, pasta partilhada).
3. Compilar **nessa** máquina:

```bat
mvnw.cmd package
```

4. Rede **Privada** (não Público, não Wi-Fi de convidado). VPN desligada.
5. IPv4 da placa que está no cabo / Wi-Fi da sala — não `127.0.0.1`, não `169.254.*`.

```bat
ipconfig
```

No PowerShell:

```powershell
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.PrefixOrigin -ne "WellKnown" } | Format-Table InterfaceAlias, IPAddress
```

Anota: IP do PC A = `IP_A`. IP deste drone = `IP_B`.

Do **PC B**, o PC A tem de responder:

```bat
ping IP_A
```

Do **PC A**, o PC B tem de responder:

```bat
ping IP_B
```

Se o ping falhar, as duas máquinas não estão na mesma rede. Não continues para o Java.

## 1. Firewall em cada máquina física (Administrador)

Abrir uma consola **como Administrador** em **todos** os PCs (A, B, C) e correr **nessa** máquina:

```bat
cd d:\Programming\rescue-net
scripts\allow-lan.bat
```

(ajusta o caminho à pasta do projecto **naquele** disco.)

| Nesta máquina | Portas de entrada que o outro PC precisa |
|---|---|
| PC A (Central) | **1099** e **1100** |
| PC B, C (um drone cada) | **1101** |

À mão, no PC A:

```bat
netsh advfirewall firewall add rule name="RescueNet registry 1099" dir=in action=allow protocol=TCP localport=1099
netsh advfirewall firewall add rule name="RescueNet objeto 1100" dir=in action=allow protocol=TCP localport=1100
```

À mão, em cada PC drone:

```bat
netsh advfirewall firewall add rule name="RescueNet drone 1101" dir=in action=allow protocol=TCP localport=1101
```

Confirmar na máquina em que acabaste de criar a regra:

```bat
netsh advfirewall firewall show rule name=all | findstr RescueNet
```

Se o drone aparece na frota e cai a OFFLINE, o Windows do **PC B** está a recusar a chamada de volta. Permite o Java nesse PC:

```bat
netsh advfirewall firewall add rule name="RescueNet Java" dir=in action=allow program="%JAVA_HOME%\bin\java.exe" enable=yes
```

## 2. Ligar o PC A (servidor) — primeiro

No **PC A**:

```bat
scripts\start-central.bat --port 1099 --bind 1100
```

O canto da janela tem de mostrar o IPv4 **deste** PC, por exemplo:

```
rmi://192.168.1.10:1099/RescueNet  ·  objeto 192.168.1.10:1100
```

Se aparecer `127.0.0.1`, o PC B nunca chega. Fecha, confirma o `ipconfig` do PC A, volta a abrir.

Deixa esta janela aberta. Um segundo Command Center no mesmo PC ocupa a 1099.

Com a GUI no ar, no PC A:

```powershell
netstat -an | findstr "1099 1100"
```

Tem de haver `0.0.0.0:1099` e `0.0.0.0:1100` em `LISTENING` (todas as placas, não só loopback).

## 3. Ligar o PC B (drone) — noutro computador

No **PC B** (não no A). Troca `192.168.1.10` pelo IP do PC A e `192.168.1.11` pelo IP do PC B:

```bat
scripts\start-drone.bat --id DR-001 --base Maputo --lat -25.9692 --lon 32.5732 --battery 90 --central 192.168.1.10:1099 --hostname 192.168.1.11 --listen 1101
```

| Argumento | Neste exemplo | O que é |
|---|---|---|
| `--central` | `192.168.1.10:1099` | IPv4 do **PC A** e porta do registry |
| `--hostname` | `192.168.1.11` | IPv4 do **PC B** (esta máquina) |
| `--listen` | `1101` | Porta neste PC B que o PC A vai chamar |

`--central 127.0.0.1:1099` no PC B aponta para o próprio B. A Central no A nunca é encontrada.

A consola no PC B: **A LIGAR** → **REGISTADO**. O cartão deve mostrar o URL do PC A, o endpoint `IP_B:1101`, e a hora da última chamada vinda do PC A (isso só acontece se o cabo/Wi-Fi e a 1101 estiverem abertos).

No Command Center do PC A o DR-001 fica **ONLINE**.

No PC B:

```powershell
netstat -an | findstr "1101"
```

`0.0.0.0:1101` em `LISTENING`.

## 4. Terceira máquina física (PC C)

Mesmo `--central` (sempre o IP do PC A). `--id` e `--hostname` deste PC C. `--listen 1101` (um drone por computador; a 1101 é local a essa máquina):

```bat
scripts\start-drone.bat --id DR-002 --base Matola --lat -25.9622 --lon 32.4589 --battery 76 --central 192.168.1.10:1099 --hostname 192.168.1.12 --listen 1101
```

## 5. Falhas entre PCs físicos

| O que vês | Causa nesta rede | Correcção |
|---|---|---|
| Ping entre A e B falha | Cabo, Wi-Fi de convidado, VPN, redes diferentes | Mesma LAN; perfil Privada; sem VPN |
| `CENTRAL INALCANÇÁVEL` no PC B | PC A ainda não subiu, IP do A errado, 1099 fechada no A | Central primeiro; `ping IP_A`; `allow-lan.bat` no **PC A** |
| `ConnectException` / `UnknownHost` | IP do A mudou (DHCP) | Novo `ipconfig` no PC A; actualizar `--central` |
| Frota vazia no PC A | `--central` no B não é o IP do A | O URL no cabeçalho do A = o `--central` do B |
| Join no A e logo **OFFLINE** | `--hostname` no B é `127.0.0.1`, ou 1101 fechada no B | `--hostname` = IP do **PC B**; `allow-lan.bat` no **PC B** |
| «Última chamada da central» no B não muda | O PC A não entra na 1101 do B | Firewall / `java.exe` no **PC B**; `--listen 1101` |
| Cabeçalho do A com `127.0.0.1` | A JVM do A anunciou loopback | Reiniciar o Central no A; usar o IPv4 da LAN |
| `already bound` no A | Já há um Command Center nesse PC | Fechar a janela antiga no A |

Desligar o Wi-Fi ou o cabo do PC B em missão é a falha física: o A marca timeout (~2 s + 1,5 s) e reatribui a outro drone **ONLINE**.
