package cc.sbsj.mc.tracesDeath.events;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageProvider;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import cc.sbsj.mc.tracesDeath.util.InventoryUtil;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * 墓碑容器事件监听器
 * <p>
 * 处理容器关闭时的自动清理逻辑。
 */
public class TraceContainerEvents implements Listener {
    private final TracesDeath plugin;
    private final TraceKeys keys;

    public TraceContainerEvents(TracesDeath plugin, TraceKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 检查是否是方块容器
        if (event.getInventory().getHolder() instanceof Container container) {
            handleBlockContainer(container);
        }
        
        // 检查是否是矿车容器
        if (event.getInventory().getHolder() instanceof StorageMinecart minecart) {
            handleMinecartContainer(minecart);
        }
    }

    private void handleBlockContainer(Container container) {
        if (!(container instanceof TileState tileState)) {
            return;
        }
        
        String traceIdStr = tileState.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
        if (traceIdStr == null) {
            return; // 不是墓碑容器
        }
        
        UUID traceId;
        try {
            traceId = UUID.fromString(traceIdStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        
        // 检查配置是否启用自动清理
        boolean autoRemove = false;
        String storageType = plugin.traceConfig().storageType();
        if ("block".equals(storageType)) {
            autoRemove = plugin.traceConfig().block().autoRemoveWhenEmpty();
        }
        
        if (!autoRemove) {
            return;
        }
        
        // 检查容器是否为空
        if (InventoryUtil.isEmpty(container.getInventory())) {
            Block block = container.getBlock();
            Location location = block.getLocation();
            
            // 验证这确实是我们的墓碑
            var providerOpt = plugin.storageRegistry().find("block");
            if (providerOpt.isPresent() && providerOpt.get().isValid(location, traceId)) {
                // 移除容器方块
                block.setType(org.bukkit.Material.AIR, false);
                
                if (plugin.traceConfig().debug()) {
                    plugin.getLogger().info("自动清理空容器: " + traceId);
                }
            }
        }
    }

    private void handleMinecartContainer(StorageMinecart minecart) {
        String traceIdStr = minecart.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
        if (traceIdStr == null) {
            return; // 不是墓碑容器
        }
        
        UUID traceId;
        try {
            traceId = UUID.fromString(traceIdStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        
        // 检查配置是否启用自动清理
        boolean autoRemove = false;
        String storageType = plugin.traceConfig().storageType();
        if ("minecart".equals(storageType)) {
            autoRemove = plugin.traceConfig().minecart().autoRemoveWhenEmpty();
        }
        
        if (!autoRemove) {
            return;
        }
        
        // 检查容器是否为空
        if (InventoryUtil.isEmpty(minecart.getInventory())) {
            Location location = minecart.getLocation();
            
            // 验证这确实是我们的墓碑
            var providerOpt = plugin.storageRegistry().find("minecart");
            if (providerOpt.isPresent() && providerOpt.get().isValid(location, traceId)) {
                // 移除矿车实体
                minecart.remove();
                
                if (plugin.traceConfig().debug()) {
                    plugin.getLogger().info("自动清理空矿车: " + traceId);
                }
            }
        }
    }
}
