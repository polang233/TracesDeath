package cc.sbsj.mc.tracesdeath.corpse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.sbsj.mc.tracesdeath.compat.ServerAdapter;
import cc.sbsj.mc.tracesdeath.config.*;
import cc.sbsj.mc.tracesdeath.language.Messages;
import cc.sbsj.mc.tracesdeath.storage.CorpseStore;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.util.*;

class DeathHandlingTest {
    @Test
    void lateKeepInventoryChangeRemovesTheCapturedCopy() throws Exception {
        Fixture f = new Fixture(50);
        var scheduler = mock(BukkitScheduler.class);
        var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            f.service.death(f.event);
            when(f.event.getKeepInventory()).thenReturn(true);
            when(f.event.getKeepLevel()).thenReturn(true);
            verify(scheduler).runTask(any(org.bukkit.plugin.Plugin.class), task.capture());
            task.getValue().run();
            assertTrue(f.service.getAll().isEmpty());
            var saved = org.mockito.ArgumentCaptor.forClass(Corpse.class);
            verify(f.store, times(2)).save(saved.capture());
            assertTrue(saved.getAllValues().getLast().empty());
        }
    }

    @Test
    void keepsExcludedDropsAndStoresHalfOfSixLevels() throws Exception {
        Fixture f = new Fixture(50);
        var excluded = mock(ItemStack.class);
        var meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        when(excluded.getType()).thenReturn(Material.DIAMOND);
        when(excluded.getAmount()).thenReturn(1);
        when(excluded.hasItemMeta()).thenReturn(true);
        when(excluded.getItemMeta()).thenReturn(meta);
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(List.of("§aSoulbound"));
        f.drops.add(excluded);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            f.service.death(f.event);
            assertEquals(List.of(excluded), f.drops);
            assertEquals(36, f.service.getAll().getFirst().experience());
            verify(f.event).setDroppedExp(0);
        }
    }

    @Test
    void keepInventoryDoesNotCreateAnItemOrExperienceCopy() throws Exception {
        Fixture f = new Fixture(50);
        when(f.event.getKeepInventory()).thenReturn(true);
        f.service.death(f.event);
        assertTrue(f.service.getAll().isEmpty());
        assertEquals(1, f.drops.size());
        verifyNoInteractions(f.store);
    }

    @Test
    void respectsKeptExperienceAndCustomDeathExperience() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            Fixture f = new Fixture(mode == 3 ? 0 : 50);
            if (mode == 0) when(f.event.getKeepLevel()).thenReturn(true);
            if (mode == 1) when(f.event.getNewLevel()).thenReturn(3);
            if (mode == 2) when(f.event.getDroppedExp()).thenReturn(10);
            try (var bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
                f.service.death(f.event);
                assertEquals(0, f.service.getAll().getFirst().experience());
                verify(f.event, never()).setDroppedExp(anyInt());
            }
        }
    }

    @Test
    void createsExperienceOnlyRemainsAndRespectsPaperRetainedItems() throws Exception {
        Fixture f = new Fixture(100);
        when(f.adapter.keptItems(f.event)).thenReturn(List.copyOf(f.drops));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            f.service.death(f.event);
            Corpse corpse = f.service.getAll().getFirst();
            assertTrue(corpse.items().isEmpty());
            assertEquals(72, corpse.experience());
            assertFalse(corpse.empty());
            assertEquals(1, f.drops.size());
        }
    }

    private static final class Fixture {
        final CorpseStore store = mock(CorpseStore.class);
        final Player player = mock(Player.class);
        final PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        final ServerAdapter adapter = mock(ServerAdapter.class);
        final List<ItemStack> drops = new ArrayList<>();
        final CorpseService service;

        Fixture(int percent) throws Exception {
            var config = new YamlConfiguration();
            config.set("experience.keep-percent", percent);
            config.set("death.exclude-items.lore-contains", List.of("Soulbound"));
            var inventory = mock(PlayerInventory.class);
            var world = mock(World.class);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getName()).thenReturn("Player");
            when(player.hasPermission("tracesdeath.use")).thenReturn(true);
            when(player.getLevel()).thenReturn(6);
            when(player.getInventory()).thenReturn(inventory);
            ItemStack item = CorpseItemsTest.item(Material.DIAMOND, 1);
            when(inventory.getContents()).thenReturn(new ItemStack[] {item});
            drops.add(item);
            when(event.getEntity()).thenReturn(player);
            when(event.getDrops()).thenReturn(drops);
            when(event.getDroppedExp()).thenReturn(42);
            when(adapter.findGroundLocation(any())).thenAnswer(call -> call.getArgument(0));
            service =
                    new CorpseService(
                            mock(JavaPlugin.class),
                            store,
                            false,
                            false,
                            CorpseAppearance.VANILLA,
                            adapter,
                            Messages.bundled("zh_CN"),
                            new DeathSettings(config));
            clearInvocations(store);
        }
    }
}
