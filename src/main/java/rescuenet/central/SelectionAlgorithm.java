package rescuenet.central;

import rescuenet.common.Coordinates;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public final class SelectionAlgorithm {
    private SelectionAlgorithm() {
    }

    public static Optional<TrackedDrone> select(Collection<TrackedDrone> drones, Coordinates destination) {
        return drones.stream()
                .filter(TrackedDrone::selectable)
                .max(Comparator
                        .comparingDouble((TrackedDrone drone) -> score(drone, destination))
                        .thenComparingDouble(drone -> drone.snapshot().batteryPercent()));
    }

    public static double score(TrackedDrone drone, Coordinates destination) {
        double distance = drone.snapshot().location().euclidean(destination);
        return drone.snapshot().batteryPercent() / (1.0 + distance);
    }
}
