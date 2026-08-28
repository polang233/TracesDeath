package cc.sbsj.mc.tracesDeath.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class TraceDataTest {
    @Test
    void ownsCopiesOfLocationAndStorageMetadata() {
        UUID entityId = UUID.randomUUID();
        Location sourceLocation = new Location(null, 1.0, 2.0, 3.0);
        TraceData data = new TraceData(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Player",
                sourceLocation,
                List.of(),
                100L,
                "mannequin",
                Map.of("visual-entity-id", entityId.toString())
        );

        sourceLocation.setX(99.0);
        assertEquals(1.0, data.location().getX());
        data.location().setX(42.0);
        assertEquals(1.0, data.location().getX());
        assertEquals(entityId, data.storageUuid("visual-entity-id").orElseThrow());
        assertThrows(UnsupportedOperationException.class,
                () -> data.storageData().put("interaction-entity-id", "value"));
    }

    @Test
    void rejectsMalformedProviderUuid() {
        TraceData data = new TraceData(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Player",
                new Location(null, 0.0, 0.0, 0.0),
                List.of(),
                100L,
                "mannequin",
                Map.of("visual-entity-id", "not-a-uuid")
        );

        assertFalse(data.storageUuid("visual-entity-id").isPresent());
    }
}
