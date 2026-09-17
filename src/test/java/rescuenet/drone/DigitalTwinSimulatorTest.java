package rescuenet.drone;

import rescuenet.common.Coordinates;
import rescuenet.common.DroneState;
import rescuenet.common.Mission;
import rescuenet.common.MissionType;
import rescuenet.common.Priority;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigitalTwinSimulatorTest {
    private DigitalTwinSimulator twin;

    @AfterEach
    void tearDown() {
        if (twin != null) {
            twin.close();
        }
    }

    @Test
    void clampsBatteryAndStartsAvailable() {
        twin = new DigitalTwinSimulator("DR-001", new Coordinates(-25.97, 32.57), 150);
        var snapshot = twin.snapshot("127.0.0.1", "Maputo");
        assertEquals(100.0, snapshot.batteryPercent());
        assertEquals(DroneState.AVAILABLE, snapshot.state());
        assertNull(snapshot.missionId());
        assertEquals("DR-001", snapshot.id());
    }

    @Test
    void assignRejectsSecondMissionUntilAbort() {
        twin = new DigitalTwinSimulator("DR-002", new Coordinates(-25.97, 32.57), 90);
        Mission first = mission("MIS-001");
        Mission second = mission("MIS-002");

        assertTrue(twin.assign(first));
        assertEquals(DroneState.ON_MISSION, twin.state());
        assertEquals("MIS-001", twin.snapshot("127.0.0.1", "Maputo").missionId());
        assertFalse(twin.assign(second));

        twin.abort("teste");
        assertEquals(DroneState.AVAILABLE, twin.state());
        assertTrue(twin.assign(second));
        assertEquals("MIS-002", twin.snapshot("127.0.0.1", "Maputo").missionId());
    }

    private static Mission mission(String id) {
        return new Mission(
                id,
                MissionType.RECONHECIMENTO,
                "Zona B",
                new Coordinates(-25.93, 32.56),
                Priority.MEDIUM
        );
    }
}
