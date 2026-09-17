package rescuenet.e2e;

import rescuenet.central.EmergencyServer;
import rescuenet.common.Coordinates;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.DroneState;
import rescuenet.common.FleetDirectory;
import rescuenet.drone.DroneServiceImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rescuenet.e2e.E2EHarness.await;
import static rescuenet.e2e.E2EHarness.directory;
import static rescuenet.e2e.E2EHarness.drone;
import static rescuenet.e2e.E2EHarness.droneState;
import static rescuenet.e2e.E2EHarness.fleetMember;
import static rescuenet.e2e.E2EHarness.shutdownQuietly;
import static rescuenet.e2e.E2EHarness.startCentral;
import static rescuenet.e2e.E2EHarness.stopQuietly;

@Timeout(20)
class FleetJoinE2E {
    private EmergencyServer server;
    private DroneServiceImpl first;
    private DroneServiceImpl second;

    @AfterEach
    void tearDown() {
        shutdownQuietly(first);
        shutdownQuietly(second);
        stopQuietly(server);
    }

    @Test
    void dronesAppearInTheCentralFleetAfterJoin() throws Exception {
        server = startCentral();
        first = drone("DR-001", "Maputo", new Coordinates(-25.9692, 32.5732), 90);
        second = drone("DR-002", "Matola", new Coordinates(-25.9622, 32.4589), 76);
        FleetDirectory directory = directory(server);
        directory.join(first.getSnapshot(), first);
        directory.join(second.getSnapshot(), second);

        assertEquals(2, server.fleetSnapshots().size());
        DroneSnapshot maputo = fleetMember(server, "DR-001");
        assertEquals("Maputo", maputo.baseName());
        assertEquals(DroneState.AVAILABLE, maputo.state());
        assertEquals("Matola", fleetMember(server, "DR-002").baseName());
    }

    @Test
    void leaveMarksTheDroneOfflineWithoutWaitingForHeartbeat() throws Exception {
        server = startCentral();
        first = drone("DR-001", "Maputo", new Coordinates(-25.9692, 32.5732), 90);
        FleetDirectory directory = directory(server);
        directory.join(first.getSnapshot(), first);
        assertEquals(DroneState.AVAILABLE, fleetMember(server, "DR-001").state());

        directory.leave("DR-001");
        await("DR-001 deveria ficar OFFLINE após leave()",
                () -> droneState(server, "DR-001", DroneState.OFFLINE));
        assertTrue(server.alerts().stream().anyMatch(alert -> alert.contains("FALHA DR-001")));
    }
}
