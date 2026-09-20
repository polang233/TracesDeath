package cc.sbsj.mc.tracesdeath.corpse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;

class CorpseMenuTest {
    @Test
    void shiftWithdrawalsAreAcceptedButSwapAndDropClicksAreNot() {
        assertTrue(CorpseMenu.isTakeClick(org.bukkit.event.inventory.ClickType.SHIFT_LEFT));
        assertTrue(CorpseMenu.isTakeClick(org.bukkit.event.inventory.ClickType.SHIFT_RIGHT));
        assertTrue(CorpseMenu.isTakeClick(org.bukkit.event.inventory.ClickType.LEFT));
        assertFalse(CorpseMenu.isTakeClick(org.bukkit.event.inventory.ClickType.NUMBER_KEY));
        assertFalse(CorpseMenu.isTakeClick(org.bukkit.event.inventory.ClickType.DROP));
        assertFalse(CorpseMenu.isTakeClick(org.bukkit.event.inventory.ClickType.DOUBLE_CLICK));
    }

    @Test
    void informationIncludesIdentityTimeAndLocation() {
        Corpse corpse = CorpseTest.corpse();
        try (var ignored = mockStatic(Bukkit.class)) {
            var info = CorpseMenu.information(corpse);
            assertTrue(info.contains("死亡者 ID：Player"));
            assertTrue(info.contains("UUID：" + corpse.owner));
            assertTrue(info.stream().anyMatch(line -> line.startsWith("死亡时间：2023-11-")));
            assertTrue(info.stream().anyMatch(line -> line.endsWith("0 64 0")));
        }
    }

    @Test
    void legacyRecordReportsUnknownDeathTime() {
        Corpse original = CorpseTest.corpse();
        Corpse legacy =
                new Corpse(
                        original.id,
                        original.owner,
                        original.name,
                        original.world,
                        original.x,
                        original.y,
                        original.z,
                        original.yaw,
                        original.heldSlot,
                        original.skin,
                        0,
                        original.items());
        try (var ignored = mockStatic(Bukkit.class)) {
            assertTrue(CorpseMenu.information(legacy).contains("死亡时间：未记录"));
        }
    }
}
