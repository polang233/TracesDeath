package cc.sbsj.mc.tracesDeath.config;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 插件配置管理类
 * <p>
 * 封装所有配置选项，提供类型安全的访问方法。
 */
public final class TraceConfig {
    // 基础配置
    private final boolean debug;
    private final boolean enabled;
    
    // 存储配置
    private final String storageType;
    
    // 死亡处理配置
    private final boolean ignoreKeepInventory;
    private final boolean clearDrops;
    private final boolean clearExperience;
    private final boolean playSound;
    private final boolean showParticles;
    
    // 放置配置
    private final int searchRadius;
    private final boolean forcePlace;
    
    // 过期配置
    private final long traceExpirationMillis;
    private final long cleanupIntervalTicks;
    private final boolean dropOnExpire;
    private final boolean warnBeforeExpire;
    private final long warnTimeSeconds;
    
    // 保护配置
    private final boolean ownerOnly;
    private final boolean allowBreak;
    private final boolean explosionProof;
    private final double interactionDistanceSquared;
    
    // 各类型独立配置
    private final BlockConfig blockConfig;
    private final MannequinConfig mannequinConfig;

    public TraceConfig(FileConfiguration config) {
        // 基础配置
        this.debug = config.getBoolean("debug", false);
        this.enabled = config.getBoolean("enabled", true);
        
        // 存储配置
        this.storageType = config.getString("storage.type", "block").toLowerCase(Locale.ROOT);
        
        // 死亡处理配置
        this.ignoreKeepInventory = config.getBoolean("death.ignore-keep-inventory", false);
        this.clearDrops = config.getBoolean("death.clear-drops", true);
        this.clearExperience = config.getBoolean("death.clear-experience", false);
        this.playSound = config.getBoolean("death.play-sound", true);
        this.showParticles = config.getBoolean("death.show-particles", true);
        
        // 放置配置
        this.searchRadius = Math.max(0, config.getInt("placement.search-radius", 3));
        this.forcePlace = config.getBoolean("placement.force-place", false);
        
        // 过期配置
        long timeSeconds = Math.max(0, config.getLong("expiration.time-seconds", 600));
        this.traceExpirationMillis = timeSeconds > 0 ? timeSeconds * 1000 : 0;
        long cleanupIntervalSeconds = Math.max(10, config.getLong("expiration.cleanup-interval-seconds", 60));
        this.cleanupIntervalTicks = cleanupIntervalSeconds * 20;
        this.dropOnExpire = config.getBoolean("expiration.drop-on-expire", true);
        this.warnBeforeExpire = config.getBoolean("expiration.warn-before-expire", true);
        this.warnTimeSeconds = Math.max(5, config.getLong("expiration.warn-time-seconds", 30));
        
        // 保护配置
        this.ownerOnly = config.getBoolean("protection.owner-only", false);
        this.allowBreak = config.getBoolean("protection.allow-break", false);
        this.explosionProof = config.getBoolean("protection.explosion-proof", true);
        double interactionDistance = Math.max(1.0, config.getDouble("interaction.max-distance", 7.0));
        this.interactionDistanceSquared = interactionDistance * interactionDistance;
        
        // 各类型独立配置
        this.blockConfig = new BlockConfig(config);
        this.mannequinConfig = new MannequinConfig(config);
    }

    // === 基础配置 ===
    
    public boolean debug() {
        return debug;
    }
    
    public boolean enabled() {
        return enabled;
    }

    // === 存储配置 ===
    
    public String storageType() {
        return storageType;
    }

    // === 死亡处理配置 ===
    
    public boolean ignoreKeepInventory() {
        return ignoreKeepInventory;
    }
    
    public boolean clearDrops() {
        return clearDrops;
    }

    public boolean clearExperience() {
        return clearExperience;
    }
    
    public boolean playSound() {
        return playSound;
    }
    
    public boolean showParticles() {
        return showParticles;
    }

    // === 放置配置 ===
    
    public int searchRadius() {
        return searchRadius;
    }
    
    public boolean forcePlace() {
        return forcePlace;
    }

    // === 过期配置 ===
    
    /**
     * 获取墓碑过期时间（毫秒）
     * @return 过期时间，0 表示永不过期
     */
    public long traceExpirationMillis() {
        return traceExpirationMillis;
    }
    
    /**
     * 获取清理任务间隔（ticks）
     * @return 间隔 ticks
     */
    public long cleanupIntervalTicks() {
        return cleanupIntervalTicks;
    }
    
    public boolean dropOnExpire() {
        return dropOnExpire;
    }
    
    public boolean warnBeforeExpire() {
        return warnBeforeExpire;
    }
    
    public long warnTimeSeconds() {
        return warnTimeSeconds;
    }

    // === 保护配置 ===
    
