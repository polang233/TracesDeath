package cc.sbsj.mc.tracesDeath.trace;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.config.TraceConfig;
import cc.sbsj.mc.tracesDeath.storage.PlacementResult;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageProvider;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 墓碑管理器
 * <p>
 * 负责跟踪、管理和清理所有活动的墓碑。
 * 提供墓碑的创建、查询、移除和自动过期功能。
 */
public final class TraceManager {
    private final TracesDeath plugin;
    private final TraceStorageRegistry storageRegistry;
    private final TraceCacheManager cacheManager;
    private final Map<UUID, TraceInfo> activeTraces = new ConcurrentHashMap<>();
    private final Set<UUID> terminatingTraces = ConcurrentHashMap.newKeySet();
    private BukkitTask cleanupTask;
    
    public TraceManager(@NotNull TracesDeath plugin, @NotNull TraceStorageRegistry storageRegistry,
                        @NotNull TraceCacheManager cacheManager) {
        this.plugin = plugin;
        this.storageRegistry = storageRegistry;
        this.cacheManager = cacheManager;
    }
    
    /**
     * 启动墓碑管理器，开始定期清理过期的墓碑
     */
    public void start() {
        loadCachedTraces();
        Bukkit.getScheduler().runTask(plugin, this::reconcileLoadedTraces);
        long interval = plugin.traceConfig().cleanupIntervalTicks();
        if (interval > 0) {
            cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredTraces, interval, interval);
            plugin.getLogger().info("墓碑管理器已启动，清理间隔: " + (interval / 20) + "秒");
        }
    }
    
    /**
     * 停止墓碑管理器
     */
    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        cacheManager.shutdown();
    }
    
    /**
     * 创建新的墓碑
     * @param player 死亡的玩家
     * @param location 墓碑位置
     * @param drops 掉落物品列表
     * @return 创建结果
     */
    @NotNull
    public PlacementResult createTrace(@NotNull Player player, @NotNull Location location, @NotNull Collection<ItemStack> drops) {
        UUID traceId = UUID.randomUUID();
        TraceContext context = new TraceContext(
                traceId,
                player,
                location,
                drops.stream().toList(),
                plugin.traceConfig(),
                plugin.lang()
        );
        
        PlacementResult result = storageRegistry.place(context);
        if (result.success()) {
            Location placedLocation = result.location() == null ? location.clone() : result.location();
            TraceData data = cacheManager.createTrace(traceId, player.getUniqueId(), player.getName(),
                    placedLocation, drops.stream().toList(), context.config().storageType(), result.storageData());
            if (data == null) {
                TraceStorageProvider provider =
                        storageRegistry.find(context.config().storageType()).orElse(null);
                if (provider != null) {
                    TraceData placementData = new TraceData(
                            traceId,
                            player.getUniqueId(),
                            player.getName(),
                            placedLocation,
                            drops.stream().toList(),
                            System.currentTimeMillis(),
                            context.config().storageType(),
                            result.storageData()
                    );
                    provider.cleanup(placementData);
                }
                return PlacementResult.failure(plugin.lang().text("storage.persistence-failed"));
            }
            
            TraceInfo info = new TraceInfo(
                    traceId,
                    player.getUniqueId(),
                    player.getName(),
                    placedLocation,
                    data.creationTime(),
                    context.config().storageType()
            );
            activeTraces.put(traceId, info);
            
            if (plugin.traceConfig().debug()) {
                plugin.getLogger().info("创建墓碑: " + traceId + " | 玩家: " + player.getName() + 
                        " | 类型: " + context.config().storageType() + " | 位置: " + formatLocation(location));
            }
        }
        
        return result;
    }
    
    /**
     * 移除指定墓碑
     * @param traceId 墓碑ID
     * @param dropItems 是否将容器内物品掉落
     * @return 如果成功移除返回 true
     */
    public boolean removeTrace(@NotNull UUID traceId, boolean dropItems) {
        if (!terminatingTraces.add(traceId)) {
            return false;
        }
        try {
            TraceInfo info = activeTraces.get(traceId);
            if (info == null) {
                return false;
            }

            TraceData data = cacheManager.getTrace(traceId);
            if (data == null) {
                activeTraces.remove(traceId);
                return false;
            }

            TraceStorageProvider provider = storageRegistry.find(data.storageType()).orElse(null);
            if (provider == null) {
                plugin.getLogger().warning("墓碑类型没有可用提供者，已保留数据: " + traceId);
                return false;
            }
            if (!provider.cleanup(data)) {
                plugin.getLogger().warning("无法清理墓碑世界对象，已保留数据: " + traceId);
                return false;
            }

            if (dropItems && !data.isEmpty()) {
                for (ItemStack item : data.items()) {
                    if (item != null && !item.getType().isAir()) {
                        Location dropLocation = info.location().clone().add(0.5, 0.5, 0.5);
                        info.location().getWorld().dropItemNaturally(dropLocation, item.clone());
                    }
                }
            }

            cacheManager.removeTrace(traceId);
            activeTraces.remove(traceId);

            if (plugin.traceConfig().debug()) {
                plugin.getLogger().info("移除墓碑: " + traceId + " | 玩家: " + info.playerName());
            }
            return true;
        } finally {
            terminatingTraces.remove(traceId);
        }
    }

    public boolean isTerminating(@NotNull UUID traceId) {
        return terminatingTraces.contains(traceId);
    }
    
    /**
     * 获取指定墓碑信息
     * @param traceId 墓碑ID
     * @return 墓碑信息，如果不存在返回 null
     */
    @Nullable
    public TraceInfo getTrace(@NotNull UUID traceId) {
        return activeTraces.get(traceId);
    }
    
    /**
     * 获取所有活动墓碑
     * @return 不可修改的墓碑集合
     */
    @NotNull
    public Collection<TraceInfo> getActiveTraces() {
        return Collections.unmodifiableCollection(activeTraces.values());
    }
    
    /**
     * 获取指定玩家的所有墓碑
     * @param playerId 玩家UUID
     * @return 该玩家的墓碑列表
     */
    @NotNull
    public Collection<TraceInfo> getPlayerTraces(@NotNull UUID playerId) {
        return activeTraces.values().stream()
                .filter(info -> info.playerId().equals(playerId))
                .toList();
    }
    
    /**
     * 获取活动墓碑数量
     * @return 当前活动墓碑总数
     */
    public int getActiveCount() {
        return activeTraces.size();
    }

    public boolean reconcileTrace(@NotNull UUID traceId) {
        TraceData data = cacheManager.getTrace(traceId);
        if (data == null) {
            return false;
        }
        TraceStorageProvider provider = storageRegistry.find(data.storageType()).orElse(null);
        if (provider == null) {
            return false;
        }
        try {
            Map<String, String> storageData = provider.reconcile(data);
            if (!storageData.equals(data.storageData())) {
                cacheManager.updateTraceStorageData(traceId, storageData);
            }
            return provider.isValid(data);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe(
                    "修复墓碑世界对象失败: " + traceId + " - " + exception.getMessage());
            return false;
        }
    }
    
    /**
     * 清理过期的墓碑
     */
    private void cleanupExpiredTraces() {
        long expirationTime = plugin.traceConfig().traceExpirationMillis();
        if (expirationTime <= 0) {
            return; // 禁用自动过期
        }
        
        long now = System.currentTimeMillis();
        int removedCount = 0;
        
        for (Map.Entry<UUID, TraceInfo> entry : activeTraces.entrySet()) {
            TraceInfo info = entry.getValue();
            if (now - info.creationTime() > expirationTime) {
                if (removeTrace(entry.getKey(), plugin.traceConfig().dropOnExpire())) {
                    removedCount++;
                }
            }
        }
        
        if (removedCount > 0 && plugin.traceConfig().debug()) {
            plugin.getLogger().info("清理了 " + removedCount + " 个过期墓碑");
        }
    }

    private void loadCachedTraces() {
        activeTraces.clear();
        for (TraceData data : cacheManager.getAllTraces()) {
            activeTraces.put(data.traceId(), new TraceInfo(
                    data.traceId(),
                    data.playerId(),
                    data.playerName(),
                    data.location(),
                    data.creationTime(),
                    data.storageType()
            ));
        }
    }

    private void reconcileLoadedTraces() {
        for (TraceData data : cacheManager.getAllTraces()) {
            Location location = data.location();
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (location.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                reconcileTrace(data.traceId());
            }
        }
    }
    
    /**
     * 格式化位置为可读字符串
     */
    private String formatLocation(Location location) {
        return String.format("%s[%.1f, %.1f, %.1f]", 
                location.getWorld().getName(), 
                location.getX(), 
                location.getY(), 
                location.getZ());
    }
    
    /**
     * 墓碑信息记录类
     */
    public record TraceInfo(
            @NotNull UUID traceId,
            @NotNull UUID playerId,
            @NotNull String playerName,
            @NotNull Location location,
            long creationTime,
            @NotNull String storageType
    ) {
        /**
         * 获取痕迹存在的时间（毫秒）
         */
        public long ageMillis() {
            return System.currentTimeMillis() - creationTime;
        }
        
        /**
         * 获取痕迹存在的时间（秒）
         */
        public long ageSeconds() {
            return ageMillis() / 1000;
        }
    }
}
