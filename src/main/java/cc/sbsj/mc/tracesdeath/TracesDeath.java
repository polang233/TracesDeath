package cc.sbsj.mc.tracesdeath;

import cc.sbsj.mc.tracesdeath.appearance.CorpseAppearance;
import cc.sbsj.mc.tracesdeath.commands.TracesDeathCommand;
import cc.sbsj.mc.tracesdeath.compat.*;
import cc.sbsj.mc.tracesdeath.config.PluginSettings;
import cc.sbsj.mc.tracesdeath.config.TombstoneConfiguration;
import cc.sbsj.mc.tracesdeath.corpse.CorpseService;
import cc.sbsj.mc.tracesdeath.corpse.CorpseStore;
import cc.sbsj.mc.tracesdeath.metrics.Metrics;
import cc.sbsj.mc.tracesdeath.resourcepack.*;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class TracesDeath extends JavaPlugin {
    private CorpseService service;
    private Metrics metrics;
    private ResourcePackTestService resourcePackTests;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            ServerVersion version = ServerVersion.parse(Bukkit.getBukkitVersion());
            PluginSettings settings = PluginSettings.read(getConfig());
            CustomTextureSettings textures = TombstoneConfiguration.load(this);
            ServerAdapter serverAdapter = ServerAdapterFactory.load(version, textures);
            CorpseAppearance appearance =
                    CorpseAppearance.read(
                            getConfig(), textures, version, serverAdapter.supportsResourcePack());
            service =
                    new CorpseService(
                            this,
                            new CorpseStore(getDataFolder().toPath().resolve("corpses")),
                            settings.isOwnerOnly(),
                            settings.isFillInventory(),
                            appearance,
                            serverAdapter);
            service.start();
            PluginCommand command = Objects.requireNonNull(getCommand("tracesdeath"));
            resourcePackTests = new ResourcePackTestService(this, textures, version);
            try {
                if (appearance.getType() == CorpseAppearance.CorpseType.TOMBSTONE)
                    resourcePackTests.exportPacks();
            } catch (Exception exception) {
                getLogger().log(java.util.logging.Level.WARNING, "导出内置材质包失败", exception);
            }
            TracesDeathCommand handler =
                    new TracesDeathCommand(
                            service,
                            new cc.sbsj.mc.tracesdeath.commands.ResourcePackTestCommand(
                                    resourcePackTests));
            command.setExecutor(handler);
            command.setTabCompleter(handler);
            getLogger().info("遗体核心已启用：固定槽位领取、按记录恢复外观。");
            try {
                metrics = new Metrics(this, 34148);
            } catch (Exception exception) {
                getLogger().log(java.util.logging.Level.WARNING, "bStats 初始化失败", exception);
            }
        } catch (Exception exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "遗体核心启动失败，未接管死亡掉落", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (resourcePackTests != null) resourcePackTests.close();
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
        if (service != null) service.stop();
    }
}
