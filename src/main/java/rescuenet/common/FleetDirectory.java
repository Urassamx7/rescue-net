package rescuenet.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Ponto de entrada no registry da Central. O RMI Registry só permite bind/rebind
 * a partir de localhost, por isso os drones exportam o stub localmente e
 * registam-se aqui via invocação remota.
 */
public interface FleetDirectory extends Remote {
    String BIND_NAME = "RescueNet";

    void join(DroneSnapshot snapshot, DroneService drone) throws RemoteException;

    void leave(String droneId) throws RemoteException;
}
