package cc.sbsj.mc.tracesdeath.corpse;

import cc.sbsj.mc.tracesdeath.appearance.CorpseAppearance;
import cc.sbsj.mc.tracesdeath.compat.ServerAdapter;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** All inventory/state transitions are serialized on the server thread. */
public final class CorpseService implements Listener {
    private final JavaPlugin plugin;
    private final CorpseStore store;
    private final Map<UUID, Corpse> corpses = new LinkedHashMap<>();
    private final CorpseAppearance appearance;
    private final ServerAdapter serverAdapter;
    private final boolean ownerOnly;
    private final boolean claimAllToInventory;
    private CorpseMenu menu;
    private CorpseEntities entities;

    public CorpseService(
            JavaPlugin plugin,
            CorpseStore store,
            boolean ownerOnly,
            boolean claimAllToInventory,
            CorpseAppearance appearance,
            ServerAdapter serverAdapter)
            throws Exception {
        this.plugin = plugin;
        this.appearance = appearance;
        this.serverAdapter = serverAdapter;
        this.store = store;
        this.ownerOnly = ownerOnly;
        this.claimAllToInventory = claimAllToInventory;
        for (Corpse corpse : store.load()) corpses.put(corpse.id, corpse);
    }

    public void start() {
        menu = new CorpseMenu(plugin, this, appearance, serverAdapter);
        entities =
                new CorpseEntities(
                        plugin,
                        corpses,
                        menu::open,
                        menu::closeCorpse,
                        serverAdapter.createCorpseRenderer(appearance.getType()));
        PluginManager events = Bukkit.getPluginManager();
        events.registerEvents(this, plugin);
        events.registerEvents(menu, plugin);
        events.registerEvents(entities, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) recover(player);
        entities.cleanupLoadedEntities();
        entities.reconcileAll();
        // A single repair task also covers entities removed by external cleanup plugins.
        Bukkit.getScheduler().runTaskTimer(plugin, entities::reconcileAll, 100, 100);
    }

    public void stop() {
        if (menu != null) menu.shutdown();
        if (entities != null) entities.shutdown();
        // Every accepted mutation was already persisted; never overwrite disk from a shutdown
        // cache.
    }

    public Corpse get(UUID id) {
        return corpses.get(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void death(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (event.getKeepInventory() || !player.hasPermission("tracesdeath.use")) return;
        Map<Integer, ItemStack> items =
                CorpseItems.capture(player.getInventory().getContents(), event.getDrops());
        if (items.isEmpty()) return;
        Location location = serverAdapter.findGroundLocation(player.getLocation());
        double y = location.getY();
        Corpse corpse =
                new Corpse(
                        UUID.randomUUID(),
                        player.getUniqueId(),
                        player.getName(),
                        player.getWorld().getUID(),
                        location.getX(),
                        y,
                        location.getZ(),
                        Math.round(location.getYaw() / 90f) * 90f,
                        player.getInventory().getHeldItemSlot(),
                        serverAdapter.captureSkin(player),
                        System.currentTimeMillis(),
                        items);
        try {
            store.save(corpse);
        } catch (Exception exception) {
            failure("保存死亡物品失败，保留原生掉落", corpse.id, exception);
            return;
        }
        corpses.put(corpse.id, corpse);
        event.getDrops().clear();
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (corpses.containsKey(corpse.id)) entities.show(corpse);
                        });
    }

    public boolean access(Player player, UUID id) {
        Corpse corpse = corpses.get(id);
        if (corpse == null || corpse.empty()) return false;
        if (corpse.pending() != null || hasPending(player.getUniqueId())) {
            player.sendMessage("遗体有待恢复的领取记录，请重新登录或联系管理员。");
            return false;
        }
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return false;
        if (ownerOnly
                && !corpse.owner.equals(player.getUniqueId())
                && !player.hasPermission("tracesdeath.admin")) {
            player.sendMessage("你只能领取自己的遗体。");
            return false;
        }
        if (!player.getWorld().getUID().equals(corpse.world)
                || player.getLocation()
                                .distanceSquared(
                                        new Location(
                                                player.getWorld(), corpse.x, corpse.y, corpse.z))
                        > 36) {
            player.sendMessage("请靠近遗体后再领取。");
            return false;
        }
        return true;
    }

    private boolean hasPending(UUID player) {
        return corpses.values().stream()
                .anyMatch(c -> c.pending() != null && c.pending().player().equals(player));
    }

    public boolean claimAllToInventory() {
        return claimAllToInventory;
    }