    public boolean ownerOnly() {
        return ownerOnly;
    }
    
    public boolean allowBreak() {
        return allowBreak;
    }
    
    public boolean explosionProof() {
        return explosionProof;
    }

    public double interactionDistanceSquared() {
        return interactionDistanceSquared;
    }
    
    // === 各类型独立配置 ===
    
    public BlockConfig block() {
        return blockConfig;
    }
    
    public MannequinConfig mannequin() {
        return mannequinConfig;
    }

    public InteractionConfig interactionFor(String storageType) {
        return switch (storageType) {
            case "block" -> blockConfig.interaction();
            case "mannequin" -> mannequinConfig.interaction();
            default -> null;
        };
    }

    public boolean autoRemoveWhenEmpty(String storageType) {
        return switch (storageType) {
            case "block" -> blockConfig.autoRemoveWhenEmpty();
            case "mannequin" -> mannequinConfig.autoRemoveWhenEmpty();
            default -> false;
        };
    }

    // === 工具方法 ===
    
    private static Material readMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(value);
        return material == null ? fallback : material;
    }

    // === 内部配置类 ===
    
    /**
     * 方块容器配置
     */
    public static final class BlockConfig {
        private final Material material;
        private final boolean autoRemoveWhenEmpty;
        private final boolean lavaProof;
        private final InteractionConfig interaction;
        
        BlockConfig(FileConfiguration config) {
            this.material = readMaterial(config.getString("types.block.material", "CHEST"), Material.CHEST);
            this.autoRemoveWhenEmpty = config.getBoolean("types.block.auto-remove-when-empty", true);
            this.lavaProof = config.getBoolean("types.block.lava-proof", true);
            this.interaction = new InteractionConfig(config, "types.block.interaction");
        }
        
        public Material material() { return material; }
        public boolean autoRemoveWhenEmpty() { return autoRemoveWhenEmpty; }
        public boolean lavaProof() { return lavaProof; }
        public InteractionConfig interaction() { return interaction; }
    }

    /**
     * Mannequin 玩家模型配置
     */
    public static final class MannequinConfig {
        private final boolean autoRemoveWhenEmpty;
        private final boolean lavaProof;
        private final double interactionWidth;
        private final double interactionHeight;
        private final InteractionConfig interaction;

        MannequinConfig(FileConfiguration config) {
            this.autoRemoveWhenEmpty = config.getBoolean("types.mannequin.auto-remove-when-empty", true);
            this.lavaProof = config.getBoolean("types.mannequin.lava-proof", true);
            this.interactionWidth = Math.max(0.1, config.getDouble("types.mannequin.hitbox.width", 1.8));
            this.interactionHeight = Math.max(0.1, config.getDouble("types.mannequin.hitbox.height", 1.2));
            this.interaction = new InteractionConfig(config, "types.mannequin.interaction");
        }

        public boolean autoRemoveWhenEmpty() { return autoRemoveWhenEmpty; }
        public boolean lavaProof() { return lavaProof; }
        public double interactionWidth() { return interactionWidth; }
        public double interactionHeight() { return interactionHeight; }
        public InteractionConfig interaction() { return interaction; }
    }
    
    /**
     * 交互模式配置
     */
    public static final class InteractionConfig {
        public enum ClickType {
            RIGHT_CLICK, LEFT_CLICK, BOTH
        }
        
        public enum InteractionMode {
            OPEN_GUI, DIRECT_COLLECT
        }
        
        public enum ConflictHandling {
            DROP, TRY_INVENTORY
        }
        
        private final ClickType clickType;
        private final InteractionMode mode;
        private final boolean autoEquip;
        private final ConflictHandling conflictHandling;
        
        InteractionConfig(FileConfiguration config, String path) {
            this.clickType = parseClickType(config.getString(path + ".click-type", "RIGHT_CLICK"));
            this.mode = parseMode(config.getString(path + ".mode", "OPEN_GUI"));
            this.autoEquip = config.getBoolean(path + ".auto-equip", true);
            this.conflictHandling = parseConflictHandling(config.getString(path + ".conflict-handling", "TRY_INVENTORY"));
        }
        
        public ClickType clickType() { return clickType; }
        public InteractionMode mode() { return mode; }
        public boolean autoEquip() { return autoEquip; }
        public ConflictHandling conflictHandling() { return conflictHandling; }
        
        private static ClickType parseClickType(String value) {
            try {
                return ClickType.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ClickType.RIGHT_CLICK;
            }
        }
        
        private static InteractionMode parseMode(String value) {
            try {
                return InteractionMode.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return InteractionMode.OPEN_GUI;
            }
        }
        
        private static ConflictHandling parseConflictHandling(String value) {
            try {
                return ConflictHandling.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ConflictHandling.TRY_INVENTORY;
            }
        }
    }
}
