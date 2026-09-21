package cc.sbsj.mc.tracesdeath.commands;

import cc.sbsj.mc.tracesdeath.language.Messages;
import cc.sbsj.mc.tracesdeath.resourcepack.ResourcePackTestService;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ResourcePackTestCommand {
    private final ResourcePackTestService packs;
    private final Messages messages;

    public ResourcePackTestCommand(ResourcePackTestService packs, Messages messages) {
        this.packs = packs;
        this.messages = messages;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tracesdeath.admin")) {
            sender.sendMessage(messages.text("command.no-permission"));
            return;
        }
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(messages.text("command.testpack-usage"));
            return;
        }
        try {
            Player player =
                    args.length == 2
                            ? Bukkit.getPlayerExact(args[1])
                            : sender instanceof Player ? (Player) sender : null;
            if (player == null) {
                sender.sendMessage(messages.text("command.online-player"));
                return;
            }
            packs.send(player);
            sender.sendMessage(messages.text("command.testpack-success", "name", player.getName()));
        } catch (Exception exception) {
            sender.sendMessage(
                    messages.text("command.testpack-failed", "error", exception.getMessage()));
        }
    }
}
