package cc.sbsj.mc.tracesdeath.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

class GuiItemSettingsTest {
    @Test
    void retainsDynamicDetailsWhileCustomizingDecoration() {
        var config = new YamlConfiguration();
        config.set("material", "DIAMOND");
        config.set("name", "&a领取 {default}");
        config.set("lore", List.of("{details}", "&7自定义说明"));
        config.set("custom-model-data", 42);
        var item = mock(ItemStack.class);
        var meta = mock(ItemMeta.class);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("1/3");
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(List.of("死亡时间：2026-09-21", "剩余：5"));
        var settings = GuiItemSettings.read(config);
        settings.apply(item);
        verify(item).setType(Material.DIAMOND);
        verify(meta).setDisplayName("§a领取 1/3");
        verify(meta).setLore(List.of("死亡时间：2026-09-21", "剩余：5", "§7自定义说明"));
        assertEquals(42, settings.getModelData());
    }
}
