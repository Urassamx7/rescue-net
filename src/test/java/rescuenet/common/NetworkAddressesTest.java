package rescuenet.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkAddressesTest {
    @Test
    void buildsRmiUrl() {
        assertEquals("rmi://127.0.0.1:1099/RescueNet", NetworkAddresses.rmiUrl("127.0.0.1", 1099, "RescueNet"));
    }

    @Test
    void skipsVmwareAndPrefersWifi() {
        assertTrue(NetworkAddresses.looksVirtual("VMware Network Adapter VMnet1"));
        assertTrue(NetworkAddresses.looksVirtual("VMware Network Adapter VMnet8"));
        assertTrue(NetworkAddresses.looksVirtual("vEthernet (WSL)"));
        assertFalse(NetworkAddresses.looksVirtual("Wireless LAN adapter Wi-Fi"));
        assertTrue(NetworkAddresses.looksPreferred("Wireless LAN adapter Wi-Fi"));
        assertFalse(NetworkAddresses.looksPreferred("Ethernet adapter Ethernet"));
    }

    @Test
    void readsInterfaceIpFromWindowsDefaultRoute() {
        String routePrint = """
                ===========================================================================
                Active Routes:
                Network Destination        Netmask          Gateway       Interface  Metric
                          0.0.0.0          0.0.0.0     192.168.17.164  192.168.17.204     35
                        127.0.0.0        255.0.0.0         On-link         127.0.0.1    331
                """;
        assertEquals(List.of("192.168.17.204"), NetworkAddresses.defaultRouteInterfaceIps(routePrint));
    }

    @Test
    void announcedHostUsesRmiProperty() {
        String previous = System.getProperty("java.rmi.server.hostname");
        try {
            System.setProperty("java.rmi.server.hostname", "192.168.17.204");
            assertEquals("192.168.17.204", NetworkAddresses.announcedHost());
        } finally {
            if (previous == null) {
                System.clearProperty("java.rmi.server.hostname");
            } else {
                System.setProperty("java.rmi.server.hostname", previous);
            }
        }
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
