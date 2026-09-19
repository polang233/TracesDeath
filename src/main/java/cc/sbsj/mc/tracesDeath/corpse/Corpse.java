package cc.sbsj.mc.tracesDeath.corpse;

import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import com.destroystokyo.paper.profile.PlayerProfile;

/** Main-thread owned record. Entity IDs are deliberately absent from saved data. */
public final class Corpse {
    public final UUID id;
    public final UUID owner;
    public final String name;
    public final UUID world;
    public final double x, y, z;
    public final float yaw;
    public final int heldSlot;
    public final PlayerProfile profile;
    private Map<Integer, ItemStack> items;
    private PendingClaim pending;

    public Corpse(UUID id, UUID owner, String name, UUID world, double x, double y, double z,
                  float yaw, int heldSlot, PlayerProfile profile, Map<Integer, ItemStack> items) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.heldSlot = heldSlot;
        this.profile = profile.clone();
        this.items = CorpseItems.copy(items);
    }

    public Map<Integer, ItemStack> items() { return CorpseItems.copy(items); }
    public boolean empty() { return items.isEmpty(); }
    public PendingClaim pending() { return pending; }

    public void begin(PendingClaim claim) {
        if (pending != null) throw new IllegalStateException("Claim already pending");
        pending = claim;
    }

    public void finish(boolean delivered) {
        if (pending == null) throw new IllegalStateException("No pending claim");
        if (delivered) items = pending.remaining();
        pending = null;
    }

    public Corpse copy() {
        Corpse copy = new Corpse(id, owner, name, world, x, y, z, yaw, heldSlot, profile, items);
        copy.pending = pending;
        return copy;
    }

    public record PendingClaim(UUID player, Map<Integer, ItemStack> before,
                               Map<Integer, ItemStack> after, Map<Integer, ItemStack> remaining) {
        public PendingClaim {
            before = CorpseItems.copy(before);
            after = CorpseItems.copy(after);
            remaining = CorpseItems.copy(remaining);
        }
        @Override public Map<Integer, ItemStack> before() { return CorpseItems.copy(before); }
        @Override public Map<Integer, ItemStack> after() { return CorpseItems.copy(after); }
        @Override public Map<Integer, ItemStack> remaining() { return CorpseItems.copy(remaining); }
    }
}
