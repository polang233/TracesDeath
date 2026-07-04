package cc.sbsj.mc.tracesDeath.storage;

/**
 * 墓碑放置结果
 */
public record PlacementResult(boolean success, String message) {
    public static PlacementResult success(String message) {
        return new PlacementResult(true, message);
    }

    public static PlacementResult failure(String message) {
        return new PlacementResult(false, message);
    }
}