    public void claim(Player player, UUID id, int slot) {
        if (!access(player, id)) return;
        Corpse corpse = corpses.get(id);
        Map<Integer, ItemStack> items = corpse.items();
        ItemStack source = items.get(slot);
        if (source == null) return;
        Map<Integer, ItemStack> before = CorpseItems.snapshot(player.getInventory().getContents());
        CorpseItems.Transfer transfer =
                CorpseItems.plan(before, source, player.getInventory().getMaxStackSize());
        if (transfer.remaining() == source.getAmount()) {
            player.sendMessage("背包已满，物品仍保留在遗体中。");
            return;
        }
        if (transfer.remaining() == 0) items.remove(slot);
        else {
            source.setAmount(transfer.remaining());
            items.put(slot, source);
        }
        transfer(
                player,
                corpse,
                new Corpse.PendingClaim(
                        player.getUniqueId(),
                        41,
                        before,
                        transfer.after(),
                        items,
                        Collections.emptyList(),
                        false));
    }

    public void claimAll(Player player, UUID id) {
        if (!access(player, id)) return;
        Corpse corpse = corpses.get(id);
        Map<Integer, ItemStack> before = CorpseItems.snapshot(player.getInventory().getContents());
        CorpseItems.ClaimPlan plan =
                CorpseItems.planAll(
                        before,
                        corpse.items(),
                        claimAllToInventory,
                        player.getInventory().getMaxStackSize());
        transfer(
                player,
                corpse,
                new Corpse.PendingClaim(
                        player.getUniqueId(),
                        41,
                        before,
                        plan.after(),
                        Collections.emptyMap(),
                        plan.drops(),
                        false));
    }

    private void transfer(Player player, Corpse corpse, Corpse.PendingClaim claim) {
        Corpse prepared = corpse.copy();
        prepared.begin(claim);
        try {
            store.save(prepared);
        } catch (Exception exception) {
            failure("领取准备保存失败，未发放物品", corpse.id, exception);
            player.sendMessage("保存失败，本次未领取，请联系管理员。");
            return;
        }
        corpses.put(corpse.id, prepared);
        try {
            player.getInventory()
                    .setContents(CorpseItems.inventoryArray(claim.after(), claim.inventorySize()));
            player.saveData();
            deliverDropsAndFinish(player, prepared);
            // Refresh the final active view after the corpse menu may have closed.
            player.updateInventory();
        } catch (Exception exception) {
            failure("领取未完成，已锁定遗体等待恢复", corpse.id, exception);
            menu.closeCorpse(corpse.id);
            player.kickPlayer("遗体领取保存未完成，请重新连接以恢复。");
        }
    }

    private void deliverDropsAndFinish(Player player, Corpse prepared) throws Exception {
        List<ItemStack> drops = prepared.pending().drops();
        if (!drops.isEmpty()) {
            Corpse dropping = prepared.copy();
            dropping.startDrops();
            store.save(dropping);
            corpses.put(dropping.id, dropping);
            // A crash during world item spawning requires review rather than replaying the drops.
            for (ItemStack item : drops) {
                player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
            }
            prepared = dropping;
        }
        finish(prepared, true);
    }

    private void finish(Corpse prepared, boolean delivered) throws Exception {
        Corpse completed = prepared.copy();
        completed.finish(delivered);
        store.save(completed);
        if (completed.empty()) {
            corpses.remove(completed.id);
            menu.closeCorpse(completed.id);
            entities.hide(completed.id);
        } else {
            corpses.put(completed.id, completed);
            entities.show(completed);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void join(PlayerJoinEvent event) {
        recover(event.getPlayer());
    }

    private void recover(Player player) {
        for (Corpse corpse : new ArrayList<>(corpses.values())) {
            Corpse.PendingClaim pending = corpse.pending();
            if (pending == null || !pending.player().equals(player.getUniqueId())) continue;
            try {
                if (pending.dropsStarted()) throw new IllegalStateException("掉落物发放结果需要人工核对");
                Map<Integer, ItemStack> current =
                        CorpseItems.snapshot(
                                pending.inventorySize() == 36
                                        ? player.getInventory().getStorageContents()
                                        : player.getInventory().getContents());
                if (current.equals(pending.after())) deliverDropsAndFinish(player, corpse);
                else if (current.equals(pending.before())) finish(corpse, false);
                else throw new IllegalStateException("玩家库存与领取前后记录均不相符，需要人工核对");
            } catch (Exception exception) {
                failure("无法自动恢复领取", corpse.id, exception);
                player.kickPlayer("遗体领取需要管理员核对，记录 ID: " + corpse.id);
                return;
            }
        }
    }

    private void failure(String message, UUID id, Exception exception) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, message + ": " + id, exception);
    }

    public List<Corpse> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(corpses.values()));
    }

    /** Apply an operator's reviewed outcome while the recipient is offline. */
    public void resolveClaim(UUID id, boolean delivered) throws Exception {
        Corpse corpse = corpses.get(id);
        if (corpse == null || corpse.pending() == null)
            throw new IllegalArgumentException("没有待恢复记录");
        if (Bukkit.getPlayer(corpse.pending().player()) != null)
            throw new IllegalArgumentException("请先让领取玩家离线");
        finish(corpse, delivered);
    }
}
