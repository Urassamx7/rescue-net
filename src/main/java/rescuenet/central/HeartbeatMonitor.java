package rescuenet.central;

import rescuenet.common.DroneSnapshot;

import java.rmi.RemoteException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class HeartbeatMonitor implements AutoCloseable {
    static final long INTERVAL_MS = 2_000;
    static final long TIMEOUT_MS = 1_500;

    private final EmergencyServer server;
    private final ExecutorService calls = Executors.newCachedThreadPool(thread -> {
        Thread worker = new Thread(thread, "heartbeat-call");
        worker.setDaemon(true);
        return worker;
    });
    private final java.util.concurrent.ScheduledExecutorService clock =
            Executors.newSingleThreadScheduledExecutor(thread -> {
                Thread worker = new Thread(thread, "heartbeat");
                worker.setDaemon(true);
                return worker;
            });
    private final CopyOnWriteArrayList<String> probing = new CopyOnWriteArrayList<>();

    HeartbeatMonitor(EmergencyServer server) {
        this.server = server;
    }

    void start() {
        clock.scheduleAtFixedRate(this::pulse, INTERVAL_MS, INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void pulse() {
        for (TrackedDrone drone : server.trackedDrones()) {
            calls.execute(() -> probe(drone));
        }
    }

    private void probe(TrackedDrone drone) {
        if (!probing.addIfAbsent(drone.id())) {
            return;
        }
        Future<DroneSnapshot> future = calls.submit(() -> drone.stub().getSnapshot());
        try {
            DroneSnapshot snapshot = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            server.onHeartbeatOk(drone.id(), snapshot);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            server.onHeartbeatFailed(drone.id(), "timeout " + TIMEOUT_MS + "ms");
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            String detail = cause instanceof RemoteException ? cause.getClass().getSimpleName() : cause.getMessage();
            server.onHeartbeatFailed(drone.id(), detail == null ? "unreachable" : detail);
        } finally {
            probing.remove(drone.id());
        }
    }

    @Override
    public void close() {
        clock.shutdownNow();
        calls.shutdownNow();
    }
}
