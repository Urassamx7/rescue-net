package rescuenet.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface DroneService extends Remote {
    String getId() throws RemoteException;

    boolean ping() throws RemoteException;

    DroneSnapshot getSnapshot() throws RemoteException;

    boolean assignMission(Mission mission) throws RemoteException;

    void abortMission(String reason) throws RemoteException;
}
