package cc.sbsj.mc.tracesDeath.events;

import cc.sbsj.mc.tracesDeath.trace.TraceInteractionService;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/**
 * 方块墓碑只负责解析 UUID，访问与领取逻辑统一交给 TraceInteractionService。
 */
public final class TraceInteractEvents implements Listener {
    private final TraceKeys keys;
    private final TraceInteractionService interactionService;

    public TraceInteractEvents(TraceKeys keys, TraceInteractionService interactionService) {
        this.keys = keys;
        this.interactionService = interactionService;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if ((action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK)
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Container)
                || !(block.getState() instanceof TileState tileState)) {
            return;
        }

        String value = tileState.getPersistentDataContainer().get(
                keys.traceId(), PersistentDataType.STRING);
        if (value == null) {
            return;
        }

        UUID traceId;
        try {
            traceId = UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        TraceInteractionService.ClickKind clickKind =
                action == Action.RIGHT_CLICK_BLOCK
                        ? TraceInteractionService.ClickKind.RIGHT
                        : TraceInteractionService.ClickKind.LEFT;
        interactionService.interact(event.getPlayer(), traceId, clickKind);
    }
}
