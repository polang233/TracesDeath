package cc.sbsj.mc.tracesDeath.gui;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.config.TraceConfig;
import cc.sbsj.mc.tracesDeath.trace.TraceCacheManager;
import cc.sbsj.mc.tracesDeath.trace.TraceClaimService;
import cc.sbsj.mc.tracesDeath.trace.TraceData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端控制的只取不存墓碑 GUI。界面内容不是权威库存。
 */
public final class TraceGuiManager implements Listener {
    private static final int GUI_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final TracesDeath plugin;
    private final TraceCacheManager cacheManager;
    private final TraceClaimService claimService;
    private final Map<UUID, TraceGuiHolder> openByPlayer = new HashMap<>();
    private final Map<UUID, UUID> traceLocks = new HashMap<>();

    public TraceGuiManager(TracesDeath plugin, TraceCacheManager cacheManager,
                           TraceClaimService claimService) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
        this.claimService = claimService;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openTraceGui(@NotNull Player player, @NotNull UUID traceId) {
        TraceData data = cacheManager.getTrace(traceId);
        if (data == null) {
            plugin.lang().send(player, "interaction.missing");
            return;
        }
        if (isLockedByOther(traceId, player.getUniqueId())) {
            plugin.lang().send(player, "gui.busy");
            return;
        }

        releasePlayer(player.getUniqueId());
        player.closeInventory();

        TraceGuiHolder holder = new TraceGuiHolder(traceId);
        Component title = plugin.lang().component(
                "gui.title", Map.of("player", data.playerName()));
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.bind(inventory);
        openByPlayer.put(player.getUniqueId(), holder);
        traceLocks.put(traceId, player.getUniqueId());
        render(holder);
        player.openInventory(inventory);
    }

    public boolean isLockedByOther(UUID traceId, UUID playerId) {
        UUID current = traceLocks.get(traceId);
        return current != null && !current.equals(playerId);
    }

    public boolean hasOpenGui(Player player) {
        return openByPlayer.containsKey(player.getUniqueId());
    }

    public UUID getOpenTraceId(Player player) {
        TraceGuiHolder holder = openByPlayer.get(player.getUniqueId());
        return holder == null ? null : holder.traceId();
    }

    public void shutdown() {
        for (UUID playerId : List.copyOf(openByPlayer.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.closeInventory();
            }
            releasePlayer(playerId);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof TraceGuiHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (openByPlayer.get(player.getUniqueId()) != holder) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < PAGE_SIZE) {
            claimVisibleItem(player, holder, rawSlot);
        } else if (rawSlot == PREVIOUS_SLOT && holder.page() > 0) {
            holder.setPage(holder.page() - 1);
            render(holder);
        } else if (rawSlot == NEXT_SLOT && holder.page() + 1 < holder.pageCount()) {
            holder.setPage(holder.page() + 1);
            render(holder);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TraceGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof TraceGuiHolder holder)) {
            return;
        }
        if (openByPlayer.remove(player.getUniqueId(), holder)) {
            traceLocks.remove(holder.traceId(), player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        releasePlayer(event.getPlayer().getUniqueId());
    }

    private void claimVisibleItem(Player player, TraceGuiHolder holder, int visibleSlot) {
        TraceData data = cacheManager.getTrace(holder.traceId());
        if (data == null) {
            player.closeInventory();
            plugin.lang().send(player, "interaction.missing");
            return;
        }

        int itemIndex = holder.page() * PAGE_SIZE + visibleSlot;
        TraceConfig.InteractionConfig interaction =
                plugin.traceConfig().interactionFor(data.storageType());
        if (interaction == null) {
            return;
        }

        TraceClaimService.ClaimResult result =
                claimService.claimItem(player, holder.traceId(), itemIndex, interaction);
        if (!result.found()) {
            player.closeInventory();
            plugin.lang().send(player, "interaction.missing");
            return;
        }
        if (result.movedAmount() == 0 && result.droppedAmount() == 0) {
            plugin.lang().send(player, "interaction.inventory-full",
                    Map.of("remaining", Integer.toString(result.remainingAmount())));
        }
        if (result.traceRemoved()) {
            player.closeInventory();
            plugin.lang().send(player, "gui.empty-removed");
            return;
        }
        render(holder);
    }

    private void render(TraceGuiHolder holder) {
        TraceData data = cacheManager.getTrace(holder.traceId());
        if (data == null) {
            return;
        }

        List<ItemStack> items = compact(data.items());
        int pageCount = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(holder.page(), pageCount - 1);
        holder.setPage(page);
        holder.setPageCount(pageCount);

        Inventory inventory = holder.getInventory();
        inventory.clear();
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, items.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(index - start, items.get(index).clone());
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, controlItem(
                    Material.ARROW, plugin.lang().component("gui.previous", Map.of())));
        }
        inventory.setItem(PAGE_SLOT, controlItem(
                Material.PAPER,
                plugin.lang().component("gui.page", Map.of(
                        "page", Integer.toString(page + 1),
                        "pages", Integer.toString(pageCount)
                ))
        ));
        if (page + 1 < pageCount) {
            inventory.setItem(NEXT_SLOT, controlItem(
                    Material.ARROW, plugin.lang().component("gui.next", Map.of())));
        }
    }

    private ItemStack controlItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private List<ItemStack> compact(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>(source.size());
        for (ItemStack item : source) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                result.add(item.clone());
            }
        }
        return result;
    }

    private void releasePlayer(UUID playerId) {
        TraceGuiHolder holder = openByPlayer.remove(playerId);
        if (holder != null) {
            traceLocks.remove(holder.traceId(), playerId);
        }
    }

    private static final class TraceGuiHolder implements InventoryHolder {
        private final UUID traceId;
        private Inventory inventory;
        private int page;
        private int pageCount = 1;

        private TraceGuiHolder(UUID traceId) {
            this.traceId = traceId;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        private UUID traceId() {
            return traceId;
        }

        private int page() {
            return page;
        }

        private void setPage(int page) {
            this.page = page;
        }

        private int pageCount() {
            return pageCount;
        }

        private void setPageCount(int pageCount) {
            this.pageCount = pageCount;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory == null) {
                throw new IllegalStateException("墓碑 GUI 尚未绑定库存");
            }
            return inventory;
        }
    }
}
