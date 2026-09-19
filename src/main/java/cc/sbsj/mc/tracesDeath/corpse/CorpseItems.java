package cc.sbsj.mc.tracesDeath.corpse;

import java.util.*;
import org.bukkit.inventory.ItemStack;

/** Slot numbers 0..40 match PlayerInventory; 41+ retain additional event drops. */
public final class CorpseItems {
    private CorpseItems() {}

    public static boolean empty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    public static Map<Integer, ItemStack> copy(Map<Integer, ItemStack> source) {
        Map<Integer, ItemStack> result = new TreeMap<>();
        source.forEach((slot, item) -> {
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

    /** Pure transfer plan: merge stacks first, then fill storage slots. Armor is not auto-equipped. */
    public static Transfer plan(Map<Integer, ItemStack> before, ItemStack source, int inventoryMax) {
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

    public static ItemStack[] storageArray(Map<Integer, ItemStack> items) {
        ItemStack[] result = new ItemStack[36];
        items.forEach((slot, item) -> result[slot] = item.clone());
        return result;
    }

    /** Fixed GUI mapping; empty source slots stay empty. */
    public static int sourceSlot(int page, int guiSlot) {
        if (page > 0) return guiSlot >= 0 && guiSlot < 45 ? 41 + (page - 1) * 45 + guiSlot : -1;
        if (guiSlot >= 0 && guiSlot < 4) return 39 - guiSlot;
        if (guiSlot == 4) return 40;
        if (guiSlot >= 9 && guiSlot < 36) return guiSlot;
        if (guiSlot >= 36 && guiSlot < 45) return guiSlot - 36;
        return -1;
    }

    public static int pages(Map<Integer, ItemStack> items) {
        int max = items.keySet().stream().mapToInt(Integer::intValue).max().orElse(40);
        return max < 41 ? 1 : 2 + (max - 41) / 45;
    }

    public record Transfer(Map<Integer, ItemStack> after, int remaining) {}
}
