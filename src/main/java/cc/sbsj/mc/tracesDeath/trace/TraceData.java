package cc.sbsj.mc.tracesDeath.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 墓碑数据记录类
 * <p>
 * 存储单个墓碑的所有信息，包括玩家信息、位置和物品列表。
 */
public final class TraceData {
    private final UUID traceId;
    private final UUID playerId;
    private final String playerName;
    private final Location location;
    private final long creationTime;
    private final String storageType;
    private Map<String, String> storageData;
    private List<ItemStack> items;
    
    public TraceData(@NotNull UUID traceId, @NotNull UUID playerId, 
                     @NotNull String playerName, @NotNull Location location,
                     @NotNull List<ItemStack> items) {
        this(traceId, playerId, playerName, location, items, System.currentTimeMillis(), "block", Map.of());
    }
    
    public TraceData(@NotNull UUID traceId, @NotNull UUID playerId, 
                     @NotNull String playerName, @NotNull Location location,
                     @NotNull List<ItemStack> items, long creationTime) {
        this(traceId, playerId, playerName, location, items, creationTime, "block", Map.of());
    }

    public TraceData(@NotNull UUID traceId, @NotNull UUID playerId,
                     @NotNull String playerName, @NotNull Location location,
                     @NotNull List<ItemStack> items, long creationTime,
                     @NotNull String storageType) {
        this(traceId, playerId, playerName, location, items, creationTime, storageType, Map.of());
    }

    public TraceData(@NotNull UUID traceId, @NotNull UUID playerId,
                     @NotNull String playerName, @NotNull Location location,
                     @NotNull List<ItemStack> items, long creationTime,
                     @NotNull String storageType, @NotNull Map<String, String> storageData) {
        this.traceId = traceId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.location = location.clone();
        this.items = copyItems(items);
        this.creationTime = creationTime;
        this.storageType = storageType;
        this.storageData = Map.copyOf(storageData);
    }
    
    @NotNull
    public UUID traceId() {
        return traceId;
    }
    
    @NotNull
    public UUID playerId() {
        return playerId;
    }
    
    @NotNull
    public String playerName() {
        return playerName;
    }
    
    @NotNull
    public Location location() {
        return location.clone();
    }
    
    public long creationTime() {
        return creationTime;
    }

    @NotNull
    public String storageType() {
        return storageType;
    }

    @NotNull
    public Map<String, String> storageData() {
        return storageData;
    }

    public Optional<UUID> storageUuid(@NotNull String key) {
        String value = storageData.get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
    
    /**
     * 获取物品列表（只读）
     */
    @NotNull
    public List<ItemStack> items() {
        return Collections.unmodifiableList(copyItems(items));
    }
    
    /**
     * 更新物品列表
     */
    public void updateItems(@NotNull List<ItemStack> newItems) {
        this.items = copyItems(newItems);
    }

    public void updateStorageData(@NotNull Map<String, String> newStorageData) {
        this.storageData = Map.copyOf(newStorageData);
    }
    
    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 获取非空物品数量
     */
    public int getItemCount() {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 获取墓碑存在的时间（毫秒）
     */
    public long ageMillis() {
        return System.currentTimeMillis() - creationTime;
    }
    
    /**
     * 获取墓碑存在的时间（秒）
     */
    public long ageSeconds() {
        return ageMillis() / 1000;
    }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>(source.size());
        for (ItemStack item : source) {
            result.add(item == null ? null : item.clone());
        }
        return result;
    }
}
