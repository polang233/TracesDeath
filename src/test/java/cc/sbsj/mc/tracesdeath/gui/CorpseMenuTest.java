package cc.sbsj.mc.tracesdeath.gui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.sbsj.mc.tracesdeath.corpse.Corpse;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;

class CorpseMenuTest {
    private static Corpse sampleCorpse() {
        return new Corpse(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                "Player",
                java.util.UUID.randomUUID(),
                0,
                64,
                0,
                0,
                0,
                java.util.List.of(),
                1700000000000L,
                java.util.Map.of());
    }

    @Test
    void longTitlesAreBoundedWithoutSplittingUnicode() {
        assertEquals(
                "   Polang_ 的遗体",
                CorpseMenu.menuTitle(
                        "Polang_", cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN")));
        assertEquals(
                "   LongPlayerName16 的遗体",
                CorpseMenu.menuTitle(
                        "LongPlayerName16",
                        cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN")));
        assertTrue(
                CorpseMenu.menuTitle(
                                "这是一个很长很长很长很长的玩家名字",
                                cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN"))
                        .endsWith("… 的遗体"));
        assertTrue(
                CorpseMenu.menuTitle(
                                        "x".repeat(100),
                                        cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN"))
                                .length()
                        < 30);
    }

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
        Corpse corpse = sampleCorpse();
        try (var ignored = mockStatic(Bukkit.class)) {
            var info =
                    CorpseMenu.information(
                            corpse, cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN"));
            assertTrue(info.contains("死亡者：Player"));
            assertEquals(5, info.size());
            assertTrue(info.contains("剩余物品格数：0"));
            assertTrue(info.stream().anyMatch(line -> line.startsWith("死亡时间：2023-11-")));
            assertTrue(info.stream().anyMatch(line -> line.endsWith("0 64 0")));
        }
    }

    @Test
    void legacyRecordReportsUnknownDeathTime() {
        Corpse original = sampleCorpse();
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
            assertTrue(
                    CorpseMenu.information(
                                    legacy,
                                    cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN"))
                            .contains("死亡时间：未记录"));
        }
    }
}
