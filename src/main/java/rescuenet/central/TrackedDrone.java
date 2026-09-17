package rescuenet.central;

import rescuenet.common.DroneService;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.DroneState;

import java.time.Instant;

final class TrackedDrone {
    private final String id;
    private volatile DroneService stub;
    private volatile DroneSnapshot snapshot;
    private volatile boolean online;
    private volatile Instant lastSeen = Instant.EPOCH;

    TrackedDrone(String id, DroneService stub, DroneSnapshot snapshot) {
        this.id = id;
        this.stub = stub;
        this.snapshot = snapshot;
        this.online = true;
        this.lastSeen = Instant.now();
    }

    String id() {
        return id;
    }

    DroneService stub() {
        return stub;
    }

    DroneSnapshot snapshot() {
        return snapshot;
    }

    boolean online() {
        return online;
    }

    Instant lastSeen() {
        return lastSeen;
    }

    synchronized void refresh(DroneService newStub, DroneSnapshot fresh) {
        this.stub = newStub;
        this.snapshot = fresh;
        this.online = true;
        this.lastSeen = Instant.now();
    }

    synchronized void heartbeatOk(DroneSnapshot fresh) {
        this.snapshot = fresh;
        this.online = true;
        this.lastSeen = Instant.now();
    }

    synchronized DroneSnapshot markOffline() {
        this.online = false;
        this.snapshot = snapshot.asOffline();
        return snapshot;
    }

    boolean selectable() {
        return online && snapshot.state() == DroneState.AVAILABLE;
    }
}
