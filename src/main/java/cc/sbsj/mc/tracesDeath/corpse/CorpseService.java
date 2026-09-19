package cc.sbsj.mc.tracesDeath.corpse;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** All inventory/state transitions are serialized on the server thread. */
public final class CorpseService implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final CorpseStore store;
    private final Map<UUID, Corpse> corpses = new LinkedHashMap<>();
    private final boolean ownerOnly;
    private CorpseMenu menu;
    private CorpseEntities entities;

    public CorpseService(JavaPlugin plugin, CorpseStore store, boolean ownerOnly) throws Exception {
        this.plugin = plugin;
        this.store = store;
        this.ownerOnly = ownerOnly;
        for (Corpse corpse : store.load()) corpses.put(corpse.id, corpse);
    }

    public void start() {
        menu = new CorpseMenu(plugin, this);
        entities = new CorpseEntities(plugin, corpses, menu::open, menu::closeCorpse);
        var events = Bukkit.getPluginManager();
        events.registerEvents(this, plugin);
        events.registerEvents(menu, plugin);
        events.registerEvents(entities, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) recover(player);
        entities.reconcileAll();
        // A single repair task also covers entities removed by external cleanup plugins.
        Bukkit.getScheduler().runTaskTimer(plugin, entities::reconcileAll, 100, 100);
    }

    public void stop() {
        if (menu != null) menu.shutdown();
        if (entities != null) entities.shutdown();
        // Every accepted mutation was already persisted; never overwrite disk from a shutdown cache.
    }

    public Corpse get(UUID id) { return corpses.get(id); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void death(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (event.getKeepInventory() || !player.hasPermission("tracesdeath.use")) return;
        var items = CorpseItems.capture(player.getInventory().getContents(), event.getDrops());
        if (items.isEmpty()) return;
        Location location = player.getLocation();
        // Settle the model on the collision surface below the death point without changing blocks.
        double y = Math.max(location.getWorld().getMinHeight() + 1, Math.min(location.getWorld().getMaxHeight() - 2, location.getY()));
        Location rayStart = new Location(player.getWorld(), location.getX(), y + .1, location.getZ());
        var ground = player.getWorld().rayTraceBlocks(rayStart, new org.bukkit.util.Vector(0, -1, 0),
                y - player.getWorld().getMinHeight() + 1, FluidCollisionMode.NEVER, true);
        if (ground != null) y = ground.getHitPosition().getY();
        Corpse corpse = new Corpse(UUID.randomUUID(), player.getUniqueId(), player.getName(),
                player.getWorld().getUID(), location.getX(), y, location.getZ(),
                Math.round(location.getYaw() / 90f) * 90f, player.getInventory().getHeldItemSlot(),
                player.getPlayerProfile(), items);
        try {
            store.save(corpse);
        } catch (Exception exception) {
            failure("保存死亡物品失败，保留原生掉落", corpse.id, exception);
            return;
        }
        corpses.put(corpse.id, corpse);
        event.getDrops().clear();
        Bukkit.getScheduler().runTask(plugin, () -> {
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
        if (ownerOnly && !corpse.owner.equals(player.getUniqueId()) && !player.hasPermission("tracesdeath.admin")) {
            player.sendMessage("你只能领取自己的遗体。");
            return false;
        }
        if (!player.getWorld().getUID().equals(corpse.world)
                || player.getLocation().distanceSquared(new Location(player.getWorld(), corpse.x, corpse.y, corpse.z)) > 36) {
            player.sendMessage("请靠近遗体后再领取。");
            return false;
        }
        return true;
    }

    private boolean hasPending(UUID player) {
        return corpses.values().stream().anyMatch(c -> c.pending() != null && c.pending().player().equals(player));
    }

    public void claim(Player player, UUID id, int slot) {
        if (!access(player, id)) return;
        Corpse corpse = corpses.get(id);
        var items = corpse.items();
        var source = items.get(slot);
        if (source == null) return;
        var before = CorpseItems.snapshot(player.getInventory().getStorageContents());
        var transfer = CorpseItems.plan(before, source, player.getInventory().getMaxStackSize());
        if (transfer.remaining() == source.getAmount()) {
            player.sendMessage("背包已满，物品仍保留在遗体中。");
            return;
        }
        if (transfer.remaining() == 0) items.remove(slot);
        else {
            source.setAmount(transfer.remaining());
            items.put(slot, source);
        }
        Corpse prepared = corpse.copy();
        prepared.begin(new Corpse.PendingClaim(player.getUniqueId(), before, transfer.after(), items));
        try {
            store.save(prepared); // Durable intent before touching the player's inventory.
        } catch (Exception exception) {
            failure("领取准备保存失败，未发放物品", id, exception);
            player.sendMessage("保存失败，本次未领取，请联系管理员。");
            return;
        }
        corpses.put(id, prepared);
        try {
            player.getInventory().setStorageContents(CorpseItems.storageArray(transfer.after()));
            player.saveData();
            finish(prepared, true);
        } catch (Exception exception) {
            failure("领取未完成，已锁定遗体等待恢复", id, exception);
            menu.closeCorpse(id);
            player.kick(Component.text("遗体领取保存未完成，请重新连接以恢复。"));
        }
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
    public void join(PlayerJoinEvent event) { recover(event.getPlayer()); }

    private void recover(Player player) {
        for (Corpse corpse : List.copyOf(corpses.values())) {
            Corpse.PendingClaim pending = corpse.pending();
            if (pending == null || !pending.player().equals(player.getUniqueId())) continue;
            try {
                var current = CorpseItems.snapshot(player.getInventory().getStorageContents());
                if (current.equals(pending.after())) finish(corpse, true);
                else if (current.equals(pending.before())) finish(corpse, false);
                else throw new IllegalStateException("玩家库存与领取前后记录均不相符，需要人工核对");
            } catch (Exception exception) {
                failure("无法自动恢复领取", corpse.id, exception);
                player.kick(Component.text("遗体领取需要管理员核对，记录 ID: " + corpse.id));
                return;
            }
        }
    }

    private void failure(String message, UUID id, Exception exception) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, message + ": " + id, exception);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            boolean admin = sender.hasPermission("tracesdeath.admin");
            long count = 0;
            for (Corpse corpse : corpses.values()) {
                if (admin || sender instanceof Player player && corpse.owner.equals(player.getUniqueId())) {
                    sender.sendMessage(corpse.id + " · " + corpse.name + " · " + position(corpse)
                            + (corpse.pending() != null ? " · 领取待恢复" : ""));
                    count++;
                }
            }
            sender.sendMessage("共 " + count + " 具遗体。");
            return true;
        }
        if (args[0].equalsIgnoreCase("locate") && sender instanceof Player player) {
            for (Corpse corpse : corpses.values()) {
                if (corpse.owner.equals(player.getUniqueId())) player.sendMessage(position(corpse));
            }
            return true;
        }
        // Recovery is deliberately console-only and requires an explicit outcome.
        if (args[0].equalsIgnoreCase("recover") && sender instanceof ConsoleCommandSender && args.length == 3) {
            try {
                Corpse corpse = corpses.get(UUID.fromString(args[1]));
                if (corpse == null || corpse.pending() == null) throw new IllegalArgumentException("没有待恢复记录");
                if (Bukkit.getPlayer(corpse.pending().player()) != null) throw new IllegalArgumentException("请先让领取玩家离线");
                boolean delivered;
                if (args[2].equals("delivered")) delivered = true;
                else if (args[2].equals("not-delivered")) delivered = false;
                else throw new IllegalArgumentException("结果须为 delivered 或 not-delivered");
                finish(corpse, delivered);
                sender.sendMessage("已保存恢复结果: " + corpse.id);
            } catch (Exception exception) {
                sender.sendMessage("恢复失败: " + exception.getMessage());
            }
            return true;
        }
        sender.sendMessage("/td list | /td locate");
        if (sender instanceof ConsoleCommandSender) sender.sendMessage("/td recover <ID> <delivered|not-delivered>（核对物品后使用）");
        return true;
    }

    private String position(Corpse corpse) {
        World world = Bukkit.getWorld(corpse.world);
        return (world == null ? corpse.world.toString() : world.getName()) + " "
                + (int) Math.floor(corpse.x) + " " + (int) Math.floor(corpse.y) + " " + (int) Math.floor(corpse.z);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "locate").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
