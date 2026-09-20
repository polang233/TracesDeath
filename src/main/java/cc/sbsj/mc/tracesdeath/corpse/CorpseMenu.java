package cc.sbsj.mc.tracesdeath.corpse;

import cc.sbsj.mc.tracesdeath.appearance.CorpseAppearance;
import cc.sbsj.mc.tracesdeath.compat.ServerAdapter;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Display copies and session ownership only; item mutations belong to CorpseService. */
public final class CorpseMenu implements Listener {
    private static final int DIVIDER = 5, CLAIM_ALL = 6, INFO = 7, PAGE = 8;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
    private final JavaPlugin plugin;
    private final CorpseService service;
    private final CorpseAppearance appearance;
    private final ServerAdapter serverAdapter;
    private final Map<UUID, Session> viewers = new HashMap<>();
    private final Map<UUID, UUID> locks = new HashMap<>();

    public CorpseMenu(
            JavaPlugin plugin,
            CorpseService service,
            CorpseAppearance appearance,
            ServerAdapter serverAdapter) {
        this.plugin = plugin;
        this.service = service;
        this.appearance = appearance;
        this.serverAdapter = serverAdapter;
    }

    public void open(Player player, UUID id) {
        if (!service.access(player, id)) return;
        Session existing = viewers.get(player.getUniqueId());
        if (existing != null
                && existing.id.equals(id)
                && player.getOpenInventory().getTopInventory().getHolder() == existing) return;
        UUID viewer = locks.get(id);
        if (viewer != null && !viewer.equals(player.getUniqueId())) {
            player.sendMessage("这具遗体正由另一位玩家查看。");
            return;
        }
        player.closeInventory();
        Corpse corpse = service.get(id);
        Session session = new Session(id);
        session.inventory =
                serverAdapter.createInventory(
                        session, corpse.name + " 的遗体", appearance.usesCustomMenuTextures());
        viewers.put(player.getUniqueId(), session);
        locks.put(id, player.getUniqueId());
        render(session);
        player.openInventory(session.inventory);
        if (player.getOpenInventory().getTopInventory().getHolder() != session)
            release(player.getUniqueId(), session);
    }

    private void render(Session session) {
        Corpse corpse = service.get(session.id);
        if (corpse == null) return;
        Map<Integer, ItemStack> items = corpse.items();
        int pages = CorpseItems.pages(items);
        session.page = Math.min(session.page, pages - 1);
        session.inventory.clear();
        for (int slot = 0; slot < 54; slot++) {
            ItemStack item = items.get(CorpseItems.sourceSlot(session.page, slot));
            if (item != null) session.inventory.setItem(slot, item.clone());
        }
        String[] roles = {
            "empty-helmet", "empty-chestplate", "empty-leggings", "empty-boots", "empty-offhand"
        };
        String[] names = {"头盔", "胸甲", "护腿", "靴子", "副手"};
        for (int slot = 0; slot < 5; slot++)
            if (session.inventory.getItem(slot) == null) {
                session.inventory.setItem(
                        slot, styled(roles[slot], pane(false, names[slot] + " · 空")));
            }
        session.inventory.setItem(DIVIDER, styled("divider", pane(true, " ")));
        for (int slot = 9; slot < 18; slot++)
            session.inventory.setItem(slot, styled("separator", pane(true, " ")));
        session.inventory.setItem(
                CLAIM_ALL,
                styled(
                        "claim",
                        label(
                                Material.CHEST,
                                "一键领取",
                                service.claimAllToInventory()
                                        ? Arrays.asList("将所有物品收入背包", "装不下的遗体物品掉落在脚下")
                                        : Arrays.asList(
                                                "恢复所有物品到死亡时的原槽位",
                                                "原槽位已有的物品掉落在脚下",
                                                "额外掉落收入背包，装不下的掉落在脚下"))));
        List<String> info = new ArrayList<>(information(corpse));
        info.add(
                "当前页面："
                        + (session.page == 0 ? "背包与快捷栏" : "额外掉落 " + session.page)
                        + " · "
                        + (session.page + 1)
                        + "/"
                        + pages);
        info.add("点击将信息发送到聊天栏");
        session.inventory.setItem(
                INFO, styled("info", label(material("CLOCK", "WATCH"), "遗体信息", info)));
        session.inventory.setItem(
                PAGE,
                pages > 1
                        ? styled(
                                "page",
                                label(
                                        Material.ARROW,
                                        "翻页 · " + (session.page + 1) + "/" + pages,
                                        Arrays.asList("左键：下一页", "右键：上一页")))
                        : styled("filler", pane(true, " ")));
    }

