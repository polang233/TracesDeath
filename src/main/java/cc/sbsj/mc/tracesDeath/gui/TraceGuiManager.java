package cc.sbsj.mc.tracesDeath.gui;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.trace.TraceCacheManager;
import cc.sbsj.mc.tracesDeath.trace.TraceData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * 墓碑虚拟GUI管理器
 * <p>
 * 负责创建和管理墓碑的虚拟背包界面。
 * 使用54格大箱子界面，按类别显示物品（装备槽、物品栏等）。
 */
public final class TraceGuiManager implements Listener {
    private final TracesDeath plugin;
    private final TraceCacheManager cacheManager;
    private final Map<UUID, UUID> openGuis = new HashMap<>(); // playerUUID -> traceId
    
    // GUI布局常量
    private static final int GUI_SIZE = 54; // 6行 x 9列
    private static final int EQUIPMENT_ROW = 0; // 第1行：装备槽
    private static final int SEPARATOR_ROW_1 = 1; // 第2行：分隔板
    private static final int INVENTORY_START_ROW = 2; // 第3-6行：物品栏
    
    public TraceGuiManager(@NotNull TracesDeath plugin, @NotNull TraceCacheManager cacheManager) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * 打开墓碑GUI
     */
    public void openTraceGui(@NotNull Player player, @NotNull UUID traceId) {
        TraceData data = cacheManager.getTrace(traceId);
        if (data == null) {
            player.sendMessage(Component.text("墓碑不存在或已被移除").color(NamedTextColor.RED));
            return;
        }
        
        // 创建虚拟库存
        String title = plugin.lang().text("gui.title", 
                Map.of("player", data.playerName()));
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, Component.text(title));
        
        // 填充物品到GUI
        fillGuiWithItems(gui, data.items());
        
        // 记录玩家打开了哪个墓碑
        openGuis.put(player.getUniqueId(), traceId);
        
        // 打开GUI
        player.openInventory(gui);
        
