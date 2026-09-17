package rescuenet.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CoordinatesTest {
    @Test
    void euclideanDistanceIsZeroForSamePoint() {
        Coordinates point = new Coordinates(-25.9692, 32.5732);
        assertEquals(0.0, point.euclidean(point));
    }

    @Test
    void euclideanDistanceMatchesPythagoras() {
        Coordinates origin = new Coordinates(0, 0);
        Coordinates target = new Coordinates(3, 4);
        assertEquals(5.0, origin.euclidean(target));
    }

    @Test
    void stepTowardsReturnsTargetWhenWithinMaxStep() {
        Coordinates origin = new Coordinates(-25.97, 32.57);
        Coordinates target = new Coordinates(-25.9701, 32.5701);
        assertSame(target, origin.stepTowards(target, 1.0));
    }

    @Test
    void stepTowardsMovesALimitedFractionOfThePath() {
        Coordinates origin = new Coordinates(0, 0);
        Coordinates target = new Coordinates(10, 0);
        Coordinates next = origin.stepTowards(target, 2);
        assertEquals(2.0, next.lat(), 1e-9);
        assertEquals(0.0, next.lon(), 1e-9);
        assertEquals(8.0, next.euclidean(target), 1e-9);
    }
}
