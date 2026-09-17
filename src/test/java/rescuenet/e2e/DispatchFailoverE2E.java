package rescuenet.e2e;

import rescuenet.central.EmergencyServer;
import rescuenet.common.Coordinates;
import rescuenet.common.DroneState;
import rescuenet.common.FleetDirectory;
import rescuenet.common.Mission;
import rescuenet.common.MissionState;
import rescuenet.common.MissionType;
import rescuenet.common.Priority;
import rescuenet.drone.DroneServiceImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rescuenet.e2e.E2EHarness.anyAlertContains;
import static rescuenet.e2e.E2EHarness.await;
import static rescuenet.e2e.E2EHarness.directory;
import static rescuenet.e2e.E2EHarness.drone;
import static rescuenet.e2e.E2EHarness.droneState;
import static rescuenet.e2e.E2EHarness.fleetMember;
import static rescuenet.e2e.E2EHarness.mission;
import static rescuenet.e2e.E2EHarness.missionAssignedTo;
import static rescuenet.e2e.E2EHarness.shutdownQuietly;
import static rescuenet.e2e.E2EHarness.startCentral;
import static rescuenet.e2e.E2EHarness.stopQuietly;

@Timeout(25)
class DispatchFailoverE2E {
    private static final Coordinates MAPUTO = new Coordinates(-25.9692, 32.5732);
    private static final Coordinates MARRACUENE = new Coordinates(-25.7369, 32.6744);
    private static final Coordinates ZONA_A = new Coordinates(-25.9300, 32.5600);

    private EmergencyServer server;
    private DroneServiceImpl stronger;
    private DroneServiceImpl weaker;

    @AfterEach
    void tearDown() {
        shutdownQuietly(stronger);
        shutdownQuietly(weaker);
        stopQuietly(server);
    }

    @Test
    void assignsClosestHighBatteryDroneThenFailsOverWhenItDies() throws Exception {
        server = startCentral();
        stronger = drone("DR-001", "Maputo", MAPUTO, 90);
        weaker = drone("DR-003", "Marracuene", MARRACUENE, 70);
        FleetDirectory directory = directory(server);
        directory.join(stronger.getSnapshot(), stronger);
        directory.join(weaker.getSnapshot(), weaker);

        Mission created = server.createEmergency(
                MissionType.BUSCA_E_SALVAMENTO,
                "Zona A",
                ZONA_A,
                Priority.HIGH
        );
        assertEquals("DR-001", created.assignedDroneId());
        assertEquals(MissionState.IN_PROGRESS, created.state());
        assertEquals("MIS-001", created.id());

        stronger.shutdown();
        stronger = null;

        await("DR-001 deveria ficar OFFLINE após a queda",
                () -> droneState(server, "DR-001", DroneState.OFFLINE));
        await("a missão deveria ser reatribuída a DR-003",
                () -> missionAssignedTo(server, created.id(), "DR-003"));

        Mission updated = mission(server, created.id());
        assertEquals("DR-003", updated.assignedDroneId());
        assertTrue(anyAlertContains(server, "FALHA DR-001"));
        assertTrue(anyAlertContains(server, "Reatribuição") || "DR-003".equals(updated.assignedDroneId()));
    }

    @Test
    void secondEmergencyGoesToTheRemainingAvailableDrone() throws Exception {
        server = startCentral();
        stronger = drone("DR-001", "Maputo", MAPUTO, 90);
        weaker = drone("DR-002", "Matola", new Coordinates(-25.9622, 32.4589), 76);
        FleetDirectory directory = directory(server);
        directory.join(stronger.getSnapshot(), stronger);
        directory.join(weaker.getSnapshot(), weaker);

        Mission first = server.createEmergency(
                MissionType.BUSCA_E_SALVAMENTO,
                "Zona A",
                ZONA_A,
                Priority.HIGH
        );
        assertEquals("DR-001", first.assignedDroneId());

        Mission second = server.createEmergency(
                MissionType.RECONHECIMENTO,
                "Zona B",
                new Coordinates(-25.96, 32.50),
                Priority.MEDIUM
        );
        assertEquals("DR-002", second.assignedDroneId());
        assertEquals(MissionState.IN_PROGRESS, second.state());
        assertEquals(DroneState.ON_MISSION, fleetMember(server, "DR-001").state());
        assertEquals(DroneState.ON_MISSION, fleetMember(server, "DR-002").state());
    }

    @Test
    void emergencyWaitsWhenTheFleetIsEmpty() throws Exception {
        server = startCentral();
        Mission pending = server.createEmergency(
                MissionType.ENTREGA_EMERGENCIA,
                "Zona C",
                ZONA_A,
                Priority.LOW
        );
        assertEquals(MissionState.PENDING, pending.state());
        assertNull(pending.assignedDroneId());
        assertTrue(anyAlertContains(server, "à espera de um drone disponível"));
    }
}
