package rescuenet.drone;

import rescuenet.common.Coordinates;
import rescuenet.common.DroneService;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.Mission;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class DroneServiceImpl extends UnicastRemoteObject implements DroneService {
    private final String id;
    private final String ip;
    private final String baseName;
    private final int listenPort;
    private final DigitalTwinSimulator twin;
    private final AtomicReference<Instant> lastRemoteCall = new AtomicReference<>();

    public DroneServiceImpl(String id, String ip, String baseName, Coordinates base, double battery)
            throws RemoteException {
        this(id, ip, baseName, base, battery, 0);
    }

    public DroneServiceImpl(String id, String ip, String baseName, Coordinates base, double battery, int listenPort)
            throws RemoteException {
        super(listenPort);
        this.id = id;
        this.ip = ip;
        this.baseName = baseName;
        this.listenPort = listenPort;
        this.twin = new DigitalTwinSimulator(id, base, battery);
        this.twin.start();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean ping() {
        return true;
    }

    @Override
    public DroneSnapshot getSnapshot() {
        touchRemote();
        return twin.snapshot(ip, baseName);
    }

    @Override
    public boolean assignMission(Mission mission) {
        touchRemote();
        return twin.assign(mission);
    }

    @Override
    public void abortMission(String reason) {
        touchRemote();
        twin.abort(reason);
    }

    public DroneSnapshot localSnapshot() {
        return twin.snapshot(ip, baseName);
    }

    public Mission currentMission() {
        return twin.currentMission();
    }

    public List<String> recentEvents() {
        return twin.recentEvents();
    }

    public Instant lastRemoteCall() {
        return lastRemoteCall.get();
    }

    public int listenPort() {
        return listenPort;
    }

    public String endpoint() {
        return listenPort == 0 ? ip : ip + ":" + listenPort;
    }

    public void shutdown() {
        twin.close();
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception ignored) {
            // already unexported
        }
    }

    private void touchRemote() {
        lastRemoteCall.set(Instant.now());
    }
}
