package rescuenet.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkAddressesTest {
    @Test
    void buildsRmiUrl() {
        assertEquals("rmi://127.0.0.1:1099/RescueNet", NetworkAddresses.rmiUrl("127.0.0.1", 1099, "RescueNet"));
    }

    @Test
    void asOfflineKeepsIdentityAndMarksState() {
        DroneSnapshot online = new DroneSnapshot(
                "DR-001",
                "127.0.0.1",
                "Maputo",
                90,
                DroneState.ON_MISSION,
                new Coordinates(-25.97, 32.57),
                "MIS-001"
        );
        DroneSnapshot offline = online.asOffline();
        assertEquals(DroneState.OFFLINE, offline.state());
        assertEquals("DR-001", offline.id());
        assertEquals("MIS-001", offline.missionId());
        assertEquals(90, offline.batteryPercent());
    }
}
