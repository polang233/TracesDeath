package cc.sbsj.mc.tracesDeath.commands;

import cc.sbsj.mc.tracesDeath.corpse.Corpse;
import cc.sbsj.mc.tracesDeath.corpse.CorpseService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Root command routing, sender permissions, arguments and completion. */
public final class TracesDeathCommand implements CommandExecutor, TabCompleter {
    private final CorpseService service;

    public TracesDeathCommand(CorpseService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subcommand = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "list" -> list(sender);
            case "locate" -> {
                if (sender instanceof Player player) locate(player);
                else help(sender);
            }
            case "recover" -> {
                if (sender instanceof ConsoleCommandSender && args.length == 3) recover(sender, args);
                else help(sender);
            }
            default -> help(sender);
        }
        return true;
    }

    private void list(CommandSender sender) {
        boolean admin = sender.hasPermission("tracesdeath.admin");
        long count = 0;
        for (Corpse corpse : service.getAll()) {
            if (admin || sender instanceof Player player && corpse.owner.equals(player.getUniqueId())) {
                sender.sendMessage(corpse.id + " · " + corpse.name + " · " + position(corpse)
                        + (corpse.pending() != null ? " · 领取待恢复" : ""));
                count++;
            }
        }
        sender.sendMessage("共 " + count + " 具遗体。");
    }

    private void locate(Player player) {
        for (Corpse corpse : service.getAll()) {
            if (corpse.owner.equals(player.getUniqueId())) player.sendMessage(position(corpse));
        }
    }

    private void recover(CommandSender sender, String[] args) {
        try {
            UUID id = UUID.fromString(args[1]);
            boolean delivered = switch (args[2]) {
                case "delivered" -> true;
                case "not-delivered" -> false;
                default -> throw new IllegalArgumentException("结果须为 delivered 或 not-delivered");
            };
            service.resolveClaim(id, delivered);
            sender.sendMessage("已保存恢复结果: " + id);
        } catch (Exception exception) {
            sender.sendMessage("恢复失败: " + exception.getMessage());
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage("/td list | /td locate");
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage("/td recover <ID> <delivered|not-delivered>（核对物品后使用）");
        }
    }

    private String position(Corpse corpse) {
        World world = Bukkit.getWorld(corpse.world);
        return (world == null ? corpse.world.toString() : world.getName()) + " "
                + (int) Math.floor(corpse.x) + " " + (int) Math.floor(corpse.y) + " " + (int) Math.floor(corpse.z);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("list", "locate").stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
