package cc.sbsj.mc.tracesdeath.resourcepack;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** Optional decoration overrides; real corpse contents never pass through these settings. */
public final class GuiItemSettings {
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final int modelData;

    private GuiItemSettings(ConfigurationSection config) {
        String value = config.getString("material", "").trim();
        material = value.isEmpty() ? null : Material.matchMaterial(value);
        if (!value.isEmpty() && (material == null || material == Material.AIR))
            throw new IllegalArgumentException("GUI 按钮材质无效: " + value);
        name = config.getString("name", "{default}");
        lore = config.isList("lore") ? new ArrayList<>(config.getStringList("lore")) : null;
        modelData = config.getInt("custom-model-data", 0);
        if (modelData < 0) throw new IllegalArgumentException("GUI custom-model-data 必须非负");
    }

    public static GuiItemSettings read(ConfigurationSection config) {
        return new GuiItemSettings(config);
    }

    public int getModelData() {
        return modelData;
    }

    public void apply(ItemStack item) {
        if (material != null) item.setType(material);
        ItemMeta meta = item.getItemMeta();
        String originalName = meta.hasDisplayName() ? meta.getDisplayName() : "";
        meta.setDisplayName(
                ChatColor.translateAlternateColorCodes(
                        '&', name.replace("{default}", originalName)));
        if (lore != null) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                if ("{details}".equals(line)) {
                    if (meta.hasLore()) lines.addAll(meta.getLore());
                } else lines.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lines);
        }
        item.setItemMeta(meta);
    }
}
