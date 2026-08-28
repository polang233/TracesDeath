package cc.sbsj.mc.tracesDeath.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class PlacementResultTest {
    @Test
    void protectsLocationAndProviderMetadataFromExternalMutation() {
        Location location = new Location(null, 1.0, 2.0, 3.0);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("visual-entity-id", "entity");

        PlacementResult result = PlacementResult.success("ok", location, metadata);
        location.setX(99.0);
        metadata.put("interaction-entity-id", "proxy");

        assertEquals(1.0, result.location().getX());
        assertEquals(Map.of("visual-entity-id", "entity"), result.storageData());
        result.location().setX(42.0);
        assertEquals(1.0, result.location().getX());
        assertThrows(UnsupportedOperationException.class,
                () -> result.storageData().put("new", "value"));
    }
}
