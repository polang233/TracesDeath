package cc.sbsj.mc.tracesDeath.trace;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 墓碑数据缓存管理器
 * <p>
 * 负责存储和管理所有墓碑的物品数据。
 * 支持内存缓存和YML文件持久化，防止服务器异常重启导致数据丢失。
 */
public final class TraceCacheManager {
    private final TracesDeath plugin;
    private final Map<UUID, TraceData> cache = new ConcurrentHashMap<>();
    private final File dataFolder;
    
    public TraceCacheManager(@NotNull TracesDeath plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "traces");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        // 启动时加载所有保存的墓碑数据
        loadAllTraces();
        
        // ItemStack 属于 Bukkit 对象，周期快照在主线程完成。
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveAllTraces, 6000L, 6000L);
    }
    
    /**
     * 创建新的墓碑数据
     */
    @Nullable
    public TraceData createTrace(@NotNull UUID traceId, @NotNull UUID playerId, 
                                  @NotNull String playerName, @NotNull Location location,
                                  @NotNull List<ItemStack> items, @NotNull String storageType,
                                  @NotNull Map<String, String> storageData) {
        TraceData data = new TraceData(traceId, playerId, playerName, location, items,
                System.currentTimeMillis(), storageType, storageData);
        cache.put(traceId, data);
        
        // 立即保存到文件
        if (!saveTrace(traceId)) {
            cache.remove(traceId);
            return null;
        }
        
        if (plugin.traceConfig().debug()) {
            plugin.getLogger().info("创建墓碑缓存: " + traceId + " | 物品数量: " + items.size());
        }
        
        return data;
    }
    
    /**
     * 获取墓碑数据
     */
    @Nullable
    public TraceData getTrace(@NotNull UUID traceId) {
        return cache.get(traceId);
    }
    
    /**
     * 移除墓碑数据
     */
    public boolean removeTrace(@NotNull UUID traceId) {
        TraceData removed = cache.remove(traceId);
        if (removed != null) {
            // 删除对应的文件
            File file = getTraceFile(traceId);
            if (file.exists()) {
                try {
                    Files.delete(file.toPath());
                } catch (IOException exception) {
                    plugin.getLogger().severe(
                            "删除墓碑数据失败: " + traceId + " - " + exception.getMessage());
                }
            }
            
            if (plugin.traceConfig().debug()) {
                plugin.getLogger().info("移除墓碑缓存: " + traceId);
            }
            return true;
        }
        return false;
    }
    
    /**
     * 获取所有活动墓碑
     */
    @NotNull
    public Collection<TraceData> getAllTraces() {
        return Collections.unmodifiableCollection(cache.values());
    }
    
    /**
     * 获取指定玩家的所有墓碑
     */
    @NotNull
    public Collection<TraceData> getPlayerTraces(@NotNull UUID playerId) {
        return cache.values().stream()
                .filter(data -> data.playerId().equals(playerId))
                .toList();
    }
    
    /**
     * 更新墓碑物品（当玩家拿走物品时）
     */
    public void updateTraceItems(@NotNull UUID traceId, @NotNull List<ItemStack> newItems) {
        TraceData data = cache.get(traceId);
        if (data != null) {
            data.updateItems(newItems);
            saveTrace(traceId);
        }
    }

    public void updateTraceStorageData(@NotNull UUID traceId, @NotNull Map<String, String> storageData) {
        TraceData data = cache.get(traceId);
        if (data != null) {
            data.updateStorageData(storageData);
            saveTrace(traceId);
        }
    }
    
    /**
     * 检查墓碑是否为空
     */
    public boolean isTraceEmpty(@NotNull UUID traceId) {
        TraceData data = cache.get(traceId);
        return data == null || data.isEmpty();
    }
    
    /**
     * 保存单个墓碑到文件
     */
    private synchronized boolean saveTrace(@NotNull UUID traceId) {
        TraceData data = cache.get(traceId);
        if (data == null) {
            return false;
        }
        
        File file = getTraceFile(traceId);
        File temporaryFile = new File(dataFolder, traceId + ".yml.tmp");
        YamlConfiguration config = new YamlConfiguration();
        
        config.set("schema-version", 2);
        config.set("trace-id", data.traceId().toString());
        config.set("player-id", data.playerId().toString());
        config.set("player-name", data.playerName());
        config.set("location.world", data.location().getWorld().getName());
        config.set("location.world-id", data.location().getWorld().getUID().toString());
        config.set("location.x", data.location().getX());
        config.set("location.y", data.location().getY());
        config.set("location.z", data.location().getZ());
        config.set("creation-time", data.creationTime());
        config.set("storage-type", data.storageType());
        config.set("storage-data", data.storageData());
        config.set("items", data.items());
        
        try {
            config.save(temporaryFile);
            try {
                Files.move(
                        temporaryFile.toPath(),
                        file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporaryFile.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("保存墓碑数据失败: " + traceId + " - " + e.getMessage());
            try {
                Files.deleteIfExists(temporaryFile.toPath());
            } catch (IOException ignored) {
            }
            return false;
        }
    }
    
    /**
     * 保存所有墓碑到文件
     */
    private void saveAllTraces() {
        for (UUID traceId : cache.keySet()) {
            saveTrace(traceId);
        }
        
        if (plugin.traceConfig().debug()) {
            plugin.getLogger().info("自动保存墓碑数据完成，共 " + cache.size() + " 个墓碑");
        }
    }
    
    /**
     * 从文件加载所有墓碑
     */
    private void loadAllTraces() {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        
        int loadedCount = 0;
        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                int schemaVersion = config.getInt("schema-version", 1);
                if (schemaVersion > 2) {
                    throw new IllegalArgumentException("不支持的数据格式版本: " + schemaVersion);
                }

                UUID traceId = UUID.fromString(config.getString("trace-id"));
                UUID playerId = UUID.fromString(config.getString("player-id"));
                String playerName = config.getString("player-name", "未知玩家");
                String worldName = config.getString("location.world");
                String worldId = config.getString("location.world-id");
                double x = config.getDouble("location.x");
                double y = config.getDouble("location.y");
                double z = config.getDouble("location.z");
                long creationTime = config.getLong("creation-time");
                if (creationTime <= 0) {
                    creationTime = file.lastModified() > 0
                            ? file.lastModified() : System.currentTimeMillis();
                }
                String storageType = config.getString("storage-type", "block")
                        .toLowerCase(Locale.ROOT);
                Map<String, String> storageData = readStorageData(config);
                
                @SuppressWarnings("unchecked")
                List<ItemStack> items = (List<ItemStack>) config.getList("items", new ArrayList<>());
                
                org.bukkit.World world = findWorld(worldId, worldName);
                if (world == null) {
                    plugin.getLogger().warning("无法加载墓碑 " + traceId + ": 世界 " + worldName + " 不存在");
                    continue;
                }
                
                Location location = new Location(world, x, y, z);
                TraceData data = new TraceData(
                        traceId, playerId, playerName, location, items, creationTime, storageType, storageData);
                if (cache.putIfAbsent(traceId, data) != null) {
                    throw new IllegalArgumentException("墓碑 UUID 重复: " + traceId);
                }
                loadedCount++;
                
            } catch (Exception e) {
                plugin.getLogger().severe("加载墓碑文件失败: " + file.getName() + " - " + e.getMessage());
                quarantine(file, e);
            }
        }
        
        if (loadedCount > 0) {
            plugin.getLogger().info("成功加载 " + loadedCount + " 个墓碑数据");
        }
    }
    
    /**
     * 获取墓碑文件路径
     */
    private File getTraceFile(@NotNull UUID traceId) {
        return new File(dataFolder, traceId.toString() + ".yml");
    }

    private Map<String, String> readStorageData(YamlConfiguration config) {
        var section = config.getConfigurationSection("storage-data");
        if (section == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    private org.bukkit.World findWorld(@Nullable String worldId, @Nullable String worldName) {
        if (worldId != null) {
            try {
                org.bukkit.World world = Bukkit.getWorld(UUID.fromString(worldId));
                if (world != null) {
                    return world;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    private void quarantine(File file, Exception cause) {
        File quarantineFolder = new File(dataFolder, "quarantine");
        if (!quarantineFolder.exists() && !quarantineFolder.mkdirs()) {
            plugin.getLogger().severe("无法创建墓碑损坏文件隔离目录: " + quarantineFolder);
            return;
        }
        File target = new File(
                quarantineFolder,
                file.getName() + "." + System.currentTimeMillis() + ".bad"
        );
        try {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().warning(
                    "已隔离损坏墓碑文件: " + file.getName() + " -> " + target.getName()
                            + "，原因: " + cause.getMessage());
        } catch (IOException moveException) {
            plugin.getLogger().severe(
                    "隔离损坏墓碑文件失败: " + file.getName() + " - " + moveException.getMessage());
        }
    }
    
    /**
     * 关闭管理器，保存所有数据
     */
    public void shutdown() {
        saveAllTraces();
        plugin.getLogger().info("墓碑缓存管理器已关闭，数据已保存");
    }
}
