package rescuenet.central;

import rescuenet.common.Coordinates;
import rescuenet.common.DroneService;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.DroneState;
import rescuenet.common.FleetDirectory;
import rescuenet.common.Mission;
import rescuenet.common.MissionState;
import rescuenet.common.MissionType;
import rescuenet.common.NetworkAddresses;
import rescuenet.common.Priority;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class EmergencyServer extends UnicastRemoteObject implements FleetDirectory {
    private final int port;
    private final Map<String, TrackedDrone> fleet = new ConcurrentHashMap<>();
    private final Map<String, Mission> missions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<EmergencyListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> alerts = new CopyOnWriteArrayList<>();
    private final AtomicInteger missionSeq = new AtomicInteger();
    private final HeartbeatMonitor heartbeat;
    private Registry registry;

    private EmergencyServer(int port) throws RemoteException {
        super();
        this.port = port;
        this.heartbeat = new HeartbeatMonitor(this);
    }

    public static EmergencyServer start(int port) throws RemoteException {
        String hostname = System.getProperty("java.rmi.server.hostname");
        if (hostname == null || hostname.isBlank()) {
            System.setProperty("java.rmi.server.hostname", NetworkAddresses.localHostName());
        }
        EmergencyServer server = new EmergencyServer(port);
        server.registry = LocateRegistry.createRegistry(port);
        server.registry.rebind(FleetDirectory.BIND_NAME, server);
        server.heartbeat.start();
        System.out.printf(
                "[central] RescueNet no ar — registry rmi://%s:%d/%s%n",
                NetworkAddresses.localHostName(),
                port,
                FleetDirectory.BIND_NAME
        );
        return server;
    }

    public int port() {
        return port;
    }

    public void addListener(EmergencyListener listener) {
        listeners.add(listener);
    }

    public void removeListener(EmergencyListener listener) {
        listeners.remove(listener);
    }

    @Override
    public synchronized void join(DroneSnapshot snapshot, DroneService drone) {
        TrackedDrone tracked = fleet.compute(snapshot.id(), (id, existing) -> {
            if (existing == null) {
                return new TrackedDrone(id, drone, snapshot);
            }
            existing.refresh(drone, snapshot);
            return existing;
        });
        String message = "Drone %s ligado (%s, bat %.0f%%)".formatted(
                tracked.id(),
                snapshot.baseName(),
                snapshot.batteryPercent()
        );
        pushAlert(message);
        System.out.println("[central] " + message);
        notifyFleet();
    }

    @Override
    public synchronized void leave(String droneId) {
        onHeartbeatFailed(droneId, "leave");
    }

    List<TrackedDrone> trackedDrones() {
        return List.copyOf(fleet.values());
    }

    public synchronized List<DroneSnapshot> fleetSnapshots() {
        List<DroneSnapshot> snapshots = new ArrayList<>();
        for (TrackedDrone drone : fleet.values()) {
            snapshots.add(drone.snapshot());
        }
        snapshots.sort((a, b) -> a.id().compareTo(b.id()));
        return snapshots;
    }

    public synchronized List<Mission> missions() {
        List<Mission> copy = new ArrayList<>();
        for (Mission mission : missions.values()) {
            copy.add(mission.copy());
        }
        copy.sort((a, b) -> a.id().compareTo(b.id()));
        return copy;
    }

    public List<String> alerts() {
        return List.copyOf(alerts);
    }

    public synchronized Mission createEmergency(MissionType type, String zoneName, Coordinates destination, Priority priority) {
        String id = "MIS-%03d".formatted(missionSeq.incrementAndGet());
        Mission mission = new Mission(id, type, zoneName, destination, priority);
        missions.put(id, mission);
        String created = "Ocorrência %s criada — %s em %s (%s)".formatted(
                id,
                type.name().replace('_', ' '),
                zoneName,
                priority
        );
        pushAlert(created);
        dispatch(mission);
        notifyMission(mission);
        return mission.copy();
    }

    synchronized void onHeartbeatOk(String droneId, DroneSnapshot snapshot) {
        TrackedDrone drone = fleet.get(droneId);
        if (drone == null) {
            return;
        }
        boolean wasOffline = !drone.online();
        drone.heartbeatOk(snapshot);
        if (wasOffline) {
            pushAlert("Drone %s voltou ONLINE".formatted(droneId));
            recoverStaleAssignment(drone, snapshot);
        }
        reconcileMissionProgress(snapshot);
        notifyFleet();
    }

    synchronized void onHeartbeatFailed(String droneId, String reason) {
        TrackedDrone drone = fleet.get(droneId);
        if (drone == null) {
            return;
        }
        boolean alreadyOffline = !drone.online();
        DroneSnapshot offline = drone.markOffline();
        if (alreadyOffline) {
            return;
        }
        String alert = "FALHA %s (%s) — nó inalcançável".formatted(droneId, reason);
        pushAlert(alert);
        System.out.println("[central] " + alert);
        reassignAfterFailure(droneId, offline.missionId());
        notifyFleet();
    }

    private void dispatch(Mission mission) {
        Optional<TrackedDrone> chosen = SelectionAlgorithm.select(fleet.values(), mission.destination());
        if (chosen.isEmpty()) {
            mission.waiting("Sem drones AVAILABLE/ONLINE");
            pushAlert("%s à espera de um drone disponível".formatted(mission.id()));
            return;
        }
        assignTo(mission, chosen.get(), "Alocado por bateria e proximidade");
    }

    private void assignTo(Mission mission, TrackedDrone drone, String event) {
        try {
            boolean accepted = drone.stub().assignMission(mission.copy());
            if (!accepted) {
                mission.waiting("Drone %s recusou a missão".formatted(drone.id()));
                pushAlert("%s recusou %s".formatted(drone.id(), mission.id()));
                return;
            }
            mission.assign(drone.id(), event);
            mission.markInProgress();
            drone.heartbeatOk(new DroneSnapshot(
                    drone.snapshot().id(),
                    drone.snapshot().ip(),
                    drone.snapshot().baseName(),
                    drone.snapshot().batteryPercent(),
                    DroneState.ON_MISSION,
                    drone.snapshot().location(),
                    mission.id()
            ));
            pushAlert("%s → %s (%s)".formatted(mission.id(), drone.id(), event));
            notifyMission(mission);
        } catch (RemoteException error) {
            drone.markOffline();
            mission.waiting("Falha RMI ao alocar " + drone.id());
            pushAlert("Não foi possível alocar %s a %s".formatted(mission.id(), drone.id()));
        }
    }

    private void reassignAfterFailure(String failedDroneId, String missionId) {
        if (missionId == null) {
            for (Mission mission : missions.values()) {
                if (failedDroneId.equals(mission.assignedDroneId()) && mission.isActive()) {
                    missionId = mission.id();
                    break;
                }
            }
        }
        if (missionId == null) {
            return;
        }
        Mission mission = missions.get(missionId);
        if (mission == null || !mission.isActive()) {
            return;
        }
        Optional<TrackedDrone> replacement = SelectionAlgorithm.select(fleet.values(), mission.destination());
        if (replacement.isEmpty()) {
            mission.waiting("Aguardando reatribuição após falha de " + failedDroneId);
            pushAlert("%s sem substituto após queda de %s".formatted(mission.id(), failedDroneId));
            notifyMission(mission);
            return;
        }
        TrackedDrone next = replacement.get();
        assignTo(mission, next, "Reatribuição automática após timeout de " + failedDroneId);
        listeners.forEach(listener -> listener.onFailover(mission.copy(), failedDroneId, next.id()));
    }

    private void recoverStaleAssignment(TrackedDrone drone, DroneSnapshot snapshot) {
        String missionId = snapshot.missionId();
        if (missionId == null) {
            return;
        }
        Mission mission = missions.get(missionId);
        if (mission != null && mission.isActive() && !drone.id().equals(mission.assignedDroneId())) {
            try {
                drone.stub().abortMission("missão já reatribuída");
            } catch (RemoteException ignored) {
                // next heartbeat will retry conceptually
            }
        }
    }

    private void reconcileMissionProgress(DroneSnapshot snapshot) {
        if (snapshot.missionId() != null) {
            return;
        }
        for (Mission mission : missions.values()) {
            if (snapshot.id().equals(mission.assignedDroneId())
                    && mission.state() == MissionState.IN_PROGRESS
                    && snapshot.state() == DroneState.AVAILABLE) {
                mission.complete("Drone %s regressou à base".formatted(snapshot.id()));
                notifyMission(mission);
                pushAlert("%s concluída por %s".formatted(mission.id(), snapshot.id()));
            }
        }
    }

    private void pushAlert(String message) {
        alerts.add(message);
        if (alerts.size() > 40) {
            alerts.remove(0);
        }
        listeners.forEach(listener -> listener.onAlert(message));
    }

    private void notifyFleet() {
        listeners.forEach(EmergencyListener::onFleetUpdated);
    }

    private void notifyMission(Mission mission) {
        listeners.forEach(listener -> listener.onMissionUpdated(mission.copy()));
    }

    public void stop() {
        heartbeat.close();
        try {
            if (registry != null) {
                registry.unbind(FleetDirectory.BIND_NAME);
            }
        } catch (Exception ignored) {
            // shutting down
        }
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception ignored) {
            // already unexported
        }
        try {
            if (registry != null) {
                UnicastRemoteObject.unexportObject(registry, true);
            }
        } catch (Exception ignored) {
            // registry already down
        }
    }
}
