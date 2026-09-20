package cc.sbsj.mc.tracesdeath.corpse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

class CorpseServiceTest {
    @Test
    void prepareWriteFailureNeverTransfersItems() throws Exception {
        Fixture f = new Fixture();
        doThrow(new IOException("disk full")).when(f.store).save(any());
        f.service.claim(f.player, f.corpse.id, 39);
        verify(f.inventory, never()).setContents(any());
        verify(f.player, never()).saveData();
        assertNull(f.service.get(f.corpse.id).pending());
    }

    @Test
    void completionWriteFailureKeepsDurablePendingRecordAndPreventsAnotherClaim() throws Exception {
        Fixture f = new Fixture();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            f.service.start();
            doNothing().doThrow(new IOException("disk full")).when(f.store).save(any());
            f.service.claim(f.player, f.corpse.id, 39);
            assertNotNull(f.service.get(f.corpse.id).pending());
            assertFalse(f.service.get(f.corpse.id).empty());
            verify(f.player).saveData();
            verify(f.player).kickPlayer(anyString());
            f.service.claim(f.player, f.corpse.id, 39);
            verify(f.inventory, times(1)).setContents(any());
        }
    }

    @Test
    void successfulLastClaimSavesTerminalRecordBeforeRemovingActiveCorpse() throws Exception {
        Fixture f = new Fixture();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            f.service.start();
            f.service.claim(f.player, f.corpse.id, 39);
            assertNull(f.service.get(f.corpse.id));
            ArgumentCaptor<Corpse> records = ArgumentCaptor.forClass(Corpse.class);
            verify(f.store, times(2)).save(records.capture());
            assertNotNull(records.getAllValues().get(0).pending());
            assertTrue(records.getAllValues().get(1).empty());
            assertNull(records.getAllValues().get(1).pending());
            var order = inOrder(f.store, f.inventory, f.player);
            order.verify(f.store).save(any());
            order.verify(f.inventory).setContents(any());
            order.verify(f.player).saveData();
            order.verify(f.store).save(any());
        }
    }

    @Test
    void loginBeforeSnapshotRollsBackWithoutGivingAnything() throws Exception {
        recovery(false, false);
    }

    @Test
    void loginAfterSnapshotCommitsWithoutGivingAgain() throws Exception {
        recovery(true, false);
    }

    @Test
    void ambiguousLoginKeepsRecordLockedForManualReview() throws Exception {
        recovery(false, true);
    }

    private void recovery(boolean delivered, boolean ambiguous) throws Exception {
        Fixture f = new Fixture();
        org.bukkit.inventory.ItemStack item = mock(org.bukkit.inventory.ItemStack.class);
        when(item.clone()).thenReturn(item); // Immutable identity fixture for snapshot equality.
        when(item.getAmount()).thenReturn(1);
        when(item.getType()).thenReturn(Material.DIAMOND);
        var after = Map.of(0, item);
        f.corpse.begin(
                new Corpse.PendingClaim(
                        f.corpse.owner, 36, Map.of(), after, Map.of(), List.of(), false));
        org.bukkit.inventory.ItemStack[] current = new org.bukkit.inventory.ItemStack[36];
        if (delivered) current[0] = item;
        if (ambiguous) current[1] = item;
        when(f.inventory.getStorageContents()).thenReturn(current);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            f.service.start();
            f.service.join(
                    new org.bukkit.event.player.PlayerJoinEvent(
                            f.player, net.kyori.adventure.text.Component.empty()));
            verify(f.inventory, never()).setContents(any());
            if (ambiguous) {
                assertNotNull(f.service.get(f.corpse.id).pending());
                verify(f.player).kickPlayer(anyString());
                verify(f.store, never()).save(any());
            } else if (delivered) {
                assertNull(f.service.get(f.corpse.id));
            } else {
                assertNull(f.service.get(f.corpse.id).pending());
                assertFalse(f.service.get(f.corpse.id).empty());
            }
        }
    }

    @Test
    void restoringEquipmentJournalsDisplacedItemBeforeDroppingIt() throws Exception {
        Fixture f = new Fixture();
        var contents = new org.bukkit.inventory.ItemStack[41];
        contents[39] = CorpseItemsTest.item(Material.GOLDEN_HELMET, 1);
        when(f.inventory.getContents()).thenReturn(contents);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            f.service.start();
            f.service.claimAll(f.player, f.corpse.id);
            ArgumentCaptor<Corpse> saved = ArgumentCaptor.forClass(Corpse.class);
            verify(f.store, times(3)).save(saved.capture());
            var intent = saved.getAllValues().get(0).pending();
            assertEquals(41, intent.inventorySize());
            assertEquals(Material.GOLDEN_HELMET, intent.before().get(39).getType());
            assertEquals(Material.DIAMOND_HELMET, intent.after().get(39).getType());
            assertEquals(Material.GOLDEN_HELMET, intent.drops().getFirst().getType());
            assertFalse(intent.dropsStarted());
            assertTrue(saved.getAllValues().get(1).pending().dropsStarted());
            assertTrue(saved.getAllValues().get(2).empty());
            var order = inOrder(f.store, f.inventory, f.player, f.player.getWorld());
            order.verify(f.store).save(any());
            order.verify(f.inventory).setContents(any());
            order.verify(f.player).saveData();
            order.verify(f.store).save(any());
            order.verify(f.player.getWorld()).dropItemNaturally(any(Location.class), any());
            order.verify(f.store).save(any());
        }
    }

    @Test
    void interruptedWorldDropsStayLockedInsteadOfBeingReplayedOnLogin() throws Exception {
        Fixture f = new Fixture();
        f.corpse.begin(
                new Corpse.PendingClaim(
                        f.corpse.owner,
                        41,
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        List.of(CorpseItemsTest.item(Material.DIAMOND, 3)),
                        true));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            f.service.start();
            f.service.join(
                    new org.bukkit.event.player.PlayerJoinEvent(
                            f.player, net.kyori.adventure.text.Component.empty()));
            assertNotNull(f.service.get(f.corpse.id).pending());
            verify(f.player.getWorld(), never()).dropItemNaturally(any(Location.class), any());
            verify(f.store, never()).save(any());
            verify(f.player).kickPlayer(anyString());
        }
    }

    @Test
    void manualResolutionRequiresTheRecipientToBeOffline() throws Exception {
        Fixture f = new Fixture();
        f.corpse.begin(
                new Corpse.PendingClaim(
                        f.corpse.owner, 41, Map.of(), Map.of(), Map.of(), List.of(), false));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(f.corpse.owner)).thenReturn(f.player);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> f.service.resolveClaim(f.corpse.id, true));
            verify(f.store, never()).save(any());
            assertNotNull(f.corpse.pending());
        }
    }

    private static final class Fixture {
        final Corpse corpse = CorpseTest.corpse();
        final CorpseStore store = mock(CorpseStore.class);
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final cc.sbsj.mc.tracesdeath.compat.ServerAdapter serverAdapter =
                mock(cc.sbsj.mc.tracesdeath.compat.ServerAdapter.class);
        final CorpseService service;

        Fixture() throws Exception {
            when(plugin.getName()).thenReturn("TracesDeath");
            when(plugin.namespace()).thenReturn("tracesdeath");
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            when(store.load()).thenReturn(List.of(corpse));
            World world = mock(World.class);
            when(world.getUID()).thenReturn(corpse.world);
            when(player.getWorld()).thenReturn(world);
            when(player.getUniqueId()).thenReturn(corpse.owner);
            when(player.getLocation())
                    .thenReturn(new Location(world, corpse.x, corpse.y, corpse.z));
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getStorageContents()).thenReturn(new org.bukkit.inventory.ItemStack[36]);
            when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[41]);
            when(inventory.getMaxStackSize()).thenReturn(64);
            when(serverAdapter.createCorpseRenderer(any()))
                    .thenReturn(mock(cc.sbsj.mc.tracesdeath.compat.CorpseRenderer.class));
            service =
                    new CorpseService(
                            plugin,
                            store,
                            false,
                            false,
                            cc.sbsj.mc.tracesdeath.appearance.CorpseAppearance.VANILLA,
                            serverAdapter);
        }
    }
}
