package cc.sbsj.mc.tracesdeath.commands;

import static org.mockito.Mockito.*;

import cc.sbsj.mc.tracesdeath.resourcepack.ResourcePackTestService;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ResourcePackTestCommandTest {
    @Test
    void onlyAuthorizedExplicitRequestsReachTheSender() throws Exception {
        var service = mock(ResourcePackTestService.class);
        var command = new ResourcePackTestCommand(service);
        var player = mock(Player.class);
        command.execute(player, new String[] {"testpack"});
        verifyNoInteractions(service);
        when(player.hasPermission("tracesdeath.admin")).thenReturn(true);
        command.execute(player, new String[] {"testpack"});
        verify(service).send(player);
    }
}
