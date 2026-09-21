package cc.sbsj.mc.tracesdeath.language;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Immutable per-runtime language catalog. Missing entries use the selected bundled language. */
public final class Messages {
    private final YamlConfiguration values;

    private Messages(YamlConfiguration values) {
        this.values = values;
    }

    public static Messages bundled(String language) {
        String path = "languages/" + language + ".yml";
        try (InputStream stream = Messages.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("Unknown language: " + language);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new Messages(yaml);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot read " + path, exception);
        }
    }

    public static Messages load(JavaPlugin plugin, String language) throws Exception {
        if (!language.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid language filename");
        for (String bundled : Arrays.asList("zh_CN", "en_US")) {
            String path = "languages/" + bundled + ".yml";
            if (!new File(plugin.getDataFolder(), path).exists()) plugin.saveResource(path, false);
        }
        File file = new File(plugin.getDataFolder(), "languages/" + language + ".yml");
        if (!file.isFile()) throw new FileNotFoundException("Language file not found: " + file);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        YamlConfiguration defaults = bundled(language.equals("en_US") ? "en_US" : "zh_CN").values;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (!yaml.contains(key)) yaml.set(key, defaults.get(key));
            if (defaults.isList(key) ? !yaml.isList(key) : !yaml.isString(key))
                throw new IllegalArgumentException("Invalid language entry: " + key);
        }
        return new Messages(yaml);
    }

    public String text(String key, Object... replacements) {
        return format(Objects.requireNonNull(values.getString(key), key), replacements);
    }

    public List<String> lines(String key, Object... replacements) {
        return values.getStringList(key).stream()
                .map(s -> format(s, replacements))
                .collect(Collectors.toList());
    }

    private String format(String text, Object... replacements) {
        // Parse catalog colors before substituting player/item text.
        text = ChatColor.translateAlternateColorCodes('&', text);
        for (int i = 0; i < replacements.length; i += 2)
            text = text.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        return text;
    }
}
