package cc.sbsj.mc.tracesdeath.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class TombstoneConfigurationTest {
    @TempDir Path directory;

    private JavaPlugin plugin(String type) {
        var plugin = mock(JavaPlugin.class);
        var main = new YamlConfiguration();
        main.set("corpse.type", type);
        when(plugin.getConfig()).thenReturn(main);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        return plugin;
    }

    @Test
    void otherTypesDoNotCreateOrReadTheOptionalFile() throws Exception {
        var plugin = plugin("auto");
        assertFalse(TombstoneConfiguration.load(plugin, plugin.getConfig()).isGuiEnabled());
        assertFalse(Files.exists(directory.resolve("tombstone.yml")));
        Files.writeString(directory.resolve("tombstone.yml"), "invalid: [");
        plugin.getConfig().set("corpse.type", "mannequin");
        assertFalse(TombstoneConfiguration.load(plugin, plugin.getConfig()).isGuiEnabled());
        assertEquals("invalid: [", Files.readString(directory.resolve("tombstone.yml")));
        verify(plugin, never()).saveResource(anyString(), anyBoolean());
    }

    @Test
    void tombstoneCreatesOnceAndPreservesAdministratorSettings() throws Exception {
        var plugin = plugin(" tombstone ");
        doAnswer(
                        call -> {
                            try (var input =
                                    getClass()
                                            .getClassLoader()
                                            .getResourceAsStream("tombstone.yml")) {
                                Files.copy(input, directory.resolve("tombstone.yml"));
                            }
                            return null;
                        })
                .when(plugin)
                .saveResource("tombstone.yml", false);
        assertTrue(TombstoneConfiguration.load(plugin, plugin.getConfig()).isGuiEnabled());
        var config =
                YamlConfiguration.loadConfiguration(directory.resolve("tombstone.yml").toFile());
        config.set("gui.enabled", true);
        config.set("tombstone.custom-model-data", 42);
        config.save(directory.resolve("tombstone.yml").toFile());
        var settings = TombstoneConfiguration.load(plugin, plugin.getConfig());
        assertTrue(settings.isGuiEnabled());
        assertEquals(42, settings.getTombstoneModelData());
        verify(plugin, times(1)).saveResource("tombstone.yml", false);
    }
}
