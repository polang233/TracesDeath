package cc.sbsj.mc.tracesDeath.events;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.gui.TraceGuiManager;
import cc.sbsj.mc.tracesDeath.trace.TraceCacheManager;
import cc.sbsj.mc.tracesDeath.trace.TraceData;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * 处理使用虚拟 GUI 的墓碑实体交互。
 */
public final class TraceEntityInteractEvents implements Listener {
    private final TracesDeath plugin;
    private final TraceKeys keys;
    private final TraceGuiManager guiManager;
    private final TraceCacheManager cacheManager;

    public TraceEntityInteractEvents(TracesDeath plugin, TraceKeys keys,
                                     TraceGuiManager guiManager, TraceCacheManager cacheManager) {
        this.plugin = plugin;
        this.keys = keys;
        this.guiManager = guiManager;
        this.cacheManager = cacheManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        UUID traceId = readTraceId(event.getRightClicked());
        if (traceId == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        TraceData data = cacheManager.getTrace(traceId);
        if (data == null) {
            player.sendMessage(Component.text("该墓碑的数据已丢失").color(NamedTextColor.RED));
            return;
        }
        if (plugin.traceConfig().ownerOnly() && !data.playerId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("你不能打开其他玩家的墓碑").color(NamedTextColor.RED));
            return;
        }
        guiManager.openTraceGui(player, traceId);

        if (plugin.traceConfig().debug()) {
            plugin.getLogger().info(player.getName() + " 点击了墓碑实体: " + traceId);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (readTraceId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private UUID readTraceId(Entity entity) {
        String type = entity.getPersistentDataContainer().get(keys.traceType(), PersistentDataType.STRING);
        if (!"mannequin".equals(type)) {
            return null;
        }
        String traceId = entity.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
        if (traceId == null) {
            return null;
        }
        try {
            return UUID.fromString(traceId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
