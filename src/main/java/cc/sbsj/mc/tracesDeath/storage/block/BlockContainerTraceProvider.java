package cc.sbsj.mc.tracesDeath.storage.block;

import cc.sbsj.mc.tracesDeath.storage.PlacementResult;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageProvider;
import cc.sbsj.mc.tracesDeath.trace.TraceContext;
import cc.sbsj.mc.tracesDeath.trace.TraceData;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import cc.sbsj.mc.tracesDeath.util.LocationUtil;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * 方块容器墓碑提供者
 * <p>
 * 使用箱子、木桶、潜影盒等方块作为墓碑容器。
 */
public final class BlockContainerTraceProvider implements TraceStorageProvider {
    private final TraceKeys keys;

    public BlockContainerTraceProvider(TraceKeys keys) {
        this.keys = keys;
    }

    @Override
    public String id() {
        return "block";
    }
    
    @Override
    public @NotNull String displayName() {
        return "方块容器";
    }

    @Override
    public boolean supports(TraceContext context) {
        Material material = context.config().block().material();
        return material.isBlock() && isKnownContainerMaterial(material);
    }

    @Override
    public PlacementResult place(TraceContext context) {
        Block target = LocationUtil.findReplaceableBlock(context.location(), context.config().searchRadius());
        if (target == null) {
            // 如果启用了强制放置，尝试在死亡点直接放置
            if (context.config().forcePlace()) {
                target = context.location().getBlock();
            } else {
                return PlacementResult.failure(context.lang().text("storage.block.no-place"));
            }
        }

        target.setType(context.config().block().material(), false);
        if (!(target.getState() instanceof Container container)) {
            target.setType(Material.AIR, false);
            return PlacementResult.failure(context.lang().text("storage.block.not-container",
                    Map.of("material", context.config().block().material().name())));
        }

        if (container instanceof TileState tileState) {
            tileState.getPersistentDataContainer().set(keys.traceId(), PersistentDataType.STRING, context.traceId().toString());
            tileState.getPersistentDataContainer().set(keys.traceType(), PersistentDataType.STRING, id());
            tileState.getPersistentDataContainer().set(keys.owner(), PersistentDataType.STRING, context.player().getUniqueId().toString());
            
            // 先更新 TileState 以保存 PDC 数据
            tileState.update(true, false);
        }
        container.customName(context.lang().component("storage.display-name", Map.of("player", context.player().getName())));
        
        // 注意：不再将物品真正放入箱子，物品存储在 TraceCacheManager 中
        // 玩家右键点击时会打开虚拟GUI界面
        
        // 更新方块状态以保存元数据
        container.update(true, false);
        
        return PlacementResult.success(context.lang().text("storage.block.created",
                Map.of("material", context.config().block().material().name())), target.getLocation());
    }
    
    @Override
    public boolean cleanup(@NotNull TraceData data) {
        Block block = data.location().getBlock();
        if (block.getState() instanceof Container container && container instanceof TileState tileState) {
            String storedId = tileState.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
            if (data.traceId().toString().equals(storedId)) {
                // 注意：物品存储在缓存中，这里只需要移除方块
                // 如果需要掉落物品，应该由 TraceManager 从缓存中获取并掉落
                block.setType(Material.AIR, false);
                return true;
            }
        }
        return true;
    }
    
    @Override
    public boolean isValid(@NotNull TraceData data) {
        Block block = data.location().getBlock();
        if (block.getState() instanceof Container container && container instanceof TileState tileState) {
            String storedId = tileState.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
            return data.traceId().toString().equals(storedId);
        }
        return false;
    }

    private static boolean isKnownContainerMaterial(Material material) {
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX,
                    MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX,
                    PINK_SHULKER_BOX, GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX,
                    PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX, BROWN_SHULKER_BOX, GREEN_SHULKER_BOX,
                    RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }
}
