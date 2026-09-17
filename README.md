# RescueNet

Sistema distribuído de coordenação de drones de emergência em **Java 26** e **Java RMI**. Cada drone corre numa JVM (e, na demo real, num PC) à parte. O Command Center detecta a queda de um nó por heartbeat/timeout e reatribui a missão.

> Quando um drone falha, a missão não pode falhar.

## Requisitos

- JDK 26 (`JAVA_HOME` a apontar para `C:\Program Files\Java\jdk-26.0.1`; o `mvnw.cmd` tenta descobrir sozinho em `C:\Program Files\Java\jdk-26*`)
- O projecto inclui `mvnw.cmd` / `mvnw` (não é preciso instalar Maven globalmente)
- Rede local para a demo em 4 PCs

Confirma o wrapper:

```bat
scripts\check-mvnw.bat
```

## Compilar

```bat
mvnw.cmd package
```

Isto gera `target/rescue-net-1.0.0.jar` e copia as dependências JavaFX para `target/lib`.

## Testes

| Script | O que corre |
|---|---|
| `scripts\test-unit.bat` | Testes unitários (algoritmo, DTOs, digital twin) — `mvnw test` |
| `scripts\test-e2e.bat` | Testes end-to-end com RMI real (join, despacho, failover) |
| `scripts\test-all.bat` | Unitários + e2e — `mvnw verify` |

```bat
scripts\test-unit.bat
scripts\test-e2e.bat
scripts\test-all.bat
```

Os e2e sobem um registry RMI em `127.0.0.1`, registam drones e verificam que a Central escolhe o melhor nó e reatribui a missão quando o drone alocado cai.

## Demo local (um PC, 4 processos)

```bat
scripts\start-local-demo.bat
```

Abre o Command Center e três drones:

| Nó | ID | Base | Bateria inicial |
|---|---|---|---|
| Central | Command Center | registry `:1099` | — |
| Drone 1 | DR-001 | Maputo | 90% |
| Drone 2 | DR-002 | Matola | 76% |
| Drone 3 | DR-003 | Marracuene | 95% |

1. Confirma os três drones **ONLINE / DISPONÍVEL**.
2. Cria uma ocorrência **Busca e salvamento / Zona A / Prioridade HIGH**.
3. O Central escolhe o melhor drone (bateria + distância euclidiana) e despacha a missão.
4. Fecha a janela (ou mata o processo) do drone alocado.
5. O Command Center marca timeout, mostra a faixa de failover e reatribui a missão ao próximo disponível.

Arranque manual:

```bat
scripts\start-central.bat
scripts\start-drone.bat --id DR-001 --base Maputo --lat -25.9692 --lon 32.5732 --battery 90 --central 127.0.0.1:1099 --hostname 127.0.0.1
```

## Demo em 4 PCs (LAN)

Todos os PCs na mesma rede. IPs de exemplo do enunciado:

| Papel | IP | Comando |
|---|---|---|
| Central | 192.168.1.10 | `scripts\start-central.bat --port 1099` |
| DR-001 | 192.168.1.11 | `scripts\start-drone.bat --id DR-001 --base Maputo --lat -25.9692 --lon 32.5732 --battery 90 --central 192.168.1.10:1099 --hostname 192.168.1.11` |
| DR-002 | 192.168.1.12 | `scripts\start-drone.bat --id DR-002 --base Matola --lat -25.9622 --lon 32.4589 --battery 76 --central 192.168.1.10:1099 --hostname 192.168.1.12` |
| DR-003 | 192.168.1.13 | `scripts\start-drone.bat --id DR-003 --base Marracuene --lat -25.7369 --lon 32.6744 --battery 95 --central 192.168.1.10:1099 --hostname 192.168.1.13` |

`--hostname` tem de ser o IP visível na LAN (`java.rmi.server.hostname`). Sem isso o stub aponta para localhost e o Central não consegue invocar o drone.

Simular falha física: desligar o Wi-Fi ou o cabo do PC do drone em missão. O heartbeat (~2 s, timeout 1,5 s) marca o nó OFFLINE e reatribui.

## Arquitectura RMI

O registry vive só no PC Central (`LocateRegistry.createRegistry(1099)`), com o nome `RescueNet`.

O RMI Registry recusa `bind`/`rebind` vindos de outro host. Por isso cada drone **exporta** o `DroneService` na sua JVM e chama `FleetDirectory.join` no Central, passando o stub. O Central guarda esses stubs e usa-os no heartbeat, na alocação e no failover.

```
Drone JVM  -- join(stub) -->  Central registry :1099 / RescueNet
Central     -- getSnapshot / assignMission -->  Drone JVM
```

## Módulos

```
rescuenet.common   contrato RMI e DTOs serializáveis
rescuenet.drone    DroneServer, digital twin (bateria e movimento)
rescuenet.central  registry, selecção, heartbeat, reatribuição
rescuenet.gui      Command Center JavaFX
```

## Maven (alternativo)

```bat
mvnw.cmd -v
mvnw.cmd test
mvnw.cmd verify
mvnw.cmd javafx:run
mvnw.cmd exec:java -Dexec.mainClass=rescuenet.drone.DroneServer -Dexec.args="--id DR-001 --base Maputo --lat -25.9692 --lon 32.5732 --battery 90 --central 127.0.0.1:1099 --hostname 127.0.0.1"
```
