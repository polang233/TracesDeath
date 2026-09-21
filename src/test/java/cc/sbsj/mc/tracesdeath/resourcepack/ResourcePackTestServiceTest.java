package cc.sbsj.mc.tracesdeath.resourcepack;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.sbsj.mc.tracesdeath.compat.ServerVersion;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.zip.ZipFile;

class ResourcePackTestServiceTest {
    @TempDir Path directory;

    @Test
    void exportContainsBothVisualFeaturesAndOnlyCommandSends() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getDescription())
                .thenReturn(new PluginDescriptionFile("TracesDeath", "test", "Main"));
        when(plugin.getResource(anyString()))
                .thenAnswer(
                        call ->
                                getClass()
                                        .getClassLoader()
                                        .getResourceAsStream(call.getArgument(0)));
        try (var bukkit = mockStatic(Bukkit.class)) {
            int port;
            try (var socket = new java.net.ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            var config = new YamlConfiguration();
            config.set("test-server.port", port);
            config.set("test-server.bind-address", "127.0.0.1");
            config.set("test-server.public-url", "http://127.0.0.1:" + port);
            var service =
                    new ResourcePackTestService(
                            plugin,
                            CustomTextureSettings.read(config),
                            ServerVersion.parse("1.20"));
            service.exportPacks();
            bukkit.verifyNoInteractions();
            try (var pack =
                    new ZipFile(
                            directory.resolve("resource-packs/test-tracesdeath.zip").toFile())) {
                assertNotNull(pack.getEntry("assets/minecraft/font/default.json"));
                assertNotNull(pack.getEntry("assets/minecraft/items/stone.json"));
            }
            var player = mock(Player.class);
            service.send(player);
            byte[] expected =
                    Files.readAllBytes(directory.resolve("resource-packs/test-tracesdeath.zip"));
            String url = "http://127.0.0.1:" + port + "/tracesdeath.zip";
            verify(player)
                    .setResourcePack(
                            eq(url),
                            eq(java.security.MessageDigest.getInstance("SHA-1").digest(expected)));
            try (var input = new java.net.URL(url).openStream()) {
                assertArrayEquals(expected, input.readAllBytes());
            }
            service.close();
        }
    }
}
