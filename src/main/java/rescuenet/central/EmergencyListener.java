package rescuenet.central;

import rescuenet.common.Mission;

public interface EmergencyListener {
    void onFleetUpdated();

    void onMissionUpdated(Mission mission);

    void onFailover(Mission mission, String failedDroneId, String newDroneId);

    void onAlert(String message);
}
