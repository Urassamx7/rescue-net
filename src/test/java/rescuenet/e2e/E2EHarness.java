package rescuenet.e2e;

import rescuenet.central.EmergencyServer;
import rescuenet.common.Coordinates;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.DroneState;
import rescuenet.common.FleetDirectory;
import rescuenet.common.Mission;
import rescuenet.common.MissionState;
import rescuenet.drone.DroneServiceImpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

final class E2EHarness {
    private E2EHarness() {
    }

    static EmergencyServer startCentral() throws RemoteException {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        RemoteException last = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            int port = freePort();
            try {
                return EmergencyServer.start(port);
            } catch (RemoteException error) {
                last = error;
            }
        }
        throw new RemoteException("Sem porta livre para o registry RMI", last);
    }

    static FleetDirectory directory(EmergencyServer server) throws Exception {
        return (FleetDirectory) Naming.lookup("rmi://127.0.0.1:" + server.port() + "/RescueNet");
    }

    static DroneServiceImpl drone(String id, String base, Coordinates location, double battery) throws RemoteException {
        return new DroneServiceImpl(id, "127.0.0.1", base, location, battery);
    }

    static Mission mission(EmergencyServer server, String id) {
        return server.missions().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missão não encontrada: " + id));
    }

    static DroneSnapshot fleetMember(EmergencyServer server, String droneId) {
        return server.fleetSnapshots().stream()
                .filter(item -> item.id().equals(droneId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Drone não encontrado: " + droneId));
    }

    static void await(String message, BooleanSupplier condition) {
        await(message, Duration.ofSeconds(12), condition);
    }

    static void await(String message, Duration timeout, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(message, interrupted);
            }
        }
        throw new AssertionError(message);
    }

    static void shutdownQuietly(DroneServiceImpl drone) {
        if (drone != null) {
            drone.shutdown();
        }
    }

    static void stopQuietly(EmergencyServer server) {
        if (server != null) {
            server.stop();
        }
    }

    static boolean anyAlertContains(EmergencyServer server, String fragment) {
        List<String> alerts = server.alerts();
        for (String alert : alerts) {
            if (alert.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    static boolean droneState(EmergencyServer server, String droneId, DroneState expected) {
        return fleetMember(server, droneId).state() == expected;
    }

    static boolean missionAssignedTo(EmergencyServer server, String missionId, String droneId) {
        Mission mission = mission(server, missionId);
        return droneId.equals(mission.assignedDroneId()) && mission.state() == MissionState.IN_PROGRESS;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException error) {
            throw new IllegalStateException("Não foi possível obter uma porta livre", error);
        }
    }
}
