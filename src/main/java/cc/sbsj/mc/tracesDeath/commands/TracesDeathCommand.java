package cc.sbsj.mc.tracesDeath.commands;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.storage.PlacementResult;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageProvider;
import cc.sbsj.mc.tracesDeath.trace.TraceManager;
import cc.sbsj.mc.tracesDeath.trace.TraceManager.TraceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TracesDeath 命令处理器
 * <p>
 * 提供插件管理、调试和墓碑操作命令。
 */
public final class TracesDeathCommand implements CommandExecutor, TabCompleter {
    private final TracesDeath plugin;
    private final TraceManager traceManager;

    public TracesDeathCommand(TracesDeath plugin, TraceManager traceManager) {
        this.plugin = plugin;
        this.traceManager = traceManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("tracesdeath.admin")) {
            plugin.lang().send(sender, "command.no-permission");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "types" -> handleTypes(sender);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "clear" -> handleClear(sender);
            case "debug" -> handleDebug(sender, label, args);
            default -> sendHelp(sender, label);
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
        Collection<TraceInfo> traces;
        
        // 如果指定了玩家名，只显示该玩家的墓碑
        if (args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("找不到玩家: " + args[1]).color(NamedTextColor.RED));
                return;
            }
            traces = traceManager.getPlayerTraces(target.getUniqueId());
        } else {
            traces = traceManager.getActiveTraces();
        }
        
        if (traces.isEmpty()) {
            sender.sendMessage(Component.text("当前没有活动的墓碑").color(NamedTextColor.YELLOW));
            return;
        }
        
        sender.sendMessage(Component.text("=== 活动墓碑列表 (" + traces.size() + ") ===").color(NamedTextColor.GOLD));
        for (TraceInfo info : traces) {
            long ageSeconds = info.ageSeconds();
            String ageStr = formatDuration(ageSeconds);
            sender.sendMessage(Component.text(
                    String.format("- %s | ID: %s | 类型: %s | 存在时间: %s",
                            info.playerName(),
                            info.traceId().toString().substring(0, 8),
                            info.storageType(),
                            ageStr)
            ).color(NamedTextColor.GRAY));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /td info <trace-id>").color(NamedTextColor.YELLOW));
            return;
        }
        
        try {
            UUID traceId = UUID.fromString(args[1]);
            TraceInfo info = traceManager.getTrace(traceId);
            if (info == null) {
                sender.sendMessage(Component.text("找不到该墓碑").color(NamedTextColor.RED));
                return;
            }
            
            sender.sendMessage(Component.text("=== 墓碑详情 ===").color(NamedTextColor.GOLD));
            sender.sendMessage(Component.text("ID: " + info.traceId()).color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("玩家: " + info.playerName()).color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("类型: " + info.storageType()).color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("位置: " + formatLocation(info.location())).color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("创建时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date(info.creationTime()))).color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("存在时间: " + formatDuration(info.ageSeconds())).color(NamedTextColor.GRAY));
            
            long expirationMillis = plugin.traceConfig().traceExpirationMillis();
            if (expirationMillis > 0) {
                long remaining = (expirationMillis - info.ageMillis()) / 1000;
                if (remaining > 0) {
                    sender.sendMessage(Component.text("剩余时间: " + formatDuration(remaining)).color(NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text("已过期，等待清理").color(NamedTextColor.RED));
                }
            } else {
                sender.sendMessage(Component.text("永不过期").color(NamedTextColor.GREEN));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("无效的墓碑ID格式").color(NamedTextColor.RED));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /td remove <trace-id> [drop-items]").color(NamedTextColor.YELLOW));
            return;
        }
        
        try {
            UUID traceId = UUID.fromString(args[1]);
            boolean dropItems = args.length >= 3 && Boolean.parseBoolean(args[2]);
            
            if (traceManager.removeTrace(traceId, dropItems)) {
                sender.sendMessage(Component.text("已移除墓碑: " + traceId).color(NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("找不到该墓碑").color(NamedTextColor.RED));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("无效的墓碑ID格式").color(NamedTextColor.RED));
        }
    }

    private void handleClear(CommandSender sender) {
        int count = traceManager.getActiveCount();
        if (count == 0) {
            sender.sendMessage(Component.text("当前没有活动的墓碑").color(NamedTextColor.YELLOW));
            return;
        }
        
        for (TraceInfo info : traceManager.getActiveTraces()) {
            traceManager.removeTrace(info.traceId(), false);
        }
        sender.sendMessage(Component.text("已清除所有 " + count + " 个墓碑").color(NamedTextColor.GREEN));
    }

    private void handleDebug(CommandSender sender, String label, String[] args) {
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

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("=== TracesDeath 命令帮助 ===").color(NamedTextColor.GOLD));
        plugin.lang().send(sender, "command.usage-reload", Map.of("label", label));
        plugin.lang().send(sender, "command.usage-types", Map.of("label", label));
        plugin.lang().send(sender, "command.usage-list", Map.of("label", label));
        plugin.lang().send(sender, "command.usage-info", Map.of("label", label));
        plugin.lang().send(sender, "command.usage-remove", Map.of("label", label));
        plugin.lang().send(sender, "command.usage-clear", Map.of("label", label));
        plugin.lang().send(sender, "command.usage-debug-create", Map.of("label", label));
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分" + (seconds % 60) + "秒";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "时" + minutes + "分";
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
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("tracesdeath.admin")) {
            return List.of();
        }
        
        if (args.length == 1) {
            return filter(List.of("reload", "types", "list", "info", "remove", "clear", "debug"), args[0]);
        }
        
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "debug" -> { return filter(List.of("create"), args[1]); }
                case "list" -> {
                    // 返回在线玩家名
                    return filter(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .toList(), args[1]);
                }
                case "info", "remove" -> {
                    // 返回活动墓碑ID前缀
                    return filter(traceManager.getActiveTraces().stream()
                            .map(info -> info.traceId().toString().substring(0, 8))
                            .toList(), args[1]);
                }
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("remove")) {
            return filter(List.of("true", "false"), args[2]);
        }
        
        return List.of();
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase().startsWith(lowerPrefix)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
