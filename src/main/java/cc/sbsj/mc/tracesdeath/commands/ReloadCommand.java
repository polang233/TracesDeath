package cc.sbsj.mc.tracesdeath.commands;

import org.bukkit.command.CommandSender;

public final class ReloadCommand {
    @FunctionalInterface
    public interface Action {
        void reload() throws Exception;
    }

    private final Action action;

    public ReloadCommand(Action action) {
        this.action = action;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tracesdeath.admin")) {
            sender.sendMessage("你没有重载权限。");
            return;
        }
        if (args.length != 1) {
            sender.sendMessage("/td reload");
            return;
        }
        try {
            action.reload();
            sender.sendMessage("TracesDeath 配置已重载，遗体界面已关闭，外观已刷新。");
        } catch (Exception exception) {
            sender.sendMessage("重载失败: " + exception.getMessage());
        }
    }
}
