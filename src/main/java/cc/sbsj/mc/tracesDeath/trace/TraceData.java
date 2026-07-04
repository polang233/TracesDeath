package cc.sbsj.mc.tracesDeath.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private List<ItemStack> items;
    
    public TraceData(@NotNull UUID traceId, @NotNull UUID playerId, 
                     @NotNull String playerName, @NotNull Location location,
                     @NotNull List<ItemStack> items) {
        this(traceId, playerId, playerName, location, items, System.currentTimeMillis());
    }
    
    public TraceData(@NotNull UUID traceId, @NotNull UUID playerId, 
                     @NotNull String playerName, @NotNull Location location,
                     @NotNull List<ItemStack> items, long creationTime) {
        this.traceId = traceId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.location = location.clone();
        this.items = new ArrayList<>(items);
        this.creationTime = creationTime;
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
    
    /**
     * 获取物品列表（只读）
     */
    @NotNull
    public List<ItemStack> items() {
        return Collections.unmodifiableList(items);
    }
    
    /**
     * 更新物品列表
     */
    public void updateItems(@NotNull List<ItemStack> newItems) {
        this.items = new ArrayList<>(newItems);
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
}
