package cc.sbsj.mc.tracesdeath.compat;

import cc.sbsj.mc.tracesdeath.appearance.CorpseAppearance;
import cc.sbsj.mc.tracesdeath.corpse.Corpse;
import cc.sbsj.mc.tracesdeath.corpse.SkinProperty;
import cc.sbsj.mc.tracesdeath.resourcepack.CustomTextureSettings;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Loaded only on Paper 1.19.4+, compiled separately for Java 17. */
public final class PaperServerAdapter extends BukkitServerAdapter {
    private final CustomTextureSettings textures;

    public PaperServerAdapter(ServerVersion version, CustomTextureSettings textures) {
        this.textures = textures;
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
        Component component = Component.text(title);
        if (textured) {
            component =
                    Component.text(textures.getTitlePrefix(), NamedTextColor.WHITE)
                            .font(Key.key("minecraft:default"))
                            .append(
                                    component
                                            .font(Key.key("minecraft:default"))
                                            .color(NamedTextColor.WHITE));
        }
        return Bukkit.createInventory(holder, 54, component);
    }

    @Override
    public void configureMenuItem(String role, ItemStack item) {
        var settings = textures.getGuiItem(role);
        if (settings == null) return;
        settings.apply(item);
        if (settings.getModelData() > 0)
            item.editMeta(meta -> meta.setCustomModelData(settings.getModelData()));
    }

    @Override
    public CorpseRenderer createCorpseRenderer(CorpseAppearance.CorpseType visual) {
        if (visual == CorpseAppearance.CorpseType.TOMBSTONE) return new TombstoneRenderer();
        if (visual == CorpseAppearance.CorpseType.MANNEQUIN) {
            try {
                return (CorpseRenderer)
                        Class.forName("cc.sbsj.mc.tracesdeath.compat.MannequinRenderer")
                                .getConstructor()
                                .newInstance();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("加载 Mannequin 适配器失败", exception);
            }
        }
        return super.createCorpseRenderer(visual);
    }

    private final class TombstoneRenderer implements CorpseRenderer {
        @Override
        public Location visualLocation(Location anchor) {
            return anchor.clone().add(0, .5, 0);
        }

        @Override
        public Entity spawn(
                World world, Location location, Corpse corpse, Consumer<Entity> configure) {
            return world.spawn(
                    location,
                    ItemDisplay.class,
                    entity -> {
                        configure.accept(entity);
                        ItemStack item = new ItemStack(textures.getTombstoneMaterial());
                        item.editMeta(
                                meta -> meta.setCustomModelData(textures.getTombstoneModelData()));
                        entity.setItemStack(item);
                        entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                        entity.setDisplayWidth(1.5f);
                        entity.setDisplayHeight(2f);
                        entity.setViewRange(1f);
                    });
        }

        @Override
        public Entity hitbox(
                World world, Location anchor, Entity visual, Consumer<Entity> configure) {
            return world.spawn(
                    anchor,
                    Interaction.class,
                    entity -> {
                        configure.accept(entity);
                        entity.setInteractionWidth(1f);
                        entity.setInteractionHeight(1.5f);
                        entity.setResponsive(true);
                    });
        }

        @Override
        public void update(Entity visual, Corpse corpse) {}
    }
}
