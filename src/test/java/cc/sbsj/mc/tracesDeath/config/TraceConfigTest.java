package cc.sbsj.mc.tracesDeath.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TraceConfigTest {
    @Test
    void defaultsToMannequinAndSafeInteractionDistance() {
        TraceConfig config = new TraceConfig(new YamlConfiguration());

        assertEquals("mannequin", config.storageType());
        assertEquals(49.0, config.interactionDistanceSquared());
        assertEquals(TraceConfig.InteractionConfig.ClickType.RIGHT_CLICK,
                config.mannequin().interaction().clickType());
    }

    @Test
    void clampsInteractionDistanceAndHitbox() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("interaction.max-distance", -5.0);
        yaml.set("types.mannequin.hitbox.width", 0.0);
        yaml.set("types.mannequin.hitbox.height", -1.0);

        TraceConfig config = new TraceConfig(yaml);

        assertEquals(1.0, config.interactionDistanceSquared());
        assertEquals(0.1, config.mannequin().interactionWidth());
        assertEquals(0.1, config.mannequin().interactionHeight());
    }
}
