package cc.sbsj.mc.tracesdeath.experience;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExperienceTest {
    @Test
    void convertsLevelsAndProgressInsteadOfHalvingTheLevel() {
        assertEquals(72, Experience.points(6, 0));
        assertEquals(3, Experience.level(36));
        assertEquals(394, Experience.atLevel(17));
        assertEquals(1507, Experience.atLevel(31));
        assertEquals(1628, Experience.atLevel(32));
        for (int level : new int[] {0, 6, 15, 16, 17, 30, 31, 32, 100}) {
            int points = Experience.points(level, .5f);
            var next = new Experience.Snapshot(0, 0, 0).add(points);
            assertEquals(points, Experience.points(next.level, next.progress));
        }
    }

    @Test
    void usesCurrentBarEvenWhenBukkitHistoricalTotalIsStale() {
        var next = new Experience.Snapshot(6, 0, 999).add(36);
        assertEquals(108, Experience.points(next.level, next.progress));
        assertEquals(1035, next.total);
    }
}
