package cc.sbsj.mc.tracesdeath.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

class DeathSettingsTest {
    @Test
    void excludesNamesAndLoreWithoutMatchingColorCodesOrEmptyRules() {
        var yaml = new YamlConfiguration();
        yaml.set("death.exclude-items.name-contains", List.of("&aBound", ""));
        yaml.set("death.exclude-items.lore-contains", List.of("灵魂绑定"));
        var settings = new DeathSettings(yaml);
        var item = mock(ItemStack.class);
        var meta = mock(ItemMeta.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("§6BOUND sword");
        assertTrue(settings.excludes(item));
        when(meta.getDisplayName()).thenReturn("普通剑");
        assertFalse(settings.excludes(item));
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(List.of("§7拥有者：Player", "§a灵魂绑定"));
        assertTrue(settings.excludes(item));
    }
}
