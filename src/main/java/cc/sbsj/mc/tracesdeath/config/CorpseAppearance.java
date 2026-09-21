package cc.sbsj.mc.tracesdeath.config;

import cc.sbsj.mc.tracesdeath.compat.ServerVersion;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Version-checked type selection. AUTO uses the player model where supported, otherwise a chest
 * minecart.
 */
public final class CorpseAppearance {
    public static final CorpseAppearance VANILLA =
            new CorpseAppearance(CorpseType.MANNEQUIN, false);

    public enum CorpseType {
        MANNEQUIN,
        TOMBSTONE,
        CHEST_MINECART
    }

    private final CorpseType type;
    private final boolean customMenuTextures;

    public CorpseAppearance(CorpseType type, boolean customMenuTextures) {
        this.type = type;
        this.customMenuTextures = customMenuTextures;
    }

    public CorpseType getType() {
        return type;
    }

    public boolean usesCustomMenuTextures() {
        return customMenuTextures;
    }

    public static CorpseAppearance read(
            ConfigurationSection config,
            cc.sbsj.mc.tracesdeath.config.CustomTextureSettings textures,
            ServerVersion version,
            boolean modernPaper) {
        if (!version.atLeast(1, 12, 0))
            throw new IllegalArgumentException("TracesDeath 需要 Minecraft 1.12 或更高版本");
        String configuredType =
                config.getString("corpse.type", "auto").trim().toUpperCase(Locale.ROOT);
        CorpseType type =
                "AUTO".equals(configuredType)
                        ? (modernPaper && version.atLeast(1, 21, 9)
                                ? CorpseType.MANNEQUIN
                                : CorpseType.CHEST_MINECART)
                        : CorpseType.valueOf(configuredType);
        if (type == CorpseType.MANNEQUIN && (!modernPaper || !version.atLeast(1, 21, 9))) {
            throw new IllegalArgumentException(
                    "mannequin 需要 Paper 1.21.9+；可选 chest_minecart 或 auto");
        }
        if (type == CorpseType.TOMBSTONE && (!modernPaper || !version.atLeast(1, 19, 4))) {
            throw new IllegalArgumentException(
                    "tombstone 需要 Paper 1.19.4+；可选 chest_minecart 或 auto");
        }
        boolean menu = type == CorpseType.TOMBSTONE && textures.isGuiEnabled();
        return new CorpseAppearance(type, menu);
    }
}
