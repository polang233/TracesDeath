package cc.sbsj.mc.tracesDeath.util;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * 位置工具类
 */
public final class LocationUtil {
    private LocationUtil() {
    }

    /**
     * 在指定范围内查找可替换的方块
     */
    public static Block findReplaceableBlock(Location origin, int radius) {
        Block base = origin.getBlock();
        if (isReplaceable(base)) {
            return base;
        }
        for (int y = 0; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = base.getRelative(x, y, z);
                    if (isReplaceable(block)) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    // 判断方块是否可被替换（空气、液体等）
    private static boolean isReplaceable(Block block) {
        return block.isEmpty() || block.isLiquid() || block.getType().isAir();
    }
}
