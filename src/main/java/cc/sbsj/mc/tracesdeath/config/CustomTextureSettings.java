package cc.sbsj.mc.tracesdeath.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** Shared configuration for optional client-side visuals and manual pack testing. */
public final class CustomTextureSettings {
    private final boolean guiEnabled;
    private final Material tombstoneMaterial;
    private final int tombstoneModelData;
    private final int fallbackModelData;
    private final boolean playerHeadEnabled;
    private final float playerHeadScale;
    private final String titlePrefix, bindAddress, publicUrl;
    private final int port;
    private final java.util.Map<String, GuiItemSettings> guiItems = new java.util.LinkedHashMap<>();

    private CustomTextureSettings(ConfigurationSection config) {
        playerHeadEnabled = config.getBoolean("tombstone.player-head.enabled", true);
        playerHeadScale = (float) config.getDouble("tombstone.player-head.scale", .55);
        if (!Float.isFinite(playerHeadScale) || playerHeadScale < .1f || playerHeadScale > 1f)
            throw new IllegalArgumentException("tombstone.player-head.scale 必须介于 0.1 和 1.0");
        guiEnabled = config.getBoolean("gui.enabled", true);
        ConfigurationSection items = config.getConfigurationSection("gui.items");
        if (items != null)
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null)
                    throw new IllegalArgumentException("gui.items." + key + " 必须是配置节");
                guiItems.put(key, GuiItemSettings.read(item));
            }
        String materialName = config.getString("tombstone.material", "STONE_PRESSURE_PLATE");
        Material material = Material.matchMaterial(materialName);
        if (material == null && "STONE_PRESSURE_PLATE".equalsIgnoreCase(materialName))
            material = Material.matchMaterial("STONE_PLATE");
        tombstoneMaterial = material;
        tombstoneModelData = config.getInt("tombstone.custom-model-data", 7310000);
        fallbackModelData = config.getInt("tombstone.fallback-model-data", 7310002);
        if (fallbackModelData < 0 || fallbackModelData == tombstoneModelData)
            throw new IllegalArgumentException("墓碑辅助模型编号必须非负，且与主体编号不同");
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

    public boolean isPlayerHeadEnabled() {
        return playerHeadEnabled;
    }

    public float getPlayerHeadScale() {
        return playerHeadScale;
    }

    public int getFallbackModelData() {
        return fallbackModelData;
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
