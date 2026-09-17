package rescuenet.common;

import java.io.Serial;
import java.io.Serializable;

public record Coordinates(double lat, double lon) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public double euclidean(Coordinates other) {
        double dLat = lat - other.lat;
        double dLon = lon - other.lon;
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    public Coordinates stepTowards(Coordinates target, double maxStep) {
        double dist = euclidean(target);
        if (dist <= maxStep || dist == 0) {
            return target;
        }
        double ratio = maxStep / dist;
        return new Coordinates(lat + (target.lat - lat) * ratio, lon + (target.lon - lon) * ratio);
    }

    @Override
    public String toString() {
        return "%.4f, %.4f".formatted(lat, lon);
    }
}
