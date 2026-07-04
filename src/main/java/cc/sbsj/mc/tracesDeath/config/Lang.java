package cc.sbsj.mc.tracesDeath.config;

import java.io.File;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 语言配置管理，支持占位符替换和颜色代码
 */
public final class Lang {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final File file;
    private FileConfiguration config;

    public Lang(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String text(String path) {
        return color(config.getString(path, path));
    }

    public String text(String path, Map<String, String> placeholders) {
        String value = text(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    public Component component(String path, Map<String, String> placeholders) {
        return component(text(path, placeholders));
    }

    public Component component(String value) {
        return LEGACY.deserialize(value);
    }

    public String prefixed(String path) {
        return text("prefix") + text(path);
    }

    public String prefixed(String path, Map<String, String> placeholders) {
        return text("prefix") + text(path, placeholders);
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(component(prefixed(path)));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(component(prefixed(path, placeholders)));
    }

    private static String color(String value) {
        return value.replace('&', (char) 0x00A7);
    }
}
