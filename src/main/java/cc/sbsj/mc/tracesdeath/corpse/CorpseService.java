package cc.sbsj.mc.tracesdeath.corpse;

import cc.sbsj.mc.tracesdeath.compat.ServerAdapter;
import cc.sbsj.mc.tracesdeath.config.CorpseAppearance;
import cc.sbsj.mc.tracesdeath.config.DeathSettings;
import cc.sbsj.mc.tracesdeath.entity.CorpseEntities;
import cc.sbsj.mc.tracesdeath.experience.Experience;
import cc.sbsj.mc.tracesdeath.gui.CorpseMenu;
import cc.sbsj.mc.tracesdeath.language.Messages;
import cc.sbsj.mc.tracesdeath.storage.CorpseStore;

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
    private final Messages messages;
    private final DeathSettings deathSettings;
    private CorpseMenu menu;
    private CorpseEntities entities;

    public CorpseService(
            JavaPlugin plugin,
            CorpseStore store,
            boolean ownerOnly,
            boolean claimAllToInventory,
            CorpseAppearance appearance,
            ServerAdapter serverAdapter,
            Messages messages,
            DeathSettings deathSettings)
            throws Exception {
        this.plugin = plugin;
        this.messages = messages;
        this.deathSettings = deathSettings;
        this.appearance = appearance;
        this.serverAdapter = serverAdapter;
        this.store = store;
        this.ownerOnly = ownerOnly;
        this.claimAllToInventory = claimAllToInventory;
        for (Corpse corpse : store.load()) corpses.put(corpse.id, corpse);
    }

    public void start() {
        menu = new CorpseMenu(plugin, this, appearance, serverAdapter, messages);
        entities =
                new CorpseEntities(
                        plugin,
                        corpses,
                        menu::open,
                        menu::closeCorpse,
                        serverAdapter.createCorpseRenderer(appearance.getType()),
                        messages);
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
        if (!player.hasPermission("tracesdeath.use")
                || (event.getKeepInventory() && deathSettings.skipKeepInventory)) return;
        List<ItemStack> captured = new ArrayList<>();
        if (!event.getKeepInventory()) {
            for (ItemStack item : event.getDrops()) {
                if (!CorpseItems.empty(item) && !deathSettings.excludes(item)) captured.add(item);
            }
            // Paper keeps these separately; do not capture them even if another plugin left them in
            // drops.
            for (ItemStack kept : serverAdapter.keptItems(event))
                captured.removeIf(item -> item.isSimilar(kept));
        }
        Map<Integer, ItemStack> items =
                CorpseItems.capture(player.getInventory().getContents(), captured);
        int experience = storedExperience(event);
        if (items.isEmpty() && experience == 0) return;
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
        corpse.setExperience(experience);
        try {
            store.save(corpse);
        } catch (Exception exception) {
            failure("保存死亡物品失败，保留原生掉落", corpse.id, exception);
            return;
        }
        corpses.put(corpse.id, corpse);
        event.getDrops().removeAll(captured);
        if (experience > 0) event.setDroppedExp(0);
        Bukkit.getScheduler().runTask(plugin, () -> finishDeath(event, corpse));
    }

    private void finishDeath(PlayerDeathEvent event, Corpse corpse) {
        // Observe the final event, including later keep flags and Paper
        // retention.
        boolean cancelled = event instanceof Cancellable && ((Cancellable) event).isCancelled();
        Map<Integer, ItemStack> remaining = corpse.items();
        if (cancelled || event.getKeepInventory()) remaining.clear();
        else {
            List<ItemStack> retained = new ArrayList<>(serverAdapter.keptItems(event));
            retained.addAll(event.getDrops());
            remaining
                    .entrySet()
                    .removeIf(
                            entry ->
                                    retained.stream()
                                            .anyMatch(
                                                    item ->
                                                            !CorpseItems.empty(item)
                                                                    && item.isSimilar(
                                                                            entry.getValue())));
        }
        int finalExperience =
                cancelled
                                || (event.getKeepInventory() && deathSettings.skipKeepInventory)
                                || event.getKeepLevel()
                                || event.getNewExp() != 0
                                || event.getNewLevel() != 0
                                || event.getNewTotalExp() != 0
                                || (corpse.experience() > 0 && event.getDroppedExp() != 0)
                        ? 0
                        : corpse.experience();
        if (!remaining.equals(corpse.items()) || finalExperience != corpse.experience()) {
            Corpse corrected =
                    new Corpse(
                            corpse.id,
                            corpse.owner,
                            corpse.name,
                            corpse.world,
                            corpse.x,
                            corpse.y,
                            corpse.z,
                            corpse.yaw,
                            corpse.heldSlot,
                            corpse.skin,
                            corpse.deathTime,
                            remaining);
            corrected.setExperience(finalExperience);
            try {
                store.save(corrected);
                if (corrected.empty()) corpses.remove(corpse.id);
                else corpses.put(corpse.id, corrected);
            } catch (Exception exception) {
                corpses.remove(corpse.id);
                failure("死亡事件被其他插件修改，修正遗体记录失败，请核对文件后再重启", corpse.id, exception);
            }
        }
        Corpse current = corpses.get(corpse.id);
        if (current != null) entities.show(current);
    }

    private int storedExperience(PlayerDeathEvent event) {
        if (deathSettings.experiencePercent == 0
                || event.getKeepLevel()
                || event.getNewExp() != 0
                || event.getNewLevel() != 0
                || event.getNewTotalExp() != 0) return 0;
        Player player = event.getEntity();
        // Respect plugins that replace vanilla death XP (including suppressing all drops).
        if (event.getDroppedExp() != Math.min(100L, player.getLevel() * 7L)) return 0;
        return (int)
                ((long) Experience.points(player.getLevel(), player.getExp())
                        * deathSettings.experiencePercent
                        / 100);
    }

    public boolean access(Player player, UUID id) {
        Corpse corpse = corpses.get(id);
        if (corpse == null || corpse.empty()) return false;
        if (corpse.pending() != null || hasPending(player.getUniqueId())) {
            player.sendMessage(messages.text("claim.pending"));
            return false;
        }
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return false;
        if (ownerOnly
                && !corpse.owner.equals(player.getUniqueId())
                && !player.hasPermission("tracesdeath.admin")) {
            player.sendMessage(messages.text("claim.owner-only"));
            return false;
        }
        if (!player.getWorld().getUID().equals(corpse.world)
                || player.getLocation()
                                .distanceSquared(
                                        new Location(
                                                player.getWorld(), corpse.x, corpse.y, corpse.z))
                        > 36) {
            player.sendMessage(messages.text("claim.too-far"));
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
            player.sendMessage(messages.text("claim.full"));
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

    public void claimExperience(Player player, UUID id) {
        if (!access(player, id)) return;
        Corpse corpse = corpses.get(id);
        if (corpse.experience() == 0) {
            player.sendMessage(messages.text("claim.no-experience"));
            return;
        }
        Map<Integer, ItemStack> inventory =
                CorpseItems.snapshot(player.getInventory().getContents());
        transfer(
                player,
                corpse,
                new Corpse.PendingClaim(
                        player.getUniqueId(),
                        41,
                        inventory,
                        inventory,
                        corpse.items(),
                        Collections.emptyList(),
                        false),
                true);
    }

    private void transfer(Player player, Corpse corpse, Corpse.PendingClaim claim) {
        transfer(player, corpse, claim, claim.remaining().isEmpty());
    }

    private void transfer(
            Player player, Corpse corpse, Corpse.PendingClaim claim, boolean takeExperience) {
        if (takeExperience && corpse.experience() > 0) {
            Experience.Snapshot before = Experience.Snapshot.capture(player);
            Experience.Snapshot after;
            try {
                after = before.add(corpse.experience());
            } catch (ArithmeticException exception) {
                player.sendMessage(messages.text("claim.experience-limit"));
                return;
            }
            claim =
                    new Corpse.PendingClaim(
                            claim.player(),
                            claim.inventorySize(),
                            claim.before(),
                            claim.after(),
                            claim.remaining(),
                            claim.drops(),
                            false,
                            before,
                            after,
                            corpse.experience());
        }
        Corpse prepared = corpse.copy();
        prepared.begin(claim);
        try {
            store.save(prepared);
        } catch (Exception exception) {
            failure("领取准备保存失败，未发放物品", corpse.id, exception);
            player.sendMessage(messages.text("claim.save-failed"));
            return;
        }
        corpses.put(corpse.id, prepared);
        try {
            player.getInventory()
                    .setContents(CorpseItems.inventoryArray(claim.after(), claim.inventorySize()));
            if (claim.experienceTaken() > 0) claim.experienceAfter().apply(player);
            player.saveData();
            deliverDropsAndFinish(player, prepared);
            // Refresh the final active view after the corpse menu may have closed.
            player.updateInventory();
            if (claim.experienceTaken() > 0)
                player.sendMessage(
                        messages.text(
                                "claim.experience-received",
                                "experience",
                                claim.experienceTaken()));
        } catch (Exception exception) {
            failure("领取未完成，已锁定遗体等待恢复", corpse.id, exception);
            menu.closeCorpse(corpse.id);
            player.kickPlayer(messages.text("claim.reconnect"));
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
                if (current.equals(pending.after())
                        && (pending.experienceTaken() == 0
                                || pending.experienceAfter().matches(player)))
                    deliverDropsAndFinish(player, corpse);
                else if (current.equals(pending.before())
                        && (pending.experienceTaken() == 0
                                || pending.experienceBefore().matches(player)))
                    finish(corpse, false);
                else throw new IllegalStateException("玩家库存与领取前后记录均不相符，需要人工核对");
            } catch (Exception exception) {
                failure("无法自动恢复领取", corpse.id, exception);
                player.kickPlayer(messages.text("claim.review", "id", corpse.id));
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
            throw new IllegalArgumentException(messages.text("command.no-pending"));
        if (Bukkit.getPlayer(corpse.pending().player()) != null)
            throw new IllegalArgumentException(messages.text("command.player-must-be-offline"));
        finish(corpse, delivered);
    }
}
