package cc.sbsj.mc.tracesdeath.commands;

import cc.sbsj.mc.tracesdeath.language.Messages;

import org.bukkit.command.CommandSender;

public final class ReloadCommand {
    @FunctionalInterface
    public interface Action {
        void reload() throws Exception;
    }

    private final Action action;
    private final java.util.function.Supplier<Messages> messages;

    public ReloadCommand(Action action, java.util.function.Supplier<Messages> messages) {
        this.action = action;
        this.messages = messages;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tracesdeath.admin")) {
            sender.sendMessage(messages.get().text("command.no-permission"));
            return;
        }
        if (args.length != 1) {
            sender.sendMessage(messages.get().text("command.reload-usage"));
            return;
        }
        try {
            action.reload();
            sender.sendMessage(messages.get().text("command.reload-success"));
        } catch (Exception exception) {
            sender.sendMessage(
                    messages.get().text("command.reload-failed", "error", exception.getMessage()));
        }
    }
}
