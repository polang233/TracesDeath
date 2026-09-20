package cc.sbsj.mc.tracesdeath.corpse;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Main-thread owned record. Entity IDs are deliberately absent from saved data. */
public final class Corpse {
    public final UUID id, owner, world;
    public final String name;
    public final long deathTime;
    public final double x, y, z;
    public final float yaw;
    public final int heldSlot;
    public final List<SkinProperty> skin;
    private Map<Integer, ItemStack> items;
    private PendingClaim pending;

    public Corpse(
            UUID id,
            UUID owner,
            String name,
            UUID world,
            double x,
            double y,
            double z,
            float yaw,
            int heldSlot,
            List<SkinProperty> skin,
            long deathTime,
            Map<Integer, ItemStack> items) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.heldSlot = heldSlot;
        this.deathTime = deathTime;
        this.skin = Collections.unmodifiableList(new ArrayList<>(skin));
        this.items = CorpseItems.copy(items);
    }

    public Map<Integer, ItemStack> items() {
        return CorpseItems.copy(items);
    }

    public boolean empty() {
        return items.isEmpty();
    }

    public PendingClaim pending() {
        return pending;
    }

    public void begin(PendingClaim claim) {
        if (pending != null) throw new IllegalStateException("Claim already pending");
        pending = claim;
    }

    public void startDrops() {
        if (pending == null || pending.drops().isEmpty() || pending.dropsStarted())
            throw new IllegalStateException("No unstarted drops");
        pending =
                new PendingClaim(
                        pending.player(),
                        pending.inventorySize(),
                        pending.before(),
                        pending.after(),
                        pending.remaining(),
                        pending.drops(),
                        true);
    }

    public void finish(boolean delivered) {
        if (pending == null) throw new IllegalStateException("No pending claim");
        if (delivered) items = pending.remaining();
        pending = null;
    }

    public Corpse copy() {
        Corpse copy =
                new Corpse(id, owner, name, world, x, y, z, yaw, heldSlot, skin, deathTime, items);
        copy.pending = pending;
        return copy;
    }

    public static final class PendingClaim {
        private final UUID player;
        private final int inventorySize;
        private final Map<Integer, ItemStack> before, after, remaining;
        private final List<ItemStack> drops;
        private final boolean dropsStarted;

        public PendingClaim(
                UUID player,
                int inventorySize,
                Map<Integer, ItemStack> before,
                Map<Integer, ItemStack> after,
                Map<Integer, ItemStack> remaining,
                List<ItemStack> drops,
                boolean dropsStarted) {
            if (inventorySize != 36 && inventorySize != 41)
                throw new IllegalArgumentException("Invalid inventory size");
            this.player = player;
            this.inventorySize = inventorySize;
            this.dropsStarted = dropsStarted;
            this.before = CorpseItems.copy(before);
            this.after = CorpseItems.copy(after);
            this.remaining = CorpseItems.copy(remaining);
            this.drops = drops.stream().map(ItemStack::clone).collect(Collectors.toList());
        }

        public UUID player() {
            return player;
        }

        public int inventorySize() {
            return inventorySize;
        }

        public boolean dropsStarted() {
            return dropsStarted;
        }

        public List<ItemStack> drops() {
            return drops.stream().map(ItemStack::clone).collect(Collectors.toList());
        }

        public Map<Integer, ItemStack> before() {
            return CorpseItems.copy(before);
        }

        public Map<Integer, ItemStack> after() {
            return CorpseItems.copy(after);
        }

        public Map<Integer, ItemStack> remaining() {
            return CorpseItems.copy(remaining);
        }
    }
}
