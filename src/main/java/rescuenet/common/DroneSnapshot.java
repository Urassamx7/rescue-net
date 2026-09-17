package rescuenet.common;

import java.io.Serial;
import java.io.Serializable;

public record DroneSnapshot(
        String id,
        String ip,
        String baseName,
        double batteryPercent,
        DroneState state,
        Coordinates location,
        String missionId
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public DroneSnapshot asOffline() {
        return new DroneSnapshot(id, ip, baseName, batteryPercent, DroneState.OFFLINE, location, missionId);
    }
}
