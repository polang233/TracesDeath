package cc.sbsj.mc.tracesDeath.events;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * 墓碑保护事件监听器
 * <p>
 * 处理墓碑容器的防熔岩燃烧和浮力特性。
 */
public class TraceProtectionEvents implements Listener {
    private final TracesDeath plugin;
    private final TraceKeys keys;

    public TraceProtectionEvents(TracesDeath plugin, TraceKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    /**
     * 防止方块容器被熔岩燃烧
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Container container)) {
            return;
        }
        
        if (!(container instanceof TileState tileState)) {
            return;
        }
        
        String traceIdStr = tileState.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
        if (traceIdStr == null) {
            return; // 不是墓碑容器
        }
        
        // 检查配置是否启用防熔岩
        boolean lavaProof = false;
        String storageType = plugin.traceConfig().storageType();
        if ("block".equals(storageType)) {
            lavaProof = plugin.traceConfig().block().lavaProof();
        }
        
        if (lavaProof) {
            event.setCancelled(true);
            if (plugin.traceConfig().debug()) {
                plugin.getLogger().info("阻止墓碑容器被燃烧: " + traceIdStr);
            }
        }
    }

    /**
     * 防止矿车和尸体实体被熔岩伤害
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        
        // 只处理熔岩伤害
        if (event.getCause() != EntityDamageEvent.DamageCause.LAVA && 
            event.getCause() != EntityDamageEvent.DamageCause.FIRE &&
            event.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK) {
            return;
        }
        
        // 检查是否是矿车
        if (entity instanceof StorageMinecart minecart) {
            handleMinecartProtection(minecart, event);
        }
    }

    /**
     * 防止实体着火
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        
        // 检查是否是矿车
        if (entity instanceof StorageMinecart minecart) {
            handleMinecartCombustProtection(minecart, event);
        }
    }

    private void handleMinecartProtection(StorageMinecart minecart, EntityDamageEvent event) {
        String traceIdStr = minecart.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
        if (traceIdStr == null) {
            return; // 不是墓碑容器
        }
        
        // 检查配置是否启用防熔岩
        boolean lavaProof = false;
        String storageType = plugin.traceConfig().storageType();
        if ("minecart".equals(storageType)) {
            lavaProof = plugin.traceConfig().minecart().lavaProof();
        }
        
        if (lavaProof) {
            event.setCancelled(true);
            if (plugin.traceConfig().debug()) {
                plugin.getLogger().info("阻止墓碑矿车被熔岩伤害: " + traceIdStr);
            }
        }
    }

    private void handleMinecartCombustProtection(StorageMinecart minecart, EntityCombustEvent event) {
        String traceIdStr = minecart.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
        if (traceIdStr == null) {
            return; // 不是墓碑容器
        }
        
        // 检查配置是否启用防熔岩
        boolean lavaProof = false;
        String storageType = plugin.traceConfig().storageType();
        if ("minecart".equals(storageType)) {
            lavaProof = plugin.traceConfig().minecart().lavaProof();
        }
        
        if (lavaProof) {
            event.setCancelled(true);
            if (plugin.traceConfig().debug()) {
                plugin.getLogger().info("阻止墓碑矿车着火: " + traceIdStr);
            }
        }
    }
}
