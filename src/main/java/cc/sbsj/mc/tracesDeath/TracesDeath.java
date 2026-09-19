package cc.sbsj.mc.tracesDeath;

import cc.sbsj.mc.tracesDeath.corpse.CorpseService;
import cc.sbsj.mc.tracesDeath.corpse.CorpseStore;
import java.nio.file.Files;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class TracesDeath extends JavaPlugin {
    private CorpseService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            var legacy = getDataFolder().toPath().resolve("traces");
            if (Files.isDirectory(legacy)) {
                try (var files = Files.list(legacy)) {
                    if (files.anyMatch(path -> path.toString().endsWith(".yml"))) {
                        throw new IllegalStateException("发现旧版 traces 数据。请先使用旧版领取完物品、备份并移走旧数据，再启用新版。新版不会修改旧记录。");
                    }
                }
            }
            service = new CorpseService(this, new CorpseStore(getDataFolder().toPath().resolve("corpses")),
                    getConfig().getBoolean("owner-only", false));
            service.start();
            var command = Objects.requireNonNull(getCommand("tracesdeath"));
            command.setExecutor(service);
            command.setTabCompleter(service);
            getLogger().info("遗体核心已启用：非持久化 Mannequin、固定槽位、只取不存。");
        } catch (Exception exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "遗体核心启动失败，未接管死亡掉落", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (service != null) service.stop();
    }
}
