package cc.sbsj.mc.tracesDeath.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 物品栏工具类
 * <p>
 * 提供物品处理、压缩、填充等实用方法。
 */
public final class InventoryUtil {
    private InventoryUtil() {
    }

    /**
     * 压缩物品列表，移除空物品
     * @param items 原始物品集合
     * @return 压缩后的物品列表
     */
    public static List<ItemStack> compact(Collection<ItemStack> items) {
        List<ItemStack> compacted = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                compacted.add(item.clone());
            }
        }
        return compacted;
    }

    /**
     * 将物品填充到库存中，无法容纳的物品掉落到指定位置
     * @param inventory 目标库存
     * @param items 要填充的物品
     * @param fallback 掉落位置
     */
    public static void fillOrDrop(Inventory inventory, Collection<ItemStack> items, Location fallback) {
        World world = fallback.getWorld();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            
            // 尝试添加物品到库存
            Map<Integer, ItemStack> leftover = inventory.addItem(item.clone());
            
            // 如果有剩余物品，掉落到地面
            if (!leftover.isEmpty() && world != null) {
                for (ItemStack left : leftover.values()) {
                    world.dropItemNaturally(fallback, left);
                }
            }
        }
    }
    
    /**
     * 将库存中的所有物品掉落到指定位置
     * @param inventory 源库存
     * @param location 掉落位置
     */
    public static void dropAll(Inventory inventory, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                world.dropItemNaturally(location, item.clone());
            }
        }
        inventory.clear();
    }

    /**
     * 检查库存是否为空
     * @param inventory 要检查的库存
     * @return 如果为空返回 true
     */
    public static boolean isEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
