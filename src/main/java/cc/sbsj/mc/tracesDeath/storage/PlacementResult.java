package cc.sbsj.mc.tracesDeath.storage;

import java.util.Map;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * 墓碑放置结果
 */
public record PlacementResult(
        boolean success,
        String message,
        @Nullable Location location,
        Map<String, String> storageData
) {
    public PlacementResult {
        location = location == null ? null : location.clone();
        storageData = Map.copyOf(storageData);
    }

    @Override
    public @Nullable Location location() {
        return location == null ? null : location.clone();
    }

    public static PlacementResult success(String message) {
        return new PlacementResult(true, message, null, Map.of());
    }

    public static PlacementResult success(String message, Location location) {
        return success(message, location, Map.of());
    }

    public static PlacementResult success(String message, Location location, Map<String, String> storageData) {
        return new PlacementResult(true, message, location, storageData);
    }

    public static PlacementResult failure(String message) {
        return new PlacementResult(false, message, null, Map.of());
    }
}
