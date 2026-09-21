package cc.sbsj.mc.tracesdeath.commands;

import cc.sbsj.mc.tracesdeath.corpse.Corpse;
import cc.sbsj.mc.tracesdeath.corpse.CorpseService;
import cc.sbsj.mc.tracesdeath.language.Messages;

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
    private final ReloadCommand reload;
    private final Messages messages;

    public TracesDeathCommand(
            CorpseService service,
            ResourcePackTestCommand resourcePackTest,
            ReloadCommand reload,
            Messages messages) {
        this.resourcePackTest = resourcePackTest;
        this.reload = reload;
        this.service = service;
        this.messages = messages;
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
            case "reload":
                reload.execute(sender, args);
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
                        messages.text(
                                "command.list-entry",
                                "id",
                                corpse.id,
                                "name",
                                corpse.name,
                                "position",
                                position(corpse),
                                "pending",
                                corpse.pending() != null ? messages.text("command.pending") : ""));
                count++;
            }
        }
        sender.sendMessage(messages.text("command.list-total", "count", count));
    }

    private void locate(Player player) {
        for (Corpse corpse : service.getAll()) {
            if (corpse.owner.equals(player.getUniqueId()))
                player.sendMessage(
                        messages.text("command.locate-entry", "position", position(corpse)));
        }
    }

    private void recover(CommandSender sender, String[] args) {
        try {
            UUID id = UUID.fromString(args[1]);
            boolean delivered;
            if ("delivered".equals(args[2])) delivered = true;
            else if ("not-delivered".equals(args[2])) delivered = false;
            else throw new IllegalArgumentException(messages.text("command.recover-outcome"));
            service.resolveClaim(id, delivered);
            sender.sendMessage(messages.text("command.recover-success", "id", id));
        } catch (Exception exception) {
            sender.sendMessage(
                    messages.text("command.recover-failed", "error", exception.getMessage()));
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(messages.text("command.help"));
        if (sender.hasPermission("tracesdeath.admin"))
            sender.sendMessage(messages.text("command.admin-help"));
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(messages.text("command.recover-help"));
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
                            ? Arrays.asList("list", "locate", "testpack", "reload")
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
