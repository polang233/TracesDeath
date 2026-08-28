package cc.sbsj.mc.tracesDeath.events;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.config.TraceConfig;
import cc.sbsj.mc.tracesDeath.gui.TraceGuiManager;
import cc.sbsj.mc.tracesDeath.trace.TraceCacheManager;
import cc.sbsj.mc.tracesDeath.trace.TraceData;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/**
 * 处理 Mannequin 墓碑及其 Interaction 点击代理的交互和加载生命周期。
 */
public final class MannequinEvents implements Listener {
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 49.0;

    private final TracesDeath plugin;
    private final TraceKeys keys;
    private final TraceGuiManager guiManager;
    private final TraceCacheManager cacheManager;

    public MannequinEvents(TracesDeath plugin, TraceKeys keys,
                           TraceGuiManager guiManager, TraceCacheManager cacheManager) {
        this.plugin = plugin;
        this.keys = keys;
        this.guiManager = guiManager;
        this.cacheManager = cacheManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        UUID traceId = readTraceId(event.getRightClicked());
        if (traceId == null) {
            return;
        }
        event.setCancelled(true);
        interact(event.getPlayer(), traceId, ClickKind.RIGHT);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        UUID traceId = readTraceId(event.getRightClicked());
        if (traceId == null) {
            return;
        }
        event.setCancelled(true);
        interact(event.getPlayer(), traceId, ClickKind.RIGHT);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        UUID traceId = readTraceId(event.getEntity());
        if (traceId == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            interact(player, traceId, ClickKind.LEFT);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        UUID traceId = readTraceId(event.getEntity());
        if (traceId == null) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setShouldPlayDeathSound(false);
        if (cacheManager.getTrace(traceId) != null) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(
                    plugin, () -> plugin.traceManager().reconcileTrace(traceId));
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        Set<UUID> tracesToReconcile = new LinkedHashSet<>();
        for (Entity entity : event.getEntities()) {
            UUID traceId = readTraceId(entity);
            if (traceId == null) {
                continue;
            }
            if (cacheManager.getTrace(traceId) == null) {
                removeOrphanEntity(entity);
            } else {
                tracesToReconcile.add(traceId);
            }
        }

        if (!tracesToReconcile.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (UUID traceId : tracesToReconcile) {
                    plugin.traceManager().reconcileTrace(traceId);
                }
            });
        }
    }

    private void interact(Player player, UUID traceId, ClickKind clickKind) {
        TraceData data = cacheManager.getTrace(traceId);
        if (data == null) {
            plugin.lang().send(player, "interaction.missing");
            return;
        }

        TraceConfig.InteractionConfig interaction = plugin.traceConfig().mannequin().interaction();
        if (!matches(interaction.clickType(), clickKind)) {
            return;
        }
        if (!player.getWorld().equals(data.location().getWorld())
                || player.getLocation().distanceSquared(data.location()) > MAX_INTERACTION_DISTANCE_SQUARED) {
            plugin.lang().send(player, "interaction.too-far");
            return;
        }
        if (plugin.traceConfig().ownerOnly()
                && !data.playerId().equals(player.getUniqueId())
                && !player.hasPermission("tracesdeath.admin.bypass")
                && !player.hasPermission("tracesdeath.admin")) {
            plugin.lang().send(player, "interaction.not-owner");
            return;
        }

        guiManager.openTraceGui(player, traceId);
        if (plugin.traceConfig().debug()) {
            plugin.getLogger().info(player.getName() + " 点击了 Mannequin 墓碑: " + traceId);
        }
    }

    private boolean matches(TraceConfig.InteractionConfig.ClickType configured, ClickKind actual) {
        return switch (configured) {
            case RIGHT_CLICK -> actual == ClickKind.RIGHT;
            case LEFT_CLICK -> actual == ClickKind.LEFT;
            case BOTH -> true;
        };
    }

    private UUID readTraceId(Entity entity) {
        if (!(entity instanceof Mannequin) && !(entity instanceof Interaction)) {
            return null;
        }
        String type = entity.getPersistentDataContainer().get(
                keys.traceType(), PersistentDataType.STRING);
        if (!"mannequin".equals(type)) {
            return null;
        }
        String value = entity.getPersistentDataContainer().get(
                keys.traceId(), PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("发现无效的 Mannequin 墓碑 UUID: " + value);
            return null;
        }
    }

    private void removeOrphanEntity(Entity entity) {
        if (entity instanceof Mannequin mannequin) {
            mannequin.getEquipment().clear();
        }
        entity.remove();
        if (plugin.traceConfig().debug()) {
            String role = entity.getPersistentDataContainer().get(
                    keys.entityRole(), PersistentDataType.STRING);
            plugin.getLogger().info(plugin.lang().text(
                    "storage.mannequin.orphan-removed", Map.of("role", role == null ? "unknown" : role)));
        }
    }

    private enum ClickKind {
        LEFT,
        RIGHT
    }
}
