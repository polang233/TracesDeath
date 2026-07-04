package cc.sbsj.mc.tracesDeath.storage.entity;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * 虚拟库存持有者，用于尸体实体的背包界面
 */
public final class TraceInventoryHolder implements InventoryHolder {
    private final UUID entityId;

    public TraceInventoryHolder(UUID entityId) {
        this.entityId = entityId;
    }

    public UUID entityId() {
        return entityId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("Trace inventory holders are lookup-only");
    }
}
