package rescuenet.drone;

import rescuenet.common.Coordinates;
import rescuenet.common.DroneService;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.Mission;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public final class DroneServiceImpl extends UnicastRemoteObject implements DroneService {
    private final String id;
    private final String ip;
    private final String baseName;
    private final DigitalTwinSimulator twin;

    public DroneServiceImpl(String id, String ip, String baseName, Coordinates base, double battery)
            throws RemoteException {
        super();
        this.id = id;
        this.ip = ip;
        this.baseName = baseName;
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
        return twin.snapshot(ip, baseName);
    }

    @Override
    public boolean assignMission(Mission mission) {
        return twin.assign(mission);
    }

    @Override
    public void abortMission(String reason) {
        twin.abort(reason);
    }

    public void shutdown() {
        twin.close();
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception ignored) {
            // already unexported
        }
    }
}
