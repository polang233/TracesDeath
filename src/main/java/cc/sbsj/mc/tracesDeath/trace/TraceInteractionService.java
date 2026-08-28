package cc.sbsj.mc.tracesDeath.trace;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.config.TraceConfig;
import cc.sbsj.mc.tracesDeath.gui.TraceGuiManager;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * 统一方块和 Mannequin 墓碑的访问、点击方式与领取入口。
 */
public final class TraceInteractionService {
    private final TracesDeath plugin;
    private final TraceCacheManager cacheManager;
    private final TraceGuiManager guiManager;
    private final TraceClaimService claimService;

    public TraceInteractionService(TracesDeath plugin, TraceCacheManager cacheManager,
                                   TraceGuiManager guiManager, TraceClaimService claimService) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
        this.guiManager = guiManager;
        this.claimService = claimService;
    }

    public boolean interact(Player player, UUID traceId, ClickKind clickKind) {
        TraceData data = cacheManager.getTrace(traceId);
        if (data == null) {
            plugin.lang().send(player, "interaction.missing");
            return false;
        }

        TraceConfig.InteractionConfig interaction = plugin.traceConfig().interactionFor(data.storageType());
        if (interaction == null || !matches(interaction.clickType(), clickKind)) {
            return false;
        }
        if (!player.getWorld().equals(data.location().getWorld())
                || player.getLocation().distanceSquared(data.location())
                > plugin.traceConfig().interactionDistanceSquared()) {
            plugin.lang().send(player, "interaction.too-far");
            return false;
        }
        if (plugin.traceConfig().ownerOnly()
                && !data.playerId().equals(player.getUniqueId())
                && !player.hasPermission("tracesdeath.admin.bypass")
                && !player.hasPermission("tracesdeath.admin")) {
            plugin.lang().send(player, "interaction.not-owner");
            return false;
        }

        boolean directCollect = player.isSneaking()
                || interaction.mode() == TraceConfig.InteractionConfig.InteractionMode.DIRECT_COLLECT;
        if (!directCollect) {
            guiManager.openTraceGui(player, traceId);
            return true;
        }
        if (guiManager.isLockedByOther(traceId, player.getUniqueId())) {
            plugin.lang().send(player, "gui.busy");
            return false;
        }

        TraceClaimService.ClaimResult result =
                claimService.claimAll(player, traceId, interaction);
        sendClaimResult(player, result);
        return result.found();
    }

    public void sendClaimResult(Player player, TraceClaimService.ClaimResult result) {
        if (!result.found()) {
            plugin.lang().send(player, "interaction.missing");
            return;
        }
        if (result.movedAmount() == 0 && result.droppedAmount() == 0) {
            plugin.lang().send(player, "interaction.inventory-full",
                    Map.of("remaining", Integer.toString(result.remainingAmount())));
            return;
        }
        plugin.lang().send(player, "interaction.collected", Map.of(
                "moved", Integer.toString(result.movedAmount()),
                "dropped", Integer.toString(result.droppedAmount()),
                "remaining", Integer.toString(result.remainingAmount())
        ));
    }

    private boolean matches(TraceConfig.InteractionConfig.ClickType configured, ClickKind actual) {
        return switch (configured) {
            case RIGHT_CLICK -> actual == ClickKind.RIGHT;
            case LEFT_CLICK -> actual == ClickKind.LEFT;
            case BOTH -> true;
        };
    }

    public enum ClickKind {
        LEFT,
        RIGHT
    }
}
