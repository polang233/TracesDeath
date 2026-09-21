package cc.sbsj.mc.tracesdeath.corpse;

import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Slot numbers 0..40 match PlayerInventory; 41+ retain additional event drops. */
public final class CorpseItems {
    private CorpseItems() {}

    public static boolean empty(ItemStack item) {
        return item == null || item.getType() == org.bukkit.Material.AIR || item.getAmount() <= 0;
    }

    public static Map<Integer, ItemStack> copy(Map<Integer, ItemStack> source) {
        Map<Integer, ItemStack> result = new TreeMap<>();
        source.forEach(
                (slot, item) -> {
                    if (!empty(item)) result.put(slot, item.clone());
                });
        return result;
    }

    public static Map<Integer, ItemStack> snapshot(ItemStack[] contents) {
        Map<Integer, ItemStack> result = new TreeMap<>();
        for (int i = 0; i < contents.length; i++) {
            if (!empty(contents[i])) result.put(i, contents[i].clone());
        }
        return result;
    }

    /** Match only actual drops, consuming quantities so equal stacks cannot be duplicated. */
    public static Map<Integer, ItemStack> capture(ItemStack[] inventory, List<ItemStack> drops) {
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : drops) if (!empty(item)) remaining.add(item.clone());
        Map<Integer, ItemStack> result = new TreeMap<>();
        for (int slot = 0; slot < Math.min(41, inventory.length); slot++) {
            ItemStack original = inventory[slot];
            if (empty(original)) continue;
            int matched = 0;
            for (ItemStack drop : remaining) {
                if (drop.getAmount() > 0 && original.isSimilar(drop)) {
                    int amount = Math.min(original.getAmount() - matched, drop.getAmount());
                    matched += amount;
                    drop.setAmount(drop.getAmount() - amount);
                }
            }
            if (matched > 0) {
                ItemStack item = original.clone();
                item.setAmount(matched);
                result.put(slot, item);
            }
        }
        int extraSlot = 41;
        for (ItemStack item : remaining) {
            if (!empty(item)) result.put(extraSlot++, item.clone());
        }
        return result;
    }

    /**
     * Pure transfer plan: merge stacks first, then fill storage slots. Armor is not auto-equipped.
     */
    public static Transfer plan(
            Map<Integer, ItemStack> before, ItemStack source, int inventoryMax) {
        Map<Integer, ItemStack> after = copy(before);
        int remaining = source.getAmount();
        int max = Math.min(source.getMaxStackSize(), inventoryMax);
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack target = after.get(slot);
            if (target != null && target.isSimilar(source)) {
                int moved = Math.min(remaining, Math.max(0, max - target.getAmount()));
                target.setAmount(target.getAmount() + moved);
                remaining -= moved;
            }
        }
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            if (!after.containsKey(slot)) {
                ItemStack item = source.clone();
                int moved = Math.min(remaining, max);
                item.setAmount(moved);
                after.put(slot, item);
                remaining -= moved;
            }
        }
        return new Transfer(after, remaining);
    }

    public static ItemStack[] inventoryArray(Map<Integer, ItemStack> items, int size) {
        ItemStack[] result = new ItemStack[size];
        items.forEach((slot, item) -> result[slot] = item.clone());
        return result;
    }

    /**
     * Restore the original slots, or merge into storage. Overflow is planned before any mutation.
     */
    public static ClaimPlan planAll(
            Map<Integer, ItemStack> before,
            Map<Integer, ItemStack> source,
            boolean toInventory,
            int inventoryMax) {
        Map<Integer, ItemStack> after = copy(before);
        List<ItemStack> drops = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : new TreeMap<>(source).entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue();
            if (!toInventory && slot < 41) {
                ItemStack displaced = after.put(slot, item.clone());
                if (!empty(displaced)) drops.add(displaced.clone());
            } else {
                Transfer transfer = plan(after, item, inventoryMax);
                after = transfer.after();
                if (transfer.remaining() > 0) {
                    ItemStack leftover = item.clone();
                    leftover.setAmount(transfer.remaining());
                    drops.add(leftover);
                }
            }
        }
        return new ClaimPlan(after, drops);
    }

    public static final class ClaimPlan {
        private final Map<Integer, ItemStack> after;
        private final List<ItemStack> drops;

        public ClaimPlan(Map<Integer, ItemStack> after, List<ItemStack> drops) {
            this.after = after;
            this.drops = drops;
        }

        public Map<Integer, ItemStack> after() {
            return after;
        }

        public List<ItemStack> drops() {
            return drops;
        }
    }

    /** Fixed GUI mapping; empty source slots stay empty. */
    public static int sourceSlot(int page, int guiSlot, int heldSlot) {
        if (guiSlot >= 0 && guiSlot < 4) return 39 - guiSlot;
        if (guiSlot == 4) return 40;
        if (guiSlot == 5) return heldSlot;
        if (guiSlot < 18 || guiSlot >= 54) return -1;
        if (page > 0) return 41 + (page - 1) * 36 + guiSlot - 18;
        if (guiSlot < 45) return guiSlot - 9;
        int hotbarSlot = guiSlot - 45;
        return hotbarSlot == heldSlot ? -1 : hotbarSlot;
    }

    public static int pages(Map<Integer, ItemStack> items) {
        int max = items.keySet().stream().mapToInt(Integer::intValue).max().orElse(40);
        return max < 41 ? 1 : 2 + (max - 41) / 36;
    }

    public static final class Transfer {
        private final Map<Integer, ItemStack> after;
        private final int remaining;

        public Transfer(Map<Integer, ItemStack> after, int remaining) {
            this.after = after;
            this.remaining = remaining;
        }

        public Map<Integer, ItemStack> after() {
            return after;
        }

        public int remaining() {
            return remaining;
        }
    }
}