        if (plugin.traceConfig().debug()) {
            plugin.getLogger().info(player.getName() + " 打开了墓碑GUI: " + traceId);
        }
    }
    
    /**
     * 将物品填充到GUI中，按类别排列
     */
    private void fillGuiWithItems(@NotNull Inventory gui, @NotNull List<ItemStack> items) {
        // 清空GUI
        gui.clear();
        
        // 添加分隔板和标签
        addSeparatorsAndLabels(gui);
        
        // 分类物品
        List<ItemStack> equipment = new ArrayList<>();
        List<ItemStack> inventory = new ArrayList<>();
        
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            
            if (isEquipment(item)) {
                equipment.add(item);
            } else {
                inventory.add(item);
            }
        }
        
        // 放置装备（第1行，最多9个）
        for (int i = 0; i < Math.min(equipment.size(), 9); i++) {
            gui.setItem(EQUIPMENT_ROW * 9 + i, equipment.get(i));
        }
        
        // 放置物品栏物品（第3-6行，共36格）
        int slot = INVENTORY_START_ROW * 9;
        for (ItemStack item : inventory) {
            if (slot >= GUI_SIZE) {
                break; // GUI已满
            }
            gui.setItem(slot++, item);
        }
    }
    
    /**
     * 添加分隔板和标签
     */
    private void addSeparatorsAndLabels(@NotNull Inventory gui) {
        // 第2行作为分隔板，使用灰色玻璃板
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = separator.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").color(NamedTextColor.GRAY));
            separator.setItemMeta(meta);
        }
        
        for (int i = 0; i < 9; i++) {
            gui.setItem(SEPARATOR_ROW_1 * 9 + i, separator);
        }
        
        // 在装备行添加标签（可选）
        ItemStack label = new ItemStack(Material.NAME_TAG);
        ItemMeta labelMeta = label.getItemMeta();
        if (labelMeta != null) {
            labelMeta.displayName(Component.text("装备栏").color(NamedTextColor.YELLOW));
            label.setItemMeta(labelMeta);
        }
        // 可以放在某个特定位置作为标识
    }
    
    /**
     * 判断物品是否为装备
     */
    private boolean isEquipment(@NotNull ItemStack item) {
        Material type = item.getType();
        return type.name().endsWith("_HELMET") ||
               type.name().endsWith("_CHESTPLATE") ||
               type.name().endsWith("_LEGGINGS") ||
               type.name().endsWith("_BOOTS") ||
               type == Material.ELYTRA ||
               type == Material.SHIELD ||
               type.name().contains("SWORD") ||
               type.name().contains("AXE") ||
               type.name().contains("PICKAXE") ||
               type.name().contains("SHOVEL") ||
               type.name().contains("HOE") ||
               type == Material.BOW ||
               type == Material.CROSSBOW ||
               type == Material.TRIDENT;
    }
    
    /**
     * 处理GUI点击事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        UUID traceId = openGuis.get(player.getUniqueId());
        if (traceId == null) {
            return; // 不是我们的GUI
        }
        
        // 检查是否点击了分隔板或标签物品，禁止拿走
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && isSeparator(clickedItem)) {
            event.setCancelled(true);
            return;
        }
        
        // 检查是否是顶部库存（GUI）的点击
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getInventory())) {
            // 允许玩家拿取物品，但需要延迟更新缓存
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                updateTraceFromGui(player, traceId, event.getInventory());
            }, 1L);
        }
    }
    
    /**
     * 处理GUI拖拽事件
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        UUID traceId = openGuis.get(player.getUniqueId());
        if (traceId == null) {
            return;
        }
        
        // 检查是否涉及分隔板槽位，禁止拖拽到这些位置
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize()) {
                ItemStack item = event.getInventory().getItem(slot);
                if (item != null && isSeparator(item)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        
        // 延迟更新
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateTraceFromGui(player, traceId, event.getInventory());
        }, 1L);
    }
    
    /**
     * 处理GUI关闭事件
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        
        UUID traceId = openGuis.remove(player.getUniqueId());
        if (traceId == null) {
            return;
        }
        
        // 更新墓碑数据
        updateTraceFromGui(player, traceId, event.getInventory());
        
        // 检查是否为空，如果为空且配置启用自动清理，则移除墓碑
        if (cacheManager.isTraceEmpty(traceId)) {
            boolean autoRemove = false;
            String storageType = plugin.traceConfig().storageType();
            if ("block".equals(storageType)) {
                autoRemove = plugin.traceConfig().block().autoRemoveWhenEmpty();
            } else if ("mannequin".equals(storageType)) {
                autoRemove = plugin.traceConfig().mannequin().autoRemoveWhenEmpty();
            }
            
            if (autoRemove) {
                // 移除墓碑（由TraceManager处理实际的方块/实体移除）
                plugin.traceManager().removeTrace(traceId, false);
                cacheManager.removeTrace(traceId);
                
                if (plugin.traceConfig().debug()) {
                    plugin.getLogger().info("墓碑已清空并自动移除: " + traceId);
                }
            }
        }
        
        if (plugin.traceConfig().debug()) {
            plugin.getLogger().info(player.getName() + " 关闭了墓碑GUI: " + traceId);
        }
    }
    
    /**
     * 从GUI更新墓碑数据
     */
    private void updateTraceFromGui(@NotNull Player player, @NotNull UUID traceId, 
                                     @NotNull Inventory gui) {
        List<ItemStack> remainingItems = new ArrayList<>();
        for (ItemStack item : gui.getContents()) {
            if (item != null && !item.getType().isAir() && !isSeparator(item)) {
                remainingItems.add(item.clone());
            }
        }
        
        cacheManager.updateTraceItems(traceId, remainingItems);
    }
    
    /**
     * 判断是否为分隔板物品
     */
    private boolean isSeparator(@NotNull ItemStack item) {
        return item.getType() == Material.GRAY_STAINED_GLASS_PANE ||
               item.getType() == Material.NAME_TAG;
    }
    
    /**
     * 检查玩家是否打开了某个墓碑GUI
     */
    public boolean hasOpenGui(@NotNull Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }
    
    /**
     * 获取玩家当前打开的墓碑ID
     */
    public UUID getOpenTraceId(@NotNull Player player) {
        return openGuis.get(player.getUniqueId());
    }
}