    static List<String> information(Corpse corpse) {
        World world = Bukkit.getWorld(corpse.world);
        int count = corpse.items().values().stream().mapToInt(ItemStack::getAmount).sum();
        return Arrays.asList(
                "死亡者 ID：" + corpse.name,
                "UUID：" + corpse.owner,
                "死亡时间："
                        + (corpse.deathTime > 0
                                ? TIME.format(Instant.ofEpochMilli(corpse.deathTime))
                                : "未记录"),
                "位置："
                        + (world == null ? corpse.world : world.getName())
                        + " "
                        + (int) Math.floor(corpse.x)
                        + " "
                        + (int) Math.floor(corpse.y)
                        + " "
                        + (int) Math.floor(corpse.z),
                "剩余物品数量：" + count);
    }

    private ItemStack styled(String role, ItemStack item) {
        serverAdapter.configureMenuItem(role, item);
        return item;
    }

    private Material material(String modern, String legacy) {
        Material type = Material.matchMaterial(modern);
        return type == null ? Objects.requireNonNull(Material.matchMaterial(legacy)) : type;
    }

    private ItemStack pane(boolean dark, String text) {
        ItemStack item =
                label(
                        material(
                                dark ? "BLACK_STAINED_GLASS_PANE" : "GRAY_STAINED_GLASS_PANE",
                                "STAINED_GLASS_PANE"),
                        text,
                        Collections.emptyList());
        if (item.getType().name().equals("STAINED_GLASS_PANE"))
            item.setDurability((short) (dark ? 15 : 7));
        return item;
    }

    private ItemStack label(Material type, String text, List<String> lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RESET.toString() + ChatColor.WHITE + text);
        meta.setLore(lore.stream().map(line -> ChatColor.GRAY + line).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    static boolean isTakeClick(ClickType type) {
        return type == ClickType.LEFT
                || type == ClickType.RIGHT
                || type == ClickType.SHIFT_LEFT
                || type == ClickType.SHIFT_RIGHT;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Session)) return;
        Session session = (Session) event.getView().getTopInventory().getHolder();
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (viewers.get(player.getUniqueId()) != session
                || session.queued
                || !isTakeClick(event.getClick())) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        boolean shifted = event.isShiftClick();
        if (shifted && CorpseItems.sourceSlot(session.page, slot) < 0) return;
        final boolean previous = event.getClick() == ClickType.RIGHT;
        session.queued = true;
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            session.queued = false;
                            if (!player.isOnline() || viewers.get(player.getUniqueId()) != session)
                                return;
                            if (!service.access(player, session.id)) {
                                player.closeInventory();
                                return;
                            }
                            Corpse corpse = service.get(session.id);
                            if (slot == CLAIM_ALL) service.claimAll(player, session.id);
                            else if (slot == INFO) information(corpse).forEach(player::sendMessage);
                            else if (slot == PAGE) {
                                int pages = CorpseItems.pages(corpse.items());
                                session.page = (session.page + (previous ? pages - 1 : 1)) % pages;
                            } else {
                                int source = CorpseItems.sourceSlot(session.page, slot);
                                if (source >= 0 && corpse.items().containsKey(source))
                                    service.claim(player, session.id, source);
                            }
                            if (viewers.get(player.getUniqueId()) == session) render(session);
                        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Session)
            event.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Session)
            release(event.getPlayer().getUniqueId(), (Session) event.getInventory().getHolder());
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
        for (UUID id : new ArrayList<>(locks.keySet())) closeCorpse(id);
    }

    private static final class Session implements InventoryHolder {
        private final UUID id;
        private Inventory inventory;
        private int page;
        private boolean queued;

        private Session(UUID id) {
            this.id = id;
        }

        @Override
        public Inventory getInventory() {
            return Objects.requireNonNull(inventory);
        }
    }
}
