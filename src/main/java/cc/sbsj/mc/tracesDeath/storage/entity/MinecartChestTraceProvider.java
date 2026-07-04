package cc.sbsj.mc.tracesDeath.storage.entity;

import cc.sbsj.mc.tracesDeath.storage.PlacementResult;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageProvider;
import cc.sbsj.mc.tracesDeath.trace.TraceContext;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import cc.sbsj.mc.tracesDeath.util.InventoryUtil;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * 箱子矿车痕迹提供者
 * <p>
 * 使用箱子矿车实体作为死亡痕迹容器。
 */
public final class MinecartChestTraceProvider implements TraceStorageProvider {
    private final TraceKeys keys;

    public MinecartChestTraceProvider(TraceKeys keys) {
        this.keys = keys;
    }

    @Override
    public String id() {
        return "minecart";
    }
    
    @Override
    public @NotNull String displayName() {
        return "箱子矿车";
    }

    @Override
    public boolean supports(TraceContext context) {
        return context.config().minecart().entityType() == EntityType.CHEST_MINECART;
    }

    @Override
    public PlacementResult place(TraceContext context) {
        Location location = context.location().clone().add(0.5, 0.1, 0.5);
        Entity entity = location.getWorld().spawnEntity(location, EntityType.CHEST_MINECART);
        if (!(entity instanceof StorageMinecart minecart)) {
            entity.remove();
            return PlacementResult.failure(context.lang().text("storage.minecart.spawn-failed"));
        }

        minecart.customName(context.lang().component("storage.display-name", Map.of("player", context.player().getName())));
        minecart.setCustomNameVisible(true);
        minecart.getPersistentDataContainer().set(keys.traceId(), PersistentDataType.STRING, context.traceId().toString());
        minecart.getPersistentDataContainer().set(keys.traceType(), PersistentDataType.STRING, id());
        minecart.getPersistentDataContainer().set(keys.owner(), PersistentDataType.STRING, context.player().getUniqueId().toString());
        InventoryUtil.fillOrDrop(minecart.getInventory(), context.drops(), location);
        return PlacementResult.success(context.lang().text("storage.minecart.created"));
    }
    
    @Override
    public boolean cleanup(@NotNull Location location, @NotNull UUID traceId) {
        // 在附近查找匹配的矿车实体
        for (Entity entity : location.getWorld().getNearbyEntities(location, 2, 2, 2)) {
            if (entity instanceof StorageMinecart minecart) {
                String storedId = minecart.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
                if (traceId.toString().equals(storedId)) {
                    // 掉落物品并移除实体
                    InventoryUtil.dropAll(minecart.getInventory(), entity.getLocation());
                    entity.remove();
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean isValid(@NotNull Location location, @NotNull UUID traceId) {
        for (Entity entity : location.getWorld().getNearbyEntities(location, 2, 2, 2)) {
            if (entity instanceof StorageMinecart minecart) {
                String storedId = minecart.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
                if (traceId.toString().equals(storedId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
