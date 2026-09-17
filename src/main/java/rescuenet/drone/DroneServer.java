package rescuenet.drone;

import rescuenet.common.CliArgs;
import rescuenet.common.Coordinates;
import rescuenet.common.FleetDirectory;
import rescuenet.common.NetworkAddresses;

import java.rmi.Naming;

public final class DroneServer {
    private DroneServer() {
    }

    public static void main(String[] args) throws Exception {
        CliArgs cli = CliArgs.parse(args);
        String id = cli.req("id");
        String baseName = cli.opt("base", "Base");
        double lat = cli.reqDouble("lat");
        double lon = cli.reqDouble("lon");
        double battery = cli.optDouble("battery", 90);
        String central = cli.opt("central", "127.0.0.1:1099");
        String hostname = cli.opt("hostname", NetworkAddresses.localHostName());

        System.setProperty("java.rmi.server.hostname", hostname);

        String[] centralParts = central.split(":");
        String centralHost = centralParts[0];
        int centralPort = centralParts.length > 1 ? Integer.parseInt(centralParts[1]) : 1099;
        String directoryUrl = NetworkAddresses.rmiUrl(centralHost, centralPort, FleetDirectory.BIND_NAME);

        DroneServiceImpl drone = new DroneServiceImpl(
                id,
                hostname,
                baseName,
                new Coordinates(lat, lon),
                battery
        );

        System.out.printf("[%s] a ligar ao Command Center em %s (hostname RMI=%s)%n", id, directoryUrl, hostname);

        FleetDirectory directory = waitForDirectory(directoryUrl);
        directory.join(drone.getSnapshot(), drone);
        System.out.printf("[%s] registado. Digital twin activo em %s.%n", id, baseName);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                directory.leave(id);
            } catch (Exception ignored) {
                // central already down
            }
            drone.shutdown();
            System.out.printf("[%s] offline%n", id);
        }));

        Thread.currentThread().join();
    }

    private static FleetDirectory waitForDirectory(String url) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                return (FleetDirectory) Naming.lookup(url);
            } catch (Exception error) {
                last = error;
                System.out.println("[drone] registry da Central ainda não disponível, a repetir...");
                Thread.sleep(1_000);
            }
        }
        throw new IllegalStateException("Não foi possível contactar a Central em " + url, last);
    }
}
