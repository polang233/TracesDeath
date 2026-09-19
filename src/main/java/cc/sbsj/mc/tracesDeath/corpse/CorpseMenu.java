package cc.sbsj.mc.tracesDeath.corpse;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Inventory contents are display copies. Every withdrawal goes through CorpseService. */
public final class CorpseMenu implements Listener {
    private static final int CLAIM_ALL = 5;
    private static final int INFO = 6;
    private static final int PREVIOUS = 7;
    private static final int NEXT = 8;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());
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
        if (player.getOpenInventory().getTopInventory().getHolder() != session) release(player.getUniqueId(), session);
    }

    private void render(Session session) {
        Corpse corpse = service.get(session.id);
        if (corpse == null) return;
        var items = corpse.items();
        int pages = CorpseItems.pages(items);
        session.page = Math.min(session.page, pages - 1);
        session.inventory.clear();
        for (int slot = 0; slot < 54; slot++) {
            int source = CorpseItems.sourceSlot(session.page, slot);
            ItemStack item = items.get(source);
            if (item != null) session.inventory.setItem(slot, item.clone());
        }
        String[] names = {"头盔", "胸甲", "护腿", "靴子", "副手"};
        for (int slot = 0; slot < 5; slot++) {
            if (session.inventory.getItem(slot) == null) session.inventory.setItem(slot,
                    label(Material.GRAY_STAINED_GLASS_PANE, names[slot] + " · 空", List.of()));
        }
        for (int slot = 9; slot < 18; slot++) session.inventory.setItem(slot,
                label(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        session.inventory.setItem(CLAIM_ALL, label(Material.CHEST, "一键领取",
                service.claimAllToInventory()
                        ? List.of("将所有物品收入背包", "装不下的遗体物品掉落在脚下")
                        : List.of("恢复所有物品到死亡时的原槽位", "原槽位已有的物品掉落在脚下", "额外掉落收入背包，装不下的掉落在脚下")));
        List<String> info = new ArrayList<>(information(corpse));
        info.add("当前页面：" + (session.page == 0 ? "背包与快捷栏" : "额外掉落 " + session.page)
                + " · " + (session.page + 1) + "/" + pages);
        info.add("点击将信息发送到聊天栏");
        session.inventory.setItem(INFO, label(Material.CLOCK, "遗体信息", info));
        session.inventory.setItem(PREVIOUS, session.page > 0
                ? label(Material.ARROW, "上一页", List.of())
                : label(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        session.inventory.setItem(NEXT, session.page + 1 < pages
                ? label(Material.ARROW, "额外掉落 / 下一页", List.of())
                : label(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
    }

    static List<String> information(Corpse corpse) {
        World world = Bukkit.getWorld(corpse.world);
        int count = corpse.items().values().stream().mapToInt(ItemStack::getAmount).sum();
        return List.of(
                "死亡者 ID：" + corpse.name,
                "UUID：" + corpse.owner,
                "死亡时间：" + (corpse.deathTime > 0 ? TIME.format(Instant.ofEpochMilli(corpse.deathTime)) : "未记录"),
                "位置：" + (world == null ? corpse.world : world.getName()) + " "
                        + (int) Math.floor(corpse.x) + " " + (int) Math.floor(corpse.y) + " " + (int) Math.floor(corpse.z),
                "剩余物品数量：" + count);
    }

    private ItemStack label(Material type, String text, List<String> lore) {
        ItemStack item = new ItemStack(type);
        item.editMeta(meta -> {
            meta.displayName(Component.text(text, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)).toList());
        });
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
            Corpse corpse = service.get(session.id);
            if (slot == CLAIM_ALL) service.claimAll(player, session.id);
            else if (slot == INFO) information(corpse).forEach(player::sendMessage);
            else if (slot == PREVIOUS && session.page > 0) session.page--;
            else if (slot == NEXT && session.page + 1 < CorpseItems.pages(corpse.items())) session.page++;
            else {
                int source = CorpseItems.sourceSlot(session.page, slot);
                if (source >= 0 && corpse.items().containsKey(source)) service.claim(player, session.id, source);
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
