package cc.sbsj.mc.tracesDeath.events;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.config.TraceConfig;
import cc.sbsj.mc.tracesDeath.gui.TraceGuiManager;
import cc.sbsj.mc.tracesDeath.trace.TraceCacheManager;
import cc.sbsj.mc.tracesDeath.trace.TraceData;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 墓碑交互事件监听器
 * <p>
 * 处理玩家点击墓碑容器时的交互行为（打开GUI或直接获取）。
 */
public class TraceInteractEvents implements Listener {
    private final TracesDeath plugin;
    private final TraceKeys keys;
    private final TraceGuiManager guiManager;
    private final TraceCacheManager cacheManager;

    public TraceInteractEvents(TracesDeath plugin, TraceKeys keys, 
                                TraceGuiManager guiManager, TraceCacheManager cacheManager) {
        this.plugin = plugin;
        this.keys = keys;
        this.guiManager = guiManager;
        this.cacheManager = cacheManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        
        // 只处理左键或右键点击方块
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        
        // 检查是否是容器
        if (!(block.getState() instanceof Container)) {
            return;
        }
        
        // 检查是否是墓碑容器
        if (!(block.getState() instanceof TileState tileState)) {
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
        
        // 检查墓碑数据是否存在
        TraceData data = cacheManager.getTrace(traceId);
        if (data == null) {
            event.getPlayer().sendMessage(Component.text("该墓碑的数据已丢失").color(NamedTextColor.RED));
            return;
        }
        
        Player player = event.getPlayer();
        if (plugin.traceConfig().ownerOnly() && !data.playerId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("你不能打开其他玩家的墓碑").color(NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }
        
        // 获取当前存储类型的交互配置
        TraceConfig.InteractionConfig interactionConfig = getInteractionConfig();
        if (interactionConfig == null) {
            // 默认打开GUI
            event.setCancelled(true);
            guiManager.openTraceGui(player, traceId);
            return;
        }
        
        // 检查点击类型是否匹配
        if (!isClickTypeMatch(action, interactionConfig.clickType())) {
            return;
        }
        
        // 取消默认打开箱子的行为
        event.setCancelled(true);
        
        // 根据交互模式执行不同行为
        switch (interactionConfig.mode()) {
            case OPEN_GUI:
                guiManager.openTraceGui(player, traceId);
                break;
                
            case DIRECT_COLLECT:
                handleDirectCollect(player, data, interactionConfig);
                break;
        }
        
        if (plugin.traceConfig().debug()) {
            plugin.getLogger().info(player.getName() + " 点击了墓碑容器: " + traceId + 
                    " | 模式: " + interactionConfig.mode());
        }
    }
    
    /**
     * 获取当前存储类型的交互配置
     */
    private TraceConfig.InteractionConfig getInteractionConfig() {
        String storageType = plugin.traceConfig().storageType();
        return switch (storageType) {
            case "block" -> plugin.traceConfig().block().interaction();
            case "mannequin" -> plugin.traceConfig().mannequin().interaction();
            default -> null;
        };
    }
    
    /**
     * 检查点击类型是否匹配
     */
    private boolean isClickTypeMatch(Action action, TraceConfig.InteractionConfig.ClickType clickType) {
        return switch (clickType) {
            case RIGHT_CLICK -> action == Action.RIGHT_CLICK_BLOCK;
            case LEFT_CLICK -> action == Action.LEFT_CLICK_BLOCK;
            case BOTH -> true;
        };
    }
    
    /**
     * 处理直接获取模式
     */
    private void handleDirectCollect(Player player, TraceData data, 
                                      TraceConfig.InteractionConfig config) {
        List<ItemStack> remainingItems = new ArrayList<>();
        int collectedCount = 0;
        
        for (ItemStack item : data.items()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            
            // 如果启用自动装备且是装备物品，尝试装备
            if (config.autoEquip() && isArmor(item)) {
                if (tryEquipArmor(player, item)) {
                    collectedCount++;
                    continue;
                }
            }
            
            // 尝试将物品放入玩家背包
            if (canFitInInventory(player, item)) {
                player.getInventory().addItem(item.clone());
                collectedCount++;
            } else {
                // 背包满了，根据冲突处理方式决定
                if (config.conflictHandling() == TraceConfig.InteractionConfig.ConflictHandling.DROP) {
                    // 丢地上
                    player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                    collectedCount++;
                } else {
                    // TRY_INVENTORY: 已经尝试过了，放不下就保留在墓碑中
                    remainingItems.add(item);
                }
            }
        }
        
        // 更新墓碑数据
        cacheManager.updateTraceItems(data.traceId(), remainingItems);
        
        // 发送消息给玩家
        if (collectedCount > 0) {
            player.sendMessage(Component.text("已获取 " + collectedCount + " 个物品")
                    .color(NamedTextColor.GREEN));
        }
        
        // 如果墓碑为空且配置启用自动清理，则移除墓碑
        if (cacheManager.isTraceEmpty(data.traceId())) {
            boolean autoRemove = false;
            String storageType = plugin.traceConfig().storageType();
            if ("block".equals(storageType)) {
                autoRemove = plugin.traceConfig().block().autoRemoveWhenEmpty();
            } else if ("mannequin".equals(storageType)) {
                autoRemove = plugin.traceConfig().mannequin().autoRemoveWhenEmpty();
            }
            
            if (autoRemove) {
                plugin.traceManager().removeTrace(data.traceId(), false);
                cacheManager.removeTrace(data.traceId());
                player.sendMessage(Component.text("墓碑已清空并移除").color(NamedTextColor.GRAY));
            }
        }
    }
    
    /**
     * 判断物品是否为护甲
     */
    private boolean isArmor(ItemStack item) {
        Material type = item.getType();
        return type.name().endsWith("_HELMET") ||
               type.name().endsWith("_CHESTPLATE") ||
               type.name().endsWith("_LEGGINGS") ||
               type.name().endsWith("_BOOTS") ||
               type == Material.ELYTRA;
    }
    
    /**
     * 尝试装备护甲到玩家身上
     */
    private boolean tryEquipArmor(Player player, ItemStack item) {
        Material type = item.getType();
        var inventory = player.getInventory();
        
        if (type.name().endsWith("_HELMET")) {
            if (inventory.getHelmet() == null || inventory.getHelmet().getType().isAir()) {
                inventory.setHelmet(item.clone());
                return true;
            }
        } else if (type.name().endsWith("_CHESTPLATE") || type == Material.ELYTRA) {
            if (inventory.getChestplate() == null || inventory.getChestplate().getType().isAir()) {
                inventory.setChestplate(item.clone());
                return true;
            }
        } else if (type.name().endsWith("_LEGGINGS")) {
            if (inventory.getLeggings() == null || inventory.getLeggings().getType().isAir()) {
                inventory.setLeggings(item.clone());
                return true;
            }
        } else if (type.name().endsWith("_BOOTS")) {
            if (inventory.getBoots() == null || inventory.getBoots().getType().isAir()) {
                inventory.setBoots(item.clone());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查物品是否可以放入玩家背包
     */
    private boolean canFitInInventory(Player player, ItemStack item) {
        // 检查是否有空位或相同物品可以堆叠
        for (ItemStack existing : player.getInventory().getContents()) {
            if (existing == null || existing.getType().isAir()) {
                return true; // 有空位
            }
            if (existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) {
                return true; // 可以堆叠
            }
        }
        return false;
    }
}
