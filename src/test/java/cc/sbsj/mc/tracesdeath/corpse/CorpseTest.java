package cc.sbsj.mc.tracesdeath.corpse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.*;

class CorpseTest {
    static Corpse corpse() {
        return new Corpse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Player",
                UUID.randomUUID(),
                0,
                64,
                0,
                0,
                0,
                java.util.Collections.emptyList(),
                1700000000000L,
                Map.of(39, CorpseItemsTest.item(Material.DIAMOND_HELMET, 1)));
    }

    @Test
    void preparedCopyDoesNotPublishRemovalAndRollbackRestoresClaimability() {
        Corpse original = corpse();
        Corpse prepared = original.copy();
        prepared.begin(
                new Corpse.PendingClaim(
                        UUID.randomUUID(), 41, Map.of(), Map.of(), Map.of(), List.of(), false));
        assertNull(original.pending());
        assertFalse(original.empty());
        prepared.finish(false);
        assertFalse(prepared.empty());
        assertNull(prepared.pending());
    }

    @Test
    void commitConsumesExactlyOnceAndEndsEmptyCorpse() {
        Corpse corpse = corpse();
        corpse.begin(
                new Corpse.PendingClaim(
                        UUID.randomUUID(), 41, Map.of(), Map.of(), Map.of(), List.of(), false));
        assertThrows(IllegalStateException.class, () -> corpse.begin(corpse.pending()));
        corpse.finish(true);
        assertTrue(corpse.empty());
        assertNull(corpse.pending());
        assertThrows(IllegalStateException.class, () -> corpse.finish(true));
    }

    @Test
    void deathTimeSurvivesCopiesAndClaims() {
        Corpse corpse = corpse();
        assertEquals(1700000000000L, corpse.copy().deathTime);
        corpse.begin(
                new Corpse.PendingClaim(
                        UUID.randomUUID(), 41, Map.of(), Map.of(), Map.of(), List.of(), false));
        corpse.finish(true);
        assertEquals(1700000000000L, corpse.deathTime);
    }

    @Test
    void droppedItemsHaveAnExplicitUncertainStage() {
        Corpse corpse = corpse();
        corpse.begin(
                new Corpse.PendingClaim(
                        UUID.randomUUID(),
                        41,
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        List.of(CorpseItemsTest.item(Material.DIAMOND, 4)),
                        false));
        Corpse dropping = corpse.copy();
        dropping.startDrops();
        assertFalse(corpse.pending().dropsStarted());
        assertTrue(dropping.pending().dropsStarted());
        assertEquals(4, dropping.pending().drops().getFirst().getAmount());
        assertThrows(IllegalStateException.class, dropping::startDrops);
    }

    @Test
    void returnedItemCopiesCannotMutateInventory() {
        Corpse corpse = corpse();
        corpse.items().get(39).setAmount(20);
        assertEquals(1, corpse.items().get(39).getAmount());
    }
}
