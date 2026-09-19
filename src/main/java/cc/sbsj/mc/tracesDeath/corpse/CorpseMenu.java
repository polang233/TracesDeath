package cc.sbsj.mc.tracesDeath.corpse;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Inventory contents are display copies. Every withdrawal goes through CorpseService. */
public final class CorpseMenu implements Listener {
    private final JavaPlugin plugin;
    private final CorpseService service;
    private final Map<UUID, Session> viewers = new HashMap<>();
    private final Map<UUID, UUID> locks = new HashMap<>();

    public CorpseMenu(JavaPlugin plugin, CorpseService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void open(Player player, UUID id) {
        if (!service.access(player, id)) return;
        Session existing = viewers.get(player.getUniqueId());
        if (existing != null && existing.id.equals(id) && player.getOpenInventory().getTopInventory().getHolder() == existing) return;
        UUID viewer = locks.get(id);
        if (viewer != null && !viewer.equals(player.getUniqueId())) {
            player.sendMessage("这具遗体正由另一位玩家查看。");
            return;
        }
        player.closeInventory();
        Corpse corpse = service.get(id);
        Session session = new Session(id);
        session.inventory = Bukkit.createInventory(session, 54, Component.text(corpse.name + " 的遗体"));
        viewers.put(player.getUniqueId(), session);
        locks.put(id, player.getUniqueId());
        render(session);
        player.openInventory(session.inventory);
        // Another plugin may cancel InventoryOpenEvent.
        if (player.getOpenInventory().getTopInventory().getHolder() != session) release(player.getUniqueId(), session);
    }

    private void render(Session session) {
        Corpse corpse = service.get(session.id);
        if (corpse == null) return;
        var items = corpse.items();
        session.page = Math.min(session.page, CorpseItems.pages(items) - 1);
        session.inventory.clear();
        for (int slot = 0; slot < 45; slot++) {
            int source = CorpseItems.sourceSlot(session.page, slot);
            ItemStack item = items.get(source);
            if (item != null) session.inventory.setItem(slot, item.clone());
        }
        if (session.page == 0) {
            String[] names = {"头盔", "胸甲", "护腿", "靴子", "副手"};
            for (int slot = 0; slot < 5; slot++) {
                if (session.inventory.getItem(slot) == null) session.inventory.setItem(slot, label(Material.GRAY_STAINED_GLASS_PANE, names[slot] + " · 空"));
            }
            for (int slot = 5; slot < 9; slot++) session.inventory.setItem(slot, label(Material.BLACK_STAINED_GLASS_PANE, " "));
        }
        if (session.page > 0) session.inventory.setItem(45, label(Material.ARROW, "上一页"));
        session.inventory.setItem(49, label(Material.PAPER, session.page == 0 ? "装备 / 背包 / 快捷栏 · 点击领取" : "额外掉落 · " + session.page));
        if (session.page + 1 < CorpseItems.pages(items)) session.inventory.setItem(53, label(Material.ARROW, "额外掉落 / 下一页"));
    }

    private ItemStack label(Material type, String text) {
        ItemStack item = new ItemStack(type);
        item.editMeta(meta -> meta.displayName(Component.text(text)));
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Session session)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || viewers.get(player.getUniqueId()) != session) return;
        if (session.queued || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        session.queued = true;
        // Closing/reopening inventories inside InventoryClickEvent is unsafe; process next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            session.queued = false;
            if (!player.isOnline() || viewers.get(player.getUniqueId()) != session) return;
            if (!service.access(player, session.id)) { player.closeInventory(); return; }
            if (slot == 45 && session.page > 0) session.page--;
            else if (slot == 53 && session.page + 1 < CorpseItems.pages(service.get(session.id).items())) session.page++;
            else {
                int source = CorpseItems.sourceSlot(session.page, slot);
                if (source >= 0) service.claim(player, session.id, source);
            }
            if (viewers.get(player.getUniqueId()) == session) render(session);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Session) event.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Session session) release(event.getPlayer().getUniqueId(), session);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Session session = viewers.get(event.getPlayer().getUniqueId());
        if (session != null) release(event.getPlayer().getUniqueId(), session);
    }

    private void release(UUID player, Session session) {
        if (viewers.remove(player, session)) locks.remove(session.id, player);
    }

    public void closeCorpse(UUID id) {
        UUID viewer = locks.get(id);
        if (viewer == null) return;
        Player player = Bukkit.getPlayer(viewer);
        Session session = viewers.get(viewer);
        if (player != null) player.closeInventory();
        if (session != null) release(viewer, session);
    }

    public void shutdown() {
        for (UUID id : List.copyOf(locks.keySet())) closeCorpse(id);
    }

    private static final class Session implements InventoryHolder {
        private final UUID id;
        private Inventory inventory;
        private int page;
        private boolean queued;
        private Session(UUID id) { this.id = id; }
        @Override public Inventory getInventory() { return Objects.requireNonNull(inventory); }
    }
}
