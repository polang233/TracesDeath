package cc.sbsj.mc.tracesdeath.config;

import cc.sbsj.mc.tracesdeath.resourcepack.CustomTextureSettings;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/** Loads the optional configuration only for the selected tombstone appearance. */
public final class TombstoneConfiguration {
    private TombstoneConfiguration() {}

    public static CustomTextureSettings load(JavaPlugin plugin) throws Exception {
        String type = plugin.getConfig().getString("corpse.type", "auto").trim();
        if (!"tombstone".equalsIgnoreCase(type)) {
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.set("gui.enabled", false);
            return CustomTextureSettings.read(defaults);
        }
        File file = new File(plugin.getDataFolder(), "tombstone.yml");
        if (!file.exists()) plugin.saveResource("tombstone.yml", false);
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        return CustomTextureSettings.read(config);
    }
}
