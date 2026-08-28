package cc.sbsj.mc.tracesDeath;

import cc.sbsj.mc.tracesDeath.commands.TracesDeathCommand;
import cc.sbsj.mc.tracesDeath.config.Lang;
import cc.sbsj.mc.tracesDeath.config.TraceConfig;
import cc.sbsj.mc.tracesDeath.events.PlayerEvents;
import cc.sbsj.mc.tracesDeath.events.MannequinEvents;
import cc.sbsj.mc.tracesDeath.events.TraceInteractEvents;
import cc.sbsj.mc.tracesDeath.events.TraceProtectionEvents;
import cc.sbsj.mc.tracesDeath.gui.TraceGuiManager;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageRegistry;
import cc.sbsj.mc.tracesDeath.storage.block.BlockContainerTraceProvider;
import cc.sbsj.mc.tracesDeath.storage.entity.MannequinTraceProvider;
import cc.sbsj.mc.tracesDeath.trace.TraceCacheManager;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import cc.sbsj.mc.tracesDeath.trace.TraceManager;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TracesDeath 主插件类
 * <p>
 * 死亡墓碑插件 - 玩家死亡后将掉落物存入容器留在原地。
 * 支持多种存储类型：方块容器、矿车、尸体实体等。
 */
public final class TracesDeath extends JavaPlugin {
    private TraceConfig traceConfig;
    private Lang lang;
    private TraceStorageRegistry storageRegistry;
    private TraceCacheManager cacheManager;
    private TraceGuiManager guiManager;
    private TraceManager traceManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lang = new Lang(this);
        reloadTraceConfig();

        // 初始化存储提供者注册表
        TraceKeys keys = new TraceKeys(this);
        storageRegistry = new TraceStorageRegistry();
        storageRegistry.register(new BlockContainerTraceProvider(keys));
        storageRegistry.register(new MannequinTraceProvider(this, keys));

        // 初始化墓碑缓存管理器
        cacheManager = new TraceCacheManager(this);
        
        // 初始化GUI管理器
        guiManager = new TraceGuiManager(this, cacheManager);
        
        // 初始化墓碑管理器
        traceManager = new TraceManager(this, storageRegistry, cacheManager);
        traceManager.start();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PlayerEvents(this, traceManager), this);
        getServer().getPluginManager().registerEvents(new TraceProtectionEvents(this, keys), this);
        getServer().getPluginManager().registerEvents(new TraceInteractEvents(this, keys, guiManager, cacheManager), this);
        getServer().getPluginManager().registerEvents(new MannequinEvents(this, keys, guiManager, cacheManager), this);

        // 注册命令
        TracesDeathCommand command = new TracesDeathCommand(this, traceManager);
        Objects.requireNonNull(getCommand("tracesdeath"), "tracesdeath command is missing from plugin.yml")
                .setExecutor(command);
        Objects.requireNonNull(getCommand("tracesdeath"), "tracesdeath command is missing from plugin.yml")
                .setTabCompleter(command);
        
        getLogger().info("TracesDeath 插件已启用！当前存储类型: " + traceConfig.storageType());
    }

    @Override
    public void onDisable() {
        if (traceManager != null) {
            traceManager.stop();
        }
        getLogger().info("TracesDeath 插件已禁用");
    }

    /**
     * 重载配置
     */
    public void reloadTraceConfig() {
        reloadConfig();
        lang.reload();
        traceConfig = new TraceConfig(getConfig());
        
        // 如果管理器已存在，重启清理任务
        if (traceManager != null) {
            traceManager.stop();
            traceManager.start();
        }
    }

    public TraceConfig traceConfig() {
        return traceConfig;
    }

    public Lang lang() {
        return lang;
    }
    
    public TraceStorageRegistry storageRegistry() {
        return storageRegistry;
    }
    
    public TraceManager traceManager() {
        return traceManager;
    }
}
