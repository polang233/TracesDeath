package cc.sbsj.mc.tracesDeath.commands;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.storage.PlacementResult;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageProvider;
import cc.sbsj.mc.tracesDeath.trace.TraceManager;
import cc.sbsj.mc.tracesDeath.trace.TraceManager.TraceInfo;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TracesDeath 命令处理器。
 */
public final class TracesDeathCommand implements CommandExecutor, TabCompleter {
    private final TracesDeath plugin;
    private final TraceManager traceManager;

    public TracesDeathCommand(TracesDeath plugin, TraceManager traceManager) {
        this.plugin = plugin;
        this.traceManager = traceManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (args.length == 0) {
            if (canUse(sender)) {
                sendHelp(sender, label);
            } else {
                noPermission(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> admin(sender, () -> handleReload(sender));
            case "types" -> admin(sender, () -> handleTypes(sender));
            case "list" -> handleList(sender, args);
            case "locate" -> handleLocate(sender, args);
            case "info" -> admin(sender, () -> handleInfo(sender, args));
            case "remove" -> admin(sender, () -> handleRemove(sender, args));
            case "clear" -> admin(sender, () -> handleClear(sender, args));
            case "debug" -> admin(sender, () -> handleDebug(sender, label, args));
            default -> {
                if (canUse(sender)) {
                    sendHelp(sender, label);
                } else {
                    noPermission(sender);
                }
            }
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadTraceConfig();
        plugin.lang().send(sender, "command.reload");
    }

    private void handleTypes(CommandSender sender) {
        String types = plugin.storageRegistry().providers().stream()
                .map(TraceStorageProvider::id)
                .collect(Collectors.joining(", "));
        plugin.lang().send(sender, "command.types", Map.of("types", types));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (!canUse(sender)) {
            noPermission(sender);
            return;
        }
        Collection<TraceInfo> traces;
        if (args.length >= 2) {
            if (!isAdmin(sender)) {
                noPermission(sender);
                return;
            }
            String playerName = args[1];
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            traces = traceManager.getActiveTraces().stream()
                    .filter(info -> info.playerId().equals(target.getUniqueId())
                            || info.playerName().equalsIgnoreCase(playerName))
                    .toList();
        } else if (isAdmin(sender)) {
            traces = traceManager.getActiveTraces();
        } else if (sender instanceof Player player) {
            traces = traceManager.getPlayerTraces(player.getUniqueId());
        } else {
            noPermission(sender);
            return;
        }

        if (traces.isEmpty()) {
            plugin.lang().send(sender, "command.no-traces");
            return;
        }
        plugin.lang().send(sender, "command.list-header",
                Map.of("count", Integer.toString(traces.size())));
        for (TraceInfo info : traces) {
            plugin.lang().send(sender, "command.list-entry", Map.of(
                    "player", info.playerName(),
                    "id", shortId(info.traceId()),
                    "type", info.storageType(),
                    "age", formatDuration(info.ageSeconds()),
                    "location", formatLocation(info.location())
            ));
        }
    }

    private void handleLocate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !canUse(sender)) {
            if (!(sender instanceof Player)) {
                plugin.lang().send(sender, "command.player-only");
            } else {
                noPermission(sender);
            }
            return;
        }

        List<TraceInfo> ownTraces = traceManager.getPlayerTraces(player.getUniqueId()).stream().toList();
        if (ownTraces.isEmpty()) {
            plugin.lang().send(sender, "command.no-traces");
            return;
        }
        if (args.length < 2) {
            TraceInfo latest = ownTraces.stream()
                    .max((left, right) -> Long.compare(left.creationTime(), right.creationTime()))
                    .orElseThrow();
            sendLocation(sender, latest);
            return;
        }

        UUID traceId = resolveTraceId(args[1], sender);
        if (traceId == null) {
            return;
        }
        TraceInfo info = traceManager.getTrace(traceId);
        if (info == null || !info.playerId().equals(player.getUniqueId())) {
            plugin.lang().send(sender, "command.not-your-trace");
            return;
        }
        sendLocation(sender, info);
    }

    private void sendLocation(CommandSender sender, TraceInfo info) {
        plugin.lang().send(sender, "command.location", Map.of(
                "id", info.traceId().toString(),
                "type", info.storageType(),
                "location", formatLocation(info.location()),
                "remaining", remainingTime(info)
        ));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.lang().send(sender, "command.usage-info", Map.of("label", "td"));
            return;
        }
        UUID traceId = resolveTraceId(args[1], sender);
        if (traceId == null) {
            return;
        }
        TraceInfo info = traceManager.getTrace(traceId);
        if (info == null) {
            plugin.lang().send(sender, "command.trace-not-found");
            return;
        }

        plugin.lang().send(sender, "command.info-header");
        plugin.lang().send(sender, "command.info-id", Map.of("id", info.traceId().toString()));
        plugin.lang().send(sender, "command.info-player", Map.of("player", info.playerName()));
        plugin.lang().send(sender, "command.info-type", Map.of("type", info.storageType()));
        plugin.lang().send(sender, "command.info-location",
                Map.of("location", formatLocation(info.location())));
        plugin.lang().send(sender, "command.info-created", Map.of(
                "time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new Date(info.creationTime()))
        ));
        plugin.lang().send(sender, "command.info-age",
                Map.of("age", formatDuration(info.ageSeconds())));
        plugin.lang().send(sender, "command.info-remaining",
                Map.of("remaining", remainingTime(info)));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.lang().send(sender, "command.usage-remove", Map.of("label", "td"));
            return;
        }
        UUID traceId = resolveTraceId(args[1], sender);
        if (traceId == null) {
            return;
        }
        boolean dropItems = parseDropMode(sender, args.length >= 3 ? args[2] : "delete");
        if (args.length >= 3 && !isDropMode(args[2])) {
            return;
        }
        if (traceManager.removeTrace(traceId, dropItems)) {
            plugin.lang().send(sender, "command.remove-success",
                    Map.of("id", traceId.toString()));
        } else {
            plugin.lang().send(sender, "command.trace-not-found");
        }
    }

    private void handleClear(CommandSender sender, String[] args) {
        boolean dropItems = parseDropMode(sender, args.length >= 2 ? args[1] : "delete");
        if (args.length >= 2 && !isDropMode(args[1])) {
            return;
        }
        List<TraceInfo> traces = new ArrayList<>(traceManager.getActiveTraces());
        if (traces.isEmpty()) {
            plugin.lang().send(sender, "command.no-traces");
            return;
        }

        int removed = 0;
        for (TraceInfo info : traces) {
            if (traceManager.removeTrace(info.traceId(), dropItems)) {
                removed++;
            }
        }
        plugin.lang().send(sender, "command.clear-success",
                Map.of("removed", Integer.toString(removed),
                        "total", Integer.toString(traces.size())));
    }

    private void handleDebug(CommandSender sender, String label, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("synth")) {
            handleDebugSynth(sender, args);
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("create")) {
            plugin.lang().send(sender, "command.usage-debug-create", Map.of("label", label));
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "command.player-only");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            item = new ItemStack(Material.STONE, 1);
        } else {
            item = item.clone();
            item.setAmount(Math.min(item.getAmount(), item.getMaxStackSize()));
        }

        PlacementResult result = traceManager.createTrace(player, player.getLocation(), List.of(item));
        sender.sendMessage(plugin.lang().component(plugin.lang().text("prefix") + result.message()));
    }

    /**
     * 控制台合成墓碑：debug synth <玩家名> [x y z]，不依赖在线玩家即可回归创建链路。
     */
    private void handleDebugSynth(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.lang().component(
                    plugin.lang().text("prefix") + "&e用法: /td debug synth <玩家名> [x y z]"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        World world = Bukkit.getWorlds().getFirst();
        double x = 0.5;
        double y = world.getHighestBlockYAt(0, 0) + 1.0;
        double z = 0.5;
        if (args.length >= 6) {
            try {
                x = Double.parseDouble(args[3]);
                y = Double.parseDouble(args[4]);
                z = Double.parseDouble(args[5]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage(plugin.lang().component(
                        plugin.lang().text("prefix") + "&c坐标格式无效。"));
                return;
            }
        }
        Location location = new Location(world, x, y, z);
        List<ItemStack> drops = List.of(new ItemStack(Material.DIAMOND, 3), new ItemStack(Material.IRON_SWORD, 1));
        PlacementResult result = traceManager.createTrace(target, location, drops);
        sender.sendMessage(plugin.lang().component(
                plugin.lang().text("prefix") + (result.success()
                        ? "&a合成墓碑创建成功: " + result.message()
                        : "&c合成墓碑创建失败: " + result.message())));
    }

    private void sendHelp(CommandSender sender, String label) {
        plugin.lang().send(sender, "command.help-header");
        plugin.lang().send(sender, "command.usage-list", Map.of("label", label));
        plugin.lang().send(sender, "command.usage-locate", Map.of("label", label));
        if (isAdmin(sender)) {
            plugin.lang().send(sender, "command.usage-reload", Map.of("label", label));
            plugin.lang().send(sender, "command.usage-types", Map.of("label", label));
            plugin.lang().send(sender, "command.usage-info", Map.of("label", label));
            plugin.lang().send(sender, "command.usage-remove", Map.of("label", label));
            plugin.lang().send(sender, "command.usage-clear", Map.of("label", label));
            plugin.lang().send(sender, "command.usage-debug-create", Map.of("label", label));
        }
    }

    private UUID resolveTraceId(String value, CommandSender sender) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
        }

        List<UUID> matches = traceManager.getActiveTraces().stream()
                .map(TraceInfo::traceId)
                .filter(id -> id.toString().startsWith(value.toLowerCase()))
                .toList();
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (matches.size() > 1) {
            plugin.lang().send(sender, "command.ambiguous-id",
                    Map.of("value", value, "count", Integer.toString(matches.size())));
        } else {
            plugin.lang().send(sender, "command.invalid-id", Map.of("value", value));
        }
        return null;
    }

    private String remainingTime(TraceInfo info) {
        long expiration = plugin.traceConfig().traceExpirationMillis();
        if (expiration <= 0) {
            return plugin.lang().text("command.never");
        }
        long remaining = Math.max(0, (expiration - info.ageMillis()) / 1000);
        return remaining == 0 ? plugin.lang().text("command.expired") : formatDuration(remaining);
    }

    private boolean parseDropMode(CommandSender sender, String value) {
        if ("drop".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("delete".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return false;
        }
        plugin.lang().send(sender, "command.invalid-value", Map.of("value", value));
        return false;
    }

    private boolean isDropMode(String value) {
        return "drop".equalsIgnoreCase(value)
                || "delete".equalsIgnoreCase(value)
                || "true".equalsIgnoreCase(value)
                || "false".equalsIgnoreCase(value);
    }

    private void admin(CommandSender sender, Runnable action) {
        if (isAdmin(sender)) {
            action.run();
        } else {
            noPermission(sender);
        }
    }

    private boolean canUse(CommandSender sender) {
        return isAdmin(sender) || sender.hasPermission("tracesdeath.use");
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("tracesdeath.admin");
    }

    private void noPermission(CommandSender sender) {
        plugin.lang().send(sender, "command.no-permission");
    }

    private String shortId(UUID traceId) {
        return traceId.toString().substring(0, 8);
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分" + (seconds % 60) + "秒";
        } else {
            return (seconds / 3600) + "时" + (seconds % 3600) / 60 + "分";
        }
    }

    private String formatLocation(org.bukkit.Location location) {
        return String.format("%s[%.1f, %.1f, %.1f]",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String label,
                                                 String @NotNull [] args) {
        if (!canUse(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(List.of("list", "locate"));
            if (isAdmin(sender)) {
                commands.addAll(List.of("reload", "types", "info", "remove", "clear", "debug"));
            }
            return filter(commands, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "debug" -> filter(List.of("create"), args[1]);
                case "list" -> isAdmin(sender)
                        ? filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1])
                        : List.of();
                case "info", "remove", "locate" -> filter(
                        traceManager.getActiveTraces().stream().map(info -> shortId(info.traceId())).toList(),
                        args[1]);
                case "clear" -> filter(List.of("drop", "delete"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("remove")) {
            return filter(List.of("drop", "delete"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase().startsWith(lowerPrefix))
                .toList();
    }
}
