package rescuenet.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionTest {
    @Test
    void startsPendingAndBecomesInactiveAfterCompletion() {
        Mission mission = sample();
        assertEquals(MissionState.PENDING, mission.state());
        assertTrue(mission.isActive());

        mission.assign("DR-001", "alocado");
        assertEquals(MissionState.ASSIGNED, mission.state());
        assertEquals("DR-001", mission.assignedDroneId());

        mission.markInProgress();
        assertEquals(MissionState.IN_PROGRESS, mission.state());
        assertTrue(mission.isActive());

        mission.complete("regressou");
        assertEquals(MissionState.COMPLETED, mission.state());
        assertFalse(mission.isActive());
    }

    @Test
    void waitingClearsAssignment() {
        Mission mission = sample();
        mission.assign("DR-001", "alocado");
        mission.waiting("drone caiu");
        assertNull(mission.assignedDroneId());
        assertEquals(MissionState.PENDING, mission.state());
        assertTrue(mission.isActive());
        assertEquals("drone caiu", mission.lastEvent());
    }

    @Test
    void copyDoesNotShareLaterMutations() {
        Mission original = sample();
        Mission copy = original.copy();
        original.assign("DR-002", "alocado");
        assertNull(copy.assignedDroneId());
        assertEquals(MissionState.PENDING, copy.state());
    }

    private static Mission sample() {
        return new Mission(
                "MIS-001",
                MissionType.BUSCA_E_SALVAMENTO,
                "Zona A",
                new Coordinates(-25.93, 32.56),
                Priority.HIGH
        );
    }
}
