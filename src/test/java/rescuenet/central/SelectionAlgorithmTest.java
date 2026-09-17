package rescuenet.central;

import rescuenet.common.Coordinates;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.DroneState;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionAlgorithmTest {
    @Test
    void prefersHigherBatteryWhenDistancesAreEqual() {
        TrackedDrone weak = drone("DR-001", 76, new Coordinates(-25.96, 32.46));
        TrackedDrone strong = drone("DR-002", 95, new Coordinates(-25.96, 32.46));
        Optional<TrackedDrone> chosen = SelectionAlgorithm.select(
                List.of(weak, strong),
                new Coordinates(-25.90, 32.55)
        );
        assertTrue(chosen.isPresent());
        assertEquals("DR-002", chosen.get().id());
    }

    @Test
    void prefersCloserDroneWhenBatteriesAreSimilar() {
        TrackedDrone far = drone("DR-001", 90, new Coordinates(-25.74, 32.67));
        TrackedDrone near = drone("DR-002", 88, new Coordinates(-25.97, 32.57));
        Optional<TrackedDrone> chosen = SelectionAlgorithm.select(
                List.of(far, near),
                new Coordinates(-25.97, 32.58)
        );
        assertTrue(chosen.isPresent());
        assertEquals("DR-002", chosen.get().id());
    }

    @Test
    void ignoresOfflineAndBusyDrones() {
        TrackedDrone offline = drone("DR-001", 99, new Coordinates(-25.97, 32.57));
        offline.markOffline();
        TrackedDrone busy = drone("DR-002", 97, new Coordinates(-25.97, 32.57));
        busy.heartbeatOk(new DroneSnapshot(
                "DR-002",
                "127.0.0.1",
                "Maputo",
                97,
                DroneState.ON_MISSION,
                new Coordinates(-25.97, 32.57),
                "MIS-001"
        ));
        TrackedDrone free = drone("DR-003", 70, new Coordinates(-25.96, 32.46));
        Optional<TrackedDrone> chosen = SelectionAlgorithm.select(
                List.of(offline, busy, free),
                new Coordinates(-25.97, 32.58)
        );
        assertTrue(chosen.isPresent());
        assertEquals("DR-003", chosen.get().id());
    }

    @Test
    void returnsEmptyWhenNobodyIsSelectable() {
        TrackedDrone offline = drone("DR-001", 99, new Coordinates(-25.97, 32.57));
        offline.markOffline();
        assertTrue(SelectionAlgorithm.select(List.of(offline), new Coordinates(-25.97, 32.58)).isEmpty());
        assertTrue(SelectionAlgorithm.select(List.of(), new Coordinates(-25.97, 32.58)).isEmpty());
    }

    private static TrackedDrone drone(String id, double battery, Coordinates location) {
        DroneSnapshot snapshot = new DroneSnapshot(id, "127.0.0.1", "base", battery, DroneState.AVAILABLE, location, null);
        return new TrackedDrone(id, null, snapshot);
    }
}
