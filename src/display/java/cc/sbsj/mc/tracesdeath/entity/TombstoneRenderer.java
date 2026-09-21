package cc.sbsj.mc.tracesdeath.entity;

import cc.sbsj.mc.tracesdeath.config.CustomTextureSettings;
import cc.sbsj.mc.tracesdeath.corpse.Corpse;

import com.destroystokyo.paper.profile.ProfileProperty;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Consumer;

public final class TombstoneRenderer implements CorpseRenderer {
    private final CustomTextureSettings textures;

    public TombstoneRenderer(CustomTextureSettings textures) {
        this.textures = textures;
    }

    @Override
    public Location visualLocation(Location anchor) {
        return anchor.clone().add(0, .5, 0);
    }

    @Override
    public Entity spawn(World world, Location location, Corpse corpse, Consumer<Entity> configure) {
        Entity primary =
                world.spawn(
                        location,
                        ItemDisplay.class,
                        entity -> {
                            configure.accept(entity);
                            ItemStack item = new ItemStack(textures.getTombstoneMaterial());
                            item.editMeta(
                                    meta ->
                                            meta.setCustomModelData(
                                                    textures.getTombstoneModelData()));
                            entity.setItemStack(item);
                            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                            entity.setDisplayWidth(1.5f);
                            entity.setDisplayHeight(2f);
                            entity.setViewRange(1f);
                        });
        // All members share an entity position/chunk; transformations arrange the local geometry.
        part(world, location, configure, Material.MOSSY_STONE_BRICKS, .95f, .15f, .75f, .1375f, 0);
        part(world, location, configure, Material.STONE, .80f, .12f, .54f, .2725f, 0);
        part(world, location, configure, Material.MOSSY_STONE_BRICKS, .64f, 1.00f, .22f, .8325f, 0);
        part(world, location, configure, Material.STONE, .52f, .12f, .24f, 1.3925f, 0);
        part(
                world,
                location,
                configure,
                Material.POLISHED_BLACKSTONE,
                .44f,
                .60f,
                .04f,
                .82f,
                -.14f);
        if (textures.isPlayerHeadEnabled()) playerHead(world, location, corpse, configure);
        return primary;
    }

    private void part(
            World world,
            Location origin,
            Consumer<Entity> configure,
            Material material,
            float width,
            float height,
            float depth,
            float centerHeight,
            float centerZ) {
        world.spawn(
                origin,
                ItemDisplay.class,
                entity -> {
                    configure.accept(entity);
                    ItemStack stone = new ItemStack(material);
                    stone.editMeta(
                            meta -> meta.setCustomModelData(textures.getFallbackModelData()));
                    entity.setItemStack(stone);
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    entity.setTransformation(
                            new Transformation(
                                    new Vector3f(0, centerHeight - .5f, centerZ), new Quaternionf(),
                                    new Vector3f(width, height, depth), new Quaternionf()));
                    entity.setDisplayWidth(2f);
                    entity.setDisplayHeight(3f);
                    entity.setViewRange(1f);
                });
    }

    private void playerHead(
            World world, Location origin, Corpse corpse, Consumer<Entity> configure) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        var profile = Bukkit.createProfile(corpse.owner, corpse.name);
        for (var property : corpse.skin)
            profile.setProperty(
                    new ProfileProperty(property.name, property.value, property.signature));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        world.spawn(
                origin,
                ItemDisplay.class,
                entity -> {
                    configure.accept(entity);
                    entity.setItemStack(head);
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    entity.setTransformation(
                            new Transformation(
                                    new Vector3f(0, .45f, -.14f),
                                    new Quaternionf(),
                                    new Vector3f(textures.getPlayerHeadScale()),
                                    new Quaternionf()));
                    entity.setDisplayWidth(2f);
                    entity.setDisplayHeight(3f);
                    entity.setViewRange(1f);
                });
    }

    @Override
    public Entity hitbox(World world, Location anchor, Entity visual, Consumer<Entity> configure) {
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
