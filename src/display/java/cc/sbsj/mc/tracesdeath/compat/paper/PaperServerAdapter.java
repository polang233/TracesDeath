package cc.sbsj.mc.tracesdeath.compat.paper;

import cc.sbsj.mc.tracesdeath.compat.ServerVersion;
import cc.sbsj.mc.tracesdeath.compat.bukkit.BukkitServerAdapter;
import cc.sbsj.mc.tracesdeath.config.CorpseAppearance;
import cc.sbsj.mc.tracesdeath.config.CustomTextureSettings;
import cc.sbsj.mc.tracesdeath.corpse.SkinProperty;
import cc.sbsj.mc.tracesdeath.entity.CorpseRenderer;
import cc.sbsj.mc.tracesdeath.entity.TombstoneRenderer;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Loaded only on Paper 1.19.4+, compiled separately for Java 17. */
public final class PaperServerAdapter extends BukkitServerAdapter {
    private final CustomTextureSettings textures;

    public PaperServerAdapter(ServerVersion version, CustomTextureSettings textures) {
        this.textures = textures;
    }

    @Override
    public List<ItemStack> keptItems(org.bukkit.event.entity.PlayerDeathEvent event) {
        return event.getItemsToKeep();
    }

    @Override
    public boolean supportsResourcePack() {
        return true;
    }

    @Override
    public List<SkinProperty> captureSkin(Player player) {
        List<SkinProperty> result = new ArrayList<>();
        for (var property : player.getPlayerProfile().getProperties()) {
            result.add(
                    new SkinProperty(
                            property.getName(), property.getValue(), property.getSignature()));
        }
        return result;
    }

    @Override
    public Location findGroundLocation(Location source) {
        World world = source.getWorld();
        double y =
                Math.max(
                        world.getMinHeight() + 1,
                        Math.min(world.getMaxHeight() - 2, source.getY()));
        Location start = new Location(world, source.getX(), y + .1, source.getZ());
        var hit =
                world.rayTraceBlocks(
                        start,
                        new Vector(0, -1, 0),
                        y - world.getMinHeight() + 1,
                        FluidCollisionMode.NEVER,
                        true);
        Location result = source.clone();
        result.setY(hit == null ? y : hit.getHitPosition().getY());
        return result;
    }

    @Override
    public Inventory createInventory(InventoryHolder holder, String title, boolean textured) {
        Component component =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(title)
                        .colorIfAbsent(NamedTextColor.DARK_RED);
        if (textured) {
            component =
                    Component.text(textures.getTitlePrefix(), NamedTextColor.WHITE)
                            .font(Key.key("minecraft:default"))
                            .append(component.font(Key.key("minecraft:default")));
        }
        return Bukkit.createInventory(holder, 54, component);
    }

    @Override
    public void configureMenuItem(String role, ItemStack item) {
        var settings = textures.getGuiItem(role);
        if (settings == null) return;
        settings.apply(item);
        if (textures.isGuiEnabled() && settings.getModelData() > 0)
            item.editMeta(meta -> meta.setCustomModelData(settings.getModelData()));
    }

    @Override
    public CorpseRenderer createCorpseRenderer(CorpseAppearance.CorpseType visual) {
        if (visual == CorpseAppearance.CorpseType.TOMBSTONE) return new TombstoneRenderer(textures);
        if (visual == CorpseAppearance.CorpseType.MANNEQUIN) {
            try {
                return (CorpseRenderer)
                        Class.forName("cc.sbsj.mc.tracesdeath.entity.MannequinRenderer")
                                .getConstructor()
                                .newInstance();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("加载 Mannequin 适配器失败", exception);
            }
        }
        return super.createCorpseRenderer(visual);
    }
}
