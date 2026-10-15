package rescuenet.drone;

import rescuenet.common.CliArgs;
import rescuenet.common.Coordinates;
import rescuenet.common.FleetDirectory;
import rescuenet.common.NetworkAddresses;

import java.rmi.ConnectException;
import java.rmi.Naming;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        int listen = cli.optInt("listen", 1101);

        System.setProperty("java.rmi.server.hostname", hostname);

        String directoryUrl = directoryUrl(central);

        DroneServiceImpl drone = new DroneServiceImpl(
                id,
                hostname,
                baseName,
                new Coordinates(lat, lon),
                battery,
                listen
        );

        System.out.printf(
                "[%s] a ligar ao Command Center em %s (hostname RMI=%s listen=%d)%n",
                id,
                directoryUrl,
                hostname,
                listen
        );

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

    public static String directoryUrl(String central) {
        String[] centralParts = central.split(":");
        String centralHost = centralParts[0];
        int centralPort = centralParts.length > 1 ? Integer.parseInt(centralParts[1]) : 1099;
        return NetworkAddresses.rmiUrl(centralHost, centralPort, FleetDirectory.BIND_NAME);
    }

    public static FleetDirectory lookup(String url) throws Exception {
        return (FleetDirectory) Naming.lookup(url);
    }

    public static FleetDirectory lookup(String url, long timeoutMs) throws Exception {
        return timed("lookup " + url, timeoutMs, () -> lookup(url));
    }

    public static <T> T timed(String label, long timeoutMs, Callable<T> call) throws Exception {
        var pool = Executors.newSingleThreadExecutor(task -> {
            Thread worker = new Thread(task, "rmi-call");
            worker.setDaemon(true);
            return worker;
        });
        Future<T> future = pool.submit(call);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            throw new ConnectException("Timeout (" + timeoutMs + "ms) em " + label);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new Exception(cause);
        } finally {
            pool.shutdownNow();
        }
    }

    static FleetDirectory waitForDirectory(String url) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                return lookup(url);
            } catch (Exception error) {
                last = error;
                System.out.println("[drone] registry da Central ainda não disponível, a repetir...");
                Thread.sleep(1_000);
            }
        }
        throw new IllegalStateException("Não foi possível contactar a Central em " + url, last);
    }
}
