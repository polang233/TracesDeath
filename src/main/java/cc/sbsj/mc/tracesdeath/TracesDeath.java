package cc.sbsj.mc.tracesdeath;

import cc.sbsj.mc.tracesdeath.commands.*;
import cc.sbsj.mc.tracesdeath.core.PluginRuntime;
import cc.sbsj.mc.tracesdeath.hook.Metrics;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class TracesDeath extends JavaPlugin {
    private static final int BSTATS_SERVICE_ID = 34148;
    private PluginRuntime runtime;
    private Metrics metrics;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            runtime = PluginRuntime.prepare(this);
            runtime.start();
            bindCommands();
            getLogger().info("遗体核心已启用：固定槽位领取、按记录恢复外观。");
            try {
                metrics = new Metrics(this, BSTATS_SERVICE_ID);
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "bStats 初始化失败", exception);
            }
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "遗体核心启动失败，未接管死亡掉落", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void bindCommands() {
        PluginCommand command = Objects.requireNonNull(getCommand("tracesdeath"));
        TracesDeathCommand handler =
                new TracesDeathCommand(
                        runtime.getCorpses(),
                        new ResourcePackTestCommand(runtime.getPacks(), runtime.getMessages()),
                        new ReloadCommand(this::reloadSettings, () -> runtime.getMessages()),
                        runtime.getMessages());
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private void reloadSettings() throws Exception {
        PluginRuntime candidate = PluginRuntime.prepare(this);
        PluginRuntime previous = runtime;
        try {
            previous.stop();
            runtime = candidate;
            candidate.start();
            bindCommands();
            reloadConfig();
        } catch (Exception exception) {
            try {
                candidate.stop();
            } catch (Exception cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            runtime = previous;
            try {
                previous.start();
                bindCommands();
            } catch (Exception rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
                getLogger().log(Level.SEVERE, "重载回退失败，已停止插件以保护遗体记录", exception);
                getServer().getPluginManager().disablePlugin(this);
                throw new IllegalStateException("重载回退失败，插件已停用，请查看日志", exception);
            }
            throw new IllegalStateException("配置应用失败，已恢复原设置：" + exception.getMessage(), exception);
        }
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.stop();
            runtime = null;
        }
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }
}
