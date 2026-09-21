package cc.sbsj.mc.tracesdeath.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

class CorpseEntitiesTest {
    @Test
    void preciseClickOpensWhenGenericListenerRunsFirst() throws Exception {
        Fixture f = new Fixture();
        var event = f.precise(EquipmentSlot.HAND);
        f.generic().callEvent(event);
        f.precise().callEvent(event);
        verify(f.open).accept(f.player, f.id);
        assertTrue(event.isCancelled());
    }

    @Test
    void preciseClickOpensWhenPreciseListenerRunsFirst() throws Exception {
        Fixture f = new Fixture();
        var event = f.precise(EquipmentSlot.HAND);
        f.precise().callEvent(event);
        f.generic().callEvent(event);
        verify(f.open).accept(f.player, f.id);
    }

    @Test
    void genericClickStillOpensOnVersionsWithSeparateEventLists() throws Exception {
        Fixture f = new Fixture();
        var event = new PlayerInteractEntityEvent(f.player, f.entity, EquipmentSlot.HAND);
        f.generic().callEvent(event);
        verify(f.open).accept(f.player, f.id);
        assertTrue(event.isCancelled());
    }

    @Test
    void offhandDoesNotOpenAndProtectionCancellationIsRespected() throws Exception {
        Fixture f = new Fixture();
        var offhand = f.precise(EquipmentSlot.OFF_HAND);
        f.generic().callEvent(offhand);
        f.precise().callEvent(offhand);
        assertTrue(offhand.isCancelled());
        var cancelled = f.precise(EquipmentSlot.HAND);
        cancelled.setCancelled(true);
        f.generic().callEvent(cancelled);
        f.precise().callEvent(cancelled);
        verifyNoInteractions(f.open);
    }

    @Test
    void unrelatedEntitiesRemainUnaffected() throws Exception {
        Fixture f = new Fixture();
        when(f.entity.getScoreboardTags()).thenReturn(java.util.Collections.emptySet());
        var event = f.precise(EquipmentSlot.HAND);
        f.generic().callEvent(event);
        f.precise().callEvent(event);
        assertFalse(event.isCancelled());
        verifyNoInteractions(f.open);
    }

    private static final class Fixture {
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final Player player = mock(Player.class);
        final Entity entity = mock(Entity.class);
        final UUID id = UUID.randomUUID();

        @SuppressWarnings("unchecked")
        final BiConsumer<Player, UUID> open = mock(BiConsumer.class);

        final CorpseEntities listener;

        Fixture() {
            when(plugin.namespace()).thenReturn("tracesdeath");
            when(entity.getScoreboardTags())
                    .thenReturn(java.util.Collections.singleton(CorpseEntities.TAG_PREFIX + id));
            listener =
                    new CorpseEntities(
                            plugin,
                            Map.of(),
                            open,
                            ignored -> {},
                            mock(cc.sbsj.mc.tracesdeath.entity.CorpseRenderer.class));
        }

        PlayerInteractAtEntityEvent precise(EquipmentSlot hand) {
            return new PlayerInteractAtEntityEvent(player, entity, new Vector(0, .3, 0), hand);
        }

        RegisteredListener generic() {
            return new RegisteredListener(
                    listener,
                    (ignored, event) -> listener.interact((PlayerInteractEntityEvent) event),
                    EventPriority.NORMAL,
                    plugin,
                    true);
        }

        RegisteredListener precise() {
            return new RegisteredListener(
                    listener,
                    (ignored, event) -> listener.interactAt((PlayerInteractAtEntityEvent) event),
                    EventPriority.NORMAL,
                    plugin,
                    true);
        }
    }
}
