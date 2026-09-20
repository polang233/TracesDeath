package cc.sbsj.mc.tracesdeath.commands;

import cc.sbsj.mc.tracesdeath.corpse.Corpse;
import cc.sbsj.mc.tracesdeath.corpse.CorpseService;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/** Root command routing, sender permissions, arguments and completion. */
public final class TracesDeathCommand implements CommandExecutor, TabCompleter {
    private final CorpseService service;
    private final ResourcePackTestCommand resourcePackTest;

    public TracesDeathCommand(CorpseService service, ResourcePackTestCommand resourcePackTest) {
        this.resourcePackTest = resourcePackTest;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subcommand = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "list":
                list(sender);
                break;
            case "locate":
                if (sender instanceof Player) locate((Player) sender);
                else help(sender);
                break;
            case "testpack":
                resourcePackTest.execute(sender, args);
                break;
            case "recover":
                if (sender instanceof ConsoleCommandSender && args.length == 3)
                    recover(sender, args);
                else help(sender);
                break;
            default:
                help(sender);
        }
        return true;
    }

    private void list(CommandSender sender) {
        boolean admin = sender.hasPermission("tracesdeath.admin");
        long count = 0;
        for (Corpse corpse : service.getAll()) {
            if (admin
                    || sender instanceof Player
                            && corpse.owner.equals(((Player) sender).getUniqueId())) {
                sender.sendMessage(
                        corpse.id
                                + " · "
                                + corpse.name
                                + " · "
                                + position(corpse)
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
            boolean delivered;
            if ("delivered".equals(args[2])) delivered = true;
            else if ("not-delivered".equals(args[2])) delivered = false;
            else throw new IllegalArgumentException("结果须为 delivered 或 not-delivered");
            service.resolveClaim(id, delivered);
            sender.sendMessage("已保存恢复结果: " + id);
        } catch (Exception exception) {
            sender.sendMessage("恢复失败: " + exception.getMessage());
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage("/td list | /td locate");
        if (sender.hasPermission("tracesdeath.admin")) sender.sendMessage("/td testpack [玩家]");
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage("/td recover <ID> <delivered|not-delivered>（核对物品后使用）");
        }
    }

    private String position(Corpse corpse) {
        World world = Bukkit.getWorld(corpse.world);
        return (world == null ? corpse.world.toString() : world.getName())
                + " "
                + (int) Math.floor(corpse.x)
                + " "
                + (int) Math.floor(corpse.y)
                + " "
                + (int) Math.floor(corpse.z);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return (sender.hasPermission("tracesdeath.admin")
                            ? Arrays.asList("list", "locate", "testpack")
                            : Arrays.asList("list", "locate"))
                    .stream()
                            .filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT)))
                            .collect(Collectors.toList());
        }
        if (args.length == 2
                && "testpack".equalsIgnoreCase(args[0])
                && sender.hasPermission("tracesdeath.admin"))
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(
                            name ->
                                    name.toLowerCase(Locale.ROOT)
                                            .startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        return Collections.emptyList();
    }
}
