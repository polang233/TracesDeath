package cc.sbsj.mc.tracesdeath.resourcepack;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** Shared configuration for optional client-side visuals and manual pack testing. */
public final class CustomTextureSettings {
    private final boolean guiEnabled;
    private final Material tombstoneMaterial;
    private final int tombstoneModelData;
    private final String titlePrefix, bindAddress, publicUrl;
    private final int port;
    private final java.util.Map<String, GuiItemSettings> guiItems = new java.util.LinkedHashMap<>();

    private CustomTextureSettings(ConfigurationSection config) {
        guiEnabled = config.getBoolean("gui.enabled", true);
        ConfigurationSection items = config.getConfigurationSection("gui.items");
        if (items != null)
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null)
                    throw new IllegalArgumentException("gui.items." + key + " 必须是配置节");
                guiItems.put(key, GuiItemSettings.read(item));
            }
        tombstoneMaterial = Material.matchMaterial(config.getString("tombstone.material", "STONE"));
        tombstoneModelData = config.getInt("tombstone.custom-model-data", 7310000);
        if (tombstoneMaterial == null
                || tombstoneMaterial == Material.AIR
                || tombstoneModelData < 0)
            throw new IllegalArgumentException("tombstone.yml 中墓碑材质或 custom-model-data 无效");
        titlePrefix = config.getString("gui.title-prefix", "‹◆›");
        bindAddress = config.getString("test-server.bind-address", "0.0.0.0");
        publicUrl = config.getString("test-server.public-url", "http://127.0.0.1:8163").trim();
        port = config.getInt("test-server.port", 8163);
    }

    public static CustomTextureSettings read(ConfigurationSection config) {
        return new CustomTextureSettings(config);
    }

    public GuiItemSettings getGuiItem(String role) {
        return guiItems.get(role);
    }

    public boolean isGuiEnabled() {
        return guiEnabled;
    }

    public Material getTombstoneMaterial() {
        return tombstoneMaterial;
    }

    public int getTombstoneModelData() {
        return tombstoneModelData;
    }

    public String getTitlePrefix() {
        return titlePrefix;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public int getPort() {
        return port;
    }
}
