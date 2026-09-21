package cc.sbsj.mc.tracesdeath.language;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class MessagesTest {
    @TempDir Path directory;

    @Test
    void reloadPicksUpCustomTextAndMissingEnglishEntriesFallBackToEnglish() throws Exception {
        Files.createDirectories(directory.resolve("languages"));
        Files.writeString(directory.resolve("languages/zh_CN.yml"), "{}");
        Path english = directory.resolve("languages/en_US.yml");
        Files.writeString(english, "menu.claim: '&aTake everything'\n");
        var plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        Messages first = Messages.load(plugin, "en_US");
        assertEquals("§aTake everything", first.text("menu.claim"));
        assertEquals("Death details", first.text("menu.info"));
        Files.writeString(english, "menu.claim: 'Collect'\n");
        assertEquals("Collect", Messages.load(plugin, "en_US").text("menu.claim"));
        assertEquals("§aTake everything", first.text("menu.claim"));
        Files.writeString(english, "menu.claim: [broken]\n");
        assertThrows(IllegalArgumentException.class, () -> Messages.load(plugin, "en_US"));
    }
}
