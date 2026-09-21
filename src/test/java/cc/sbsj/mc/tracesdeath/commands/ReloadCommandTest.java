package cc.sbsj.mc.tracesdeath.commands;

import static org.mockito.Mockito.*;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class ReloadCommandTest {
    @Test
    void requiresPermissionAndReportsFailureWithoutSuccessMessage() throws Exception {
        var action = mock(ReloadCommand.Action.class);
        var sender = mock(CommandSender.class);
        var command =
                new ReloadCommand(
                        action, () -> cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN"));
        command.execute(sender, new String[] {"reload"});
        verifyNoInteractions(action);
        when(sender.hasPermission("tracesdeath.admin")).thenReturn(true);
        doThrow(new IllegalArgumentException("invalid config")).when(action).reload();
        command.execute(sender, new String[] {"reload"});
        verify(action).reload();
        verify(sender).sendMessage("重载失败: invalid config");
        verify(sender, never()).sendMessage(contains("已重载"));
    }
}
