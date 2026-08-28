package cc.sbsj.mc.tracesDeath.events;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.storage.PlacementResult;
import cc.sbsj.mc.tracesDeath.trace.TraceManager;
import cc.sbsj.mc.tracesDeath.util.InventoryUtil;
import java.util.List;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 玩家事件监听器
 * <p>
 * 处理玩家死亡事件，创建墓碑。
 */
public class PlayerEvents implements Listener {
    private final TracesDeath plugin;
    private final TraceManager traceManager;

    public PlayerEvents(TracesDeath plugin, TraceManager traceManager) {
        this.plugin = plugin;
        this.traceManager = traceManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // 检查插件是否启用
        if (!plugin.traceConfig().enabled()) {
            return;
        }
        if (!event.getPlayer().hasPermission("tracesdeath.use")) {
            return;
        }

        // 当前稳定语义始终尊重 keepInventory。
        if (event.getKeepInventory()) {
            if (plugin.traceConfig().debug()) {
                plugin.getLogger().info("跳过墓碑创建：游戏规则 keepInventory=true");
            }
            return;
        }
        
        List<ItemStack> drops = InventoryUtil.compact(event.getDrops());
        if (drops.isEmpty()) {
            return;
        }

        // 创建墓碑
        PlacementResult result = traceManager.createTrace(
                event.getPlayer(),
                event.getPlayer().getLocation(),
                drops
        );
        
        if (!result.success()) {
            plugin.getLogger().warning("创建墓碑失败: " + result.message());
            return;
        }

        // 创建及初次持久化都成功后固定清除原生掉落，避免复制。
        event.getDrops().clear();
        
        // 播放音效
        if (plugin.traceConfig().playSound()) {
            event.getPlayer().getWorld().playSound(
                    event.getPlayer().getLocation(),
                    Sound.BLOCK_CHEST_CLOSE,
                    1.0f,
                    0.8f
            );
        }
        
        // 显示粒子效果
        if (plugin.traceConfig().showParticles()) {
            event.getPlayer().getWorld().spawnParticle(
                    Particle.CLOUD,
                    event.getPlayer().getLocation().add(0, 1, 0),
                    20,
                    0.5, 0.5, 0.5,
                    0.1
            );
        }
        
        // 调试日志
        if (plugin.traceConfig().debug()) {
            plugin.getLogger().info(result.message());
        }
    }
}
