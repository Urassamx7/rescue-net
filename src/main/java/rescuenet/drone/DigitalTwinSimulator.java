package rescuenet.drone;

import rescuenet.common.Coordinates;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.DroneState;
import rescuenet.common.Mission;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class DigitalTwinSimulator implements AutoCloseable {
    private static final long TICK_MS = 400;
    private static final double CRUISE_STEP = 0.0045;

    private final String droneId;
    private final Coordinates base;
    private final ScheduledExecutorService clock;
    private final AtomicBoolean running = new AtomicBoolean();

    private double battery;
    private DroneState state = DroneState.AVAILABLE;
    private Coordinates location;
    private Mission mission;
    private int lingerTicks;

    DigitalTwinSimulator(String droneId, Coordinates base, double batteryPercent) {
        this.droneId = droneId;
        this.base = base;
        this.location = base;
        this.battery = clamp(batteryPercent);
        this.clock = Executors.newSingleThreadScheduledExecutor(thread -> {
            Thread worker = new Thread(thread, "twin-" + droneId);
            worker.setDaemon(true);
            return worker;
        });
    }

    void start() {
        if (running.compareAndSet(false, true)) {
            clock.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        }
    }

    synchronized boolean assign(Mission incoming) {
        if (state != DroneState.AVAILABLE) {
            return false;
        }
        this.mission = incoming.copy();
        this.state = DroneState.ON_MISSION;
        this.lingerTicks = 0;
        log("missão %s aceite → %s".formatted(incoming.id(), incoming.zoneName()));
        return true;
    }

    synchronized void abort(String reason) {
        if (state == DroneState.AVAILABLE) {
            return;
        }
        log("aborto: %s".formatted(reason));
        this.mission = null;
        this.state = location.equals(base) ? DroneState.AVAILABLE : DroneState.RETURNING;
        this.lingerTicks = 0;
    }

    synchronized DroneSnapshot snapshot(String ip, String baseName) {
        return new DroneSnapshot(
                droneId,
                ip,
                baseName,
                Math.round(battery * 10.0) / 10.0,
                state,
                location,
                mission == null ? null : mission.id()
        );
    }

    synchronized DroneState state() {
        return state;
    }

    private int statusLogTicks;

    private synchronized void tick() {
        drainBattery();
        switch (state) {
            case ON_MISSION -> flyToObjective();
            case RETURNING -> flyHome();
            case AVAILABLE, OFFLINE -> {
            }
        }
        statusLogTicks++;
        if (statusLogTicks % 5 == 0) {
            log("bat=%s%% estado=%s pos=%s missão=%s".formatted(
                    String.format(Locale.US, "%.1f", battery),
                    state,
                    location,
                    mission == null ? "-" : mission.id()
            ));
        }
    }

    private void flyToObjective() {
        if (mission == null) {
            state = DroneState.RETURNING;
            return;
        }
        Coordinates target = mission.destination();
        if (!location.equals(target)) {
            location = location.stepTowards(target, CRUISE_STEP);
            return;
        }
        lingerTicks++;
        if (lingerTicks >= 8) {
            log("alvo %s atingido, regresso à base".formatted(mission.zoneName()));
            state = DroneState.RETURNING;
            lingerTicks = 0;
        }
    }

    private void flyHome() {
        if (!location.equals(base)) {
            location = location.stepTowards(base, CRUISE_STEP);
            return;
        }
        log("na base, disponível");
        mission = null;
        state = DroneState.AVAILABLE;
    }

    private void drainBattery() {
        double rate = switch (state) {
            case ON_MISSION -> 0.12;
            case RETURNING -> 0.08;
            case AVAILABLE -> 0.015;
            case OFFLINE -> 0;
        };
        battery = clamp(battery - rate);
    }

    private void log(String message) {
        System.out.printf("[%s] %s%n", droneId, message);
    }

    private static double clamp(double value) {
        return Math.max(5.0, Math.min(100.0, value));
    }

    @Override
    public void close() {
        running.set(false);
        clock.shutdownNow();
    }
}
