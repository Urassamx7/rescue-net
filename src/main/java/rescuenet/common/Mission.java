package rescuenet.common;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public final class Mission implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final MissionType type;
    private final String zoneName;
    private final Coordinates destination;
    private final Priority priority;
    private final Instant createdAt;
    private MissionState state;
    private String assignedDroneId;
    private String lastEvent;

    public Mission(
            String id,
            MissionType type,
            String zoneName,
            Coordinates destination,
            Priority priority
    ) {
        this(id, type, zoneName, destination, priority, Instant.now(), MissionState.PENDING, null, "Ocorrência criada");
    }

    private Mission(
            String id,
            MissionType type,
            String zoneName,
            Coordinates destination,
            Priority priority,
            Instant createdAt,
            MissionState state,
            String assignedDroneId,
            String lastEvent
    ) {
        this.id = id;
        this.type = type;
        this.zoneName = zoneName;
        this.destination = destination;
        this.priority = priority;
        this.createdAt = createdAt;
        this.state = state;
        this.assignedDroneId = assignedDroneId;
        this.lastEvent = lastEvent;
    }

    public String id() {
        return id;
    }

    public MissionType type() {
        return type;
    }

    public String zoneName() {
        return zoneName;
    }

    public Coordinates destination() {
        return destination;
    }

    public Priority priority() {
        return priority;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public synchronized MissionState state() {
        return state;
    }

    public synchronized String assignedDroneId() {
        return assignedDroneId;
    }

    public synchronized String lastEvent() {
        return lastEvent;
    }

    public synchronized boolean isActive() {
        return state == MissionState.PENDING
                || state == MissionState.ASSIGNED
                || state == MissionState.IN_PROGRESS;
    }

    public synchronized void assign(String droneId, String event) {
        this.assignedDroneId = droneId;
        this.state = MissionState.ASSIGNED;
        this.lastEvent = event;
    }

    public synchronized void markInProgress() {
        this.state = MissionState.IN_PROGRESS;
    }

    public synchronized void complete(String event) {
        this.state = MissionState.COMPLETED;
        this.lastEvent = event;
    }

    public synchronized void fail(String event) {
        this.state = MissionState.FAILED;
        this.lastEvent = event;
    }

    public synchronized void waiting(String event) {
        this.assignedDroneId = null;
        this.state = MissionState.PENDING;
        this.lastEvent = event;
    }

    public synchronized Mission copy() {
        return new Mission(
                id,
                type,
                zoneName,
                destination,
                priority,
                createdAt,
                state,
                assignedDroneId,
                lastEvent
        );
    }
}
