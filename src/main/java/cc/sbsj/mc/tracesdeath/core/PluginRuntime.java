package cc.sbsj.mc.tracesdeath.core;

import cc.sbsj.mc.tracesdeath.compat.*;
import cc.sbsj.mc.tracesdeath.config.*;
import cc.sbsj.mc.tracesdeath.corpse.CorpseService;
import cc.sbsj.mc.tracesdeath.language.Messages;
import cc.sbsj.mc.tracesdeath.resourcepack.ResourcePackTestService;
import cc.sbsj.mc.tracesdeath.storage.CorpseStore;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/** Owns one configuration generation and all of its gameplay resources. */
public final class PluginRuntime {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final CorpseAppearance appearance;
    private final ServerAdapter adapter;
    private final CorpseStore store;
    private final ResourcePackTestService packs;
    private CorpseService corpses;
    private final Messages messages;
    private final DeathSettings deathSettings;

    private PluginRuntime(
            JavaPlugin plugin,
            PluginSettings settings,
            CorpseAppearance appearance,
            ServerAdapter adapter,
            CorpseStore store,
            ResourcePackTestService packs,
            Messages messages,
            DeathSettings deathSettings) {
        this.plugin = plugin;
        this.settings = settings;
        this.appearance = appearance;
        this.adapter = adapter;
        this.store = store;
        this.packs = packs;
        this.messages = messages;
        this.deathSettings = deathSettings;
    }

    public static PluginRuntime prepare(JavaPlugin plugin) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File(plugin.getDataFolder(), "config.yml"));
        ServerVersion version = ServerVersion.parse(Bukkit.getBukkitVersion());
        PluginSettings settings = PluginSettings.read(config);
        CustomTextureSettings textures = TombstoneConfiguration.load(plugin, config);
        ServerAdapter adapter = ServerAdapterFactory.load(version, textures);
        CorpseAppearance appearance =
                CorpseAppearance.read(config, textures, version, adapter.supportsResourcePack());
        adapter.createCorpseRenderer(appearance.getType());
        CorpseStore store = new CorpseStore(plugin.getDataFolder().toPath().resolve("corpses"));
        store.load();
        return new PluginRuntime(
                plugin,
                settings,
                appearance,
                adapter,
                store,
                new ResourcePackTestService(plugin, textures, version),
                Messages.load(plugin, config.getString("language", "zh_CN")),
                new DeathSettings(config));
    }

    public void start() throws Exception {
        // Re-read records on every activation, including rollback after partial activation.
        corpses =
                new CorpseService(
                        plugin,
                        store,
                        settings.isOwnerOnly(),
                        settings.isFillInventory(),
                        appearance,
                        adapter,
                        messages,
                        deathSettings);
        corpses.start();
        if (appearance.getType() == CorpseAppearance.CorpseType.TOMBSTONE) {
            try {
                packs.exportPacks();
            } catch (Exception exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "导出内置材质包失败", exception);
            }
        }
    }

    public void stop() {
        try {
            if (corpses != null) corpses.stop();
        } finally {
            packs.close();
            HandlerList.unregisterAll(plugin);
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    public Messages getMessages() {
        return messages;
    }

    public CorpseService getCorpses() {
        return corpses;
    }

    public ResourcePackTestService getPacks() {
        return packs;
    }
}
