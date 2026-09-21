package cc.sbsj.mc.tracesdeath.config;

import static org.junit.jupiter.api.Assertions.*;

import cc.sbsj.mc.tracesdeath.compat.ServerVersion;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CorpseAppearanceTest {
    @Test
    void tombstoneSelectionUsesModelAndOptionalGui() {
        var main = new YamlConfiguration();
        main.set("corpse.type", "tombstone");
        var textures = new YamlConfiguration();
        assertEquals(
                CorpseAppearance.CorpseType.TOMBSTONE,
                read(main, textures, "1.19.4", true).getType());
        assertTrue(read(main, textures, "1.19.4", true).usesCustomMenuTextures());
        textures.set("gui.enabled", true);
        assertTrue(read(main, textures, "1.19.4", true).usesCustomMenuTextures());
        assertThrows(IllegalArgumentException.class, () -> read(main, textures, "1.19.3", true));
        main.set("corpse.type", "mannequin");
        assertFalse(read(main, textures, "1.21.9", true).usesCustomMenuTextures());
    }

    @Test
    void autoRespectsServerCapabilities() {
        var main = new YamlConfiguration();
        var textures = new YamlConfiguration();
        assertEquals(
                CorpseAppearance.CorpseType.CHEST_MINECART,
                read(main, textures, "1.12.2", false).getType());
        assertEquals(
                CorpseAppearance.CorpseType.MANNEQUIN,
                read(main, textures, "1.21.9", true).getType());
    }

    private CorpseAppearance read(
            YamlConfiguration main, YamlConfiguration textures, String version, boolean paper) {
        return CorpseAppearance.read(
                main, CustomTextureSettings.read(textures), ServerVersion.parse(version), paper);
    }
}
