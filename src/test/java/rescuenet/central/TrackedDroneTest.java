package rescuenet.central;

import rescuenet.common.Coordinates;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.DroneState;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackedDroneTest {
    @Test
    void selectableOnlyWhenOnlineAndAvailable() {
        TrackedDrone drone = drone("DR-001", DroneState.AVAILABLE);
        assertTrue(drone.selectable());
        assertTrue(drone.online());

        drone.heartbeatOk(snapshot("DR-001", DroneState.ON_MISSION, "MIS-001"));
        assertFalse(drone.selectable());

        drone.markOffline();
        assertFalse(drone.online());
        assertFalse(drone.selectable());
        assertEquals(DroneState.OFFLINE, drone.snapshot().state());
    }

    @Test
    void refreshBringsDroneBackOnline() {
        TrackedDrone drone = drone("DR-002", DroneState.AVAILABLE);
        drone.markOffline();
        drone.refresh(null, snapshot("DR-002", DroneState.AVAILABLE, null));
        assertTrue(drone.online());
        assertTrue(drone.selectable());
    }

    private static TrackedDrone drone(String id, DroneState state) {
        return new TrackedDrone(id, null, snapshot(id, state, null));
    }

    private static DroneSnapshot snapshot(String id, DroneState state, String missionId) {
        return new DroneSnapshot(
                id,
                "127.0.0.1",
                "Maputo",
                90,
                state,
                new Coordinates(-25.97, 32.57),
                missionId
        );
    }
}
