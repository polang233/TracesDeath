package cc.sbsj.mc.tracesDeath.trace;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.config.TraceConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

/**
 * 墓碑物品领取服务。GUI 和快速领取必须共用这里的差额结算。
 */
public final class TraceClaimService {
    private final TracesDeath plugin;
    private final TraceCacheManager cacheManager;
    private final TraceManager traceManager;

    public TraceClaimService(TracesDeath plugin, TraceCacheManager cacheManager, TraceManager traceManager) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
        this.traceManager = traceManager;
    }

    @NotNull
    public ClaimResult claimItem(Player player, UUID traceId, int itemIndex,
                                 TraceConfig.InteractionConfig interaction) {
        TraceData data = cacheManager.getTrace(traceId);
        if (data == null) {
            return ClaimResult.missing();
        }

        List<ItemStack> items = compact(data.items());
        if (itemIndex < 0 || itemIndex >= items.size()) {
            return ClaimResult.noChange(countAmount(items));
        }

        ItemStack selected = items.get(itemIndex);
        TransferResult transfer = transfer(player, selected, interaction);
        items.remove(itemIndex);
        items.addAll(itemIndex, transfer.remaining());
        return persistResult(data, items, transfer);
    }

    @NotNull
    public ClaimResult claimAll(Player player, UUID traceId,
                                TraceConfig.InteractionConfig interaction) {
        TraceData data = cacheManager.getTrace(traceId);
        if (data == null) {
            return ClaimResult.missing();
        }

        List<ItemStack> remaining = new ArrayList<>();
        int moved = 0;
        int dropped = 0;
        for (ItemStack item : compact(data.items())) {
            TransferResult transfer = transfer(player, item, interaction);
            moved += transfer.movedAmount();
            dropped += transfer.droppedAmount();
            remaining.addAll(transfer.remaining());
        }
        return persistResult(data, remaining, new TransferResult(moved, dropped, remaining));
    }

    private ClaimResult persistResult(TraceData data, List<ItemStack> remaining, TransferResult transfer) {
        List<ItemStack> compacted = compact(remaining);
        cacheManager.updateTraceItems(data.traceId(), compacted);

        boolean removed = false;
        if (compacted.isEmpty() && plugin.traceConfig().autoRemoveWhenEmpty(data.storageType())) {
            removed = traceManager.removeTrace(data.traceId(), false);
        }
        return new ClaimResult(
                true,
                transfer.movedAmount(),
                transfer.droppedAmount(),
                countAmount(compacted),
                removed
        );
    }

    private TransferResult transfer(Player player, ItemStack source,
                                    TraceConfig.InteractionConfig interaction) {
        ItemStack working = source.clone();
        int moved = tryEquipOne(player.getInventory(), working, interaction.autoEquip());

        List<ItemStack> remaining = new ArrayList<>();
        if (working.getAmount() > 0) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(working.clone());
            int leftoverAmount = countAmount(leftovers.values());
            moved += working.getAmount() - leftoverAmount;
            for (ItemStack leftover : leftovers.values()) {
                remaining.add(leftover.clone());
            }
        }

        int dropped = 0;
        if (!remaining.isEmpty()
                && interaction.conflictHandling()
                == TraceConfig.InteractionConfig.ConflictHandling.DROP) {
            for (ItemStack item : remaining) {
                player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                dropped += item.getAmount();
            }
            remaining.clear();
        }
        return new TransferResult(moved, dropped, remaining);
    }

    private int tryEquipOne(PlayerInventory inventory, ItemStack item, boolean autoEquip) {
        if (!autoEquip || item.getAmount() <= 0) {
            return 0;
        }

        Material type = item.getType();
        ItemStack equipped = item.clone();
        equipped.setAmount(1);
        if (type.name().endsWith("_HELMET") && isEmpty(inventory.getHelmet())) {
            inventory.setHelmet(equipped);
        } else if ((type.name().endsWith("_CHESTPLATE") || type == Material.ELYTRA)
                && isEmpty(inventory.getChestplate())) {
            inventory.setChestplate(equipped);
        } else if (type.name().endsWith("_LEGGINGS") && isEmpty(inventory.getLeggings())) {
            inventory.setLeggings(equipped);
        } else if (type.name().endsWith("_BOOTS") && isEmpty(inventory.getBoots())) {
            inventory.setBoots(equipped);
        } else {
            return 0;
        }

        item.setAmount(item.getAmount() - 1);
        return 1;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private List<ItemStack> compact(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>(source.size());
        for (ItemStack item : source) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                result.add(item.clone());
            }
        }
        return result;
    }

    private int countAmount(Iterable<ItemStack> items) {
        int amount = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    private record TransferResult(
            int movedAmount,
            int droppedAmount,
            List<ItemStack> remaining
    ) {
        private TransferResult {
            remaining = List.copyOf(remaining);
        }
    }

    public record ClaimResult(
            boolean found,
            int movedAmount,
            int droppedAmount,
            int remainingAmount,
            boolean traceRemoved
    ) {
        private static ClaimResult missing() {
            return new ClaimResult(false, 0, 0, 0, false);
        }

        private static ClaimResult noChange(int remainingAmount) {
            return new ClaimResult(true, 0, 0, remainingAmount, false);
        }
    }
}
