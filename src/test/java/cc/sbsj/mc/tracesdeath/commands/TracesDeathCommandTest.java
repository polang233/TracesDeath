package cc.sbsj.mc.tracesdeath.commands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cc.sbsj.mc.tracesdeath.corpse.Corpse;
import cc.sbsj.mc.tracesdeath.corpse.CorpseService;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class TracesDeathCommandTest {
    @Test
    void recoveryIsRestrictedToConsoleEvenForAnAdminPlayer() {
        CorpseService service = mock(CorpseService.class);
        Player player = mock(Player.class);
        when(player.hasPermission("tracesdeath.admin")).thenReturn(true);
        new TracesDeathCommand(
                        service,
                        mock(ResourcePackTestCommand.class),
                        mock(ReloadCommand.class),
                        cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN"))
                .onCommand(
                        player,
                        null,
                        "td",
                        new String[] {"recover", UUID.randomUUID().toString(), "delivered"});
        verifyNoInteractions(service);
    }

    @Test
    void consoleRecoveryDelegatesTheExplicitOutcome() throws Exception {
        CorpseService service = mock(CorpseService.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        UUID id = UUID.randomUUID();
        TracesDeathCommand command =
                new TracesDeathCommand(
                        service,
                        mock(ResourcePackTestCommand.class),
                        mock(ReloadCommand.class),
                        cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN"));
        command.onCommand(
                console, null, "td", new String[] {"recover", id.toString(), "delivered"});
        verify(service).resolveClaim(id, true);
        command.onCommand(
                console, null, "td", new String[] {"recover", id.toString(), "not-delivered"});
        verify(service).resolveClaim(id, false);
    }

    @Test
    void invalidRecoveryOutcomeDoesNotChangeState() {
        CorpseService service = mock(CorpseService.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        new TracesDeathCommand(
                        service,
                        mock(ResourcePackTestCommand.class),
                        mock(ReloadCommand.class),
                        cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN"))
                .onCommand(
                        console,
                        null,
                        "td",
                        new String[] {"recover", UUID.randomUUID().toString(), "unknown"});
        verifyNoInteractions(service);
        verify(console).sendMessage(contains("恢复失败"));
    }

    @Test
    void defaultListFiltersOwnershipAndAdminCanSeeAll() {
        CorpseService service = mock(CorpseService.class);
        Player player = mock(Player.class);
        UUID owner = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(owner);
        Corpse own = corpse(owner);
        Corpse other = corpse(UUID.randomUUID());
        when(service.getAll()).thenReturn(List.of(own, other));
        TracesDeathCommand command =
                new TracesDeathCommand(
                        service,
                        mock(ResourcePackTestCommand.class),
                        mock(ReloadCommand.class),
                        cc.sbsj.mc.tracesdeath.language.Messages.bundled("zh_CN"));
        try (var ignored = mockStatic(Bukkit.class)) {
            assertTrue(command.onCommand(player, null, "td", new String[0]));
            verify(player).sendMessage(contains(own.id.toString()));
            verify(player, never()).sendMessage(contains(other.id.toString()));
            when(player.hasPermission("tracesdeath.admin")).thenReturn(true);
            command.onCommand(player, null, "td", new String[] {"LIST"});
            verify(player).sendMessage(contains(other.id.toString()));
        }
    }

    private Corpse corpse(UUID owner) {
        return new Corpse(
                UUID.randomUUID(),
                owner,
                "Player",
                UUID.randomUUID(),
                0,
                64,
                0,
                0,
                0,
                java.util.Collections.emptyList(),
                0,
                Map.of());
    }
}
