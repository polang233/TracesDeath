package cc.sbsj.mc.tracesdeath.commands;

import cc.sbsj.mc.tracesdeath.resourcepack.ResourcePackTestService;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ResourcePackTestCommand {
    private final ResourcePackTestService packs;

    public ResourcePackTestCommand(ResourcePackTestService packs) {
        this.packs = packs;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tracesdeath.admin")) {
            sender.sendMessage("你没有材质包测试权限。");
            return;
        }
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("/td testpack [玩家]");
            return;
        }
        try {
            Player player =
                    args.length == 2
                            ? Bukkit.getPlayerExact(args[1])
                            : sender instanceof Player ? (Player) sender : null;
            if (player == null) {
                sender.sendMessage("请指定在线玩家。");
                return;
            }
            packs.send(player);
            sender.sendMessage("已向 " + player.getName() + " 发送内置材质包测试请求。");
        } catch (Exception exception) {
            sender.sendMessage("材质包测试失败: " + exception.getMessage());
        }
    }
}
