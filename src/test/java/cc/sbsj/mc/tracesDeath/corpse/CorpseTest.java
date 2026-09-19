package cc.sbsj.mc.tracesDeath.corpse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.bukkit.Material;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

class CorpseTest {
    static Corpse corpse() {
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.clone()).thenReturn(profile);
        return new Corpse(UUID.randomUUID(), UUID.randomUUID(), "Player", UUID.randomUUID(), 0, 64, 0,
                0, 0, profile, Map.of(39, CorpseItemsTest.item(Material.DIAMOND_HELMET, 1)));
    }

    @Test void preparedCopyDoesNotPublishRemovalAndRollbackRestoresClaimability() {
        Corpse original = corpse();
        Corpse prepared = original.copy();
        prepared.begin(new Corpse.PendingClaim(UUID.randomUUID(), Map.of(), Map.of(), Map.of()));
        assertNull(original.pending());
        assertFalse(original.empty());
        prepared.finish(false);
        assertFalse(prepared.empty());
        assertNull(prepared.pending());
    }

    @Test void commitConsumesExactlyOnceAndEndsEmptyCorpse() {
        Corpse corpse = corpse();
        corpse.begin(new Corpse.PendingClaim(UUID.randomUUID(), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalStateException.class, () -> corpse.begin(corpse.pending()));
        corpse.finish(true);
        assertTrue(corpse.empty());
        assertNull(corpse.pending());
        assertThrows(IllegalStateException.class, () -> corpse.finish(true));
    }

    @Test void returnedItemCopiesCannotMutateInventory() {
        Corpse corpse = corpse();
        corpse.items().get(39).setAmount(20);
        assertEquals(1, corpse.items().get(39).getAmount());
    }
}
