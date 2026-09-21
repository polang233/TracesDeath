package cc.sbsj.mc.tracesdeath.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Rules for taking ownership of death drops. Excluded items remain in the death event. */
public final class DeathSettings {
    public final boolean skipKeepInventory;
    public final int experiencePercent;
    private final List<String> names, lore;

    public DeathSettings(ConfigurationSection config) {
        skipKeepInventory = config.getBoolean("death.skip-keep-inventory", true);
        if (config.contains("experience.keep-percent") && !config.isInt("experience.keep-percent"))
            throw new IllegalArgumentException(
                    "experience.keep-percent must be an integer from 0 to 100");
        experiencePercent = config.getInt("experience.keep-percent", 50);
        if (experiencePercent < 0 || experiencePercent > 100)
            throw new IllegalArgumentException("experience.keep-percent must be between 0 and 100");
        names = terms(config.getStringList("death.exclude-items.name-contains"));
        lore = terms(config.getStringList("death.exclude-items.lore-contains"));
    }

    private static List<String> terms(List<String> values) {
        return values.stream()
                .map(DeathSettings::plain)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String plain(String text) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text))
                .toLowerCase(Locale.ROOT);
    }

    public boolean excludes(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName() && matches(meta.getDisplayName(), names)) return true;
        return meta.hasLore() && meta.getLore().stream().anyMatch(line -> matches(line, lore));
    }

    private static boolean matches(String text, List<String> terms) {
        String value = plain(text);
        return terms.stream().anyMatch(value::contains);
    }
}
