package cc.sbsj.mc.tracesdeath.compat;

import cc.sbsj.mc.tracesdeath.corpse.Corpse;

import com.destroystokyo.paper.SkinParts;
import com.destroystokyo.paper.profile.ProfileProperty;

import io.papermc.paper.datacomponent.item.ResolvableProfile;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.util.function.Consumer;

/** Loaded only for the Mannequin selection on Paper 1.21.9+, compiled for Java 21. */
public final class MannequinRenderer implements CorpseRenderer {
    @Override
    public Location visualLocation(Location anchor) {
        double angle = Math.toRadians(anchor.getYaw());
        return anchor.clone().add(Math.cos(angle) * .9, 0, -Math.sin(angle) * .9);
    }

    @Override
    public Entity spawn(World world, Location location, Corpse corpse, Consumer<Entity> configure) {
        return world.spawn(
                location,
                Mannequin.class,
                entity -> {
                    configure.accept(entity);
                    var profile = Bukkit.createProfile(corpse.owner, corpse.name);
                    for (var property : corpse.skin)
                        profile.setProperty(
                                new ProfileProperty(
                                        property.name, property.value, property.signature));
                    entity.setProfile(ResolvableProfile.resolvableProfile(profile));
                    entity.setSkinParts(SkinParts.allParts());
                    entity.setDescription(null);
                    entity.customName(Component.text(corpse.name + " 的遗体"));
                    entity.setCustomNameVisible(true);
                    entity.setImmovable(true);
                    entity.setAI(false);
                    entity.setNoPhysics(true);
                    entity.setCollidable(false);
                    entity.setRemoveWhenFarAway(false);
                    entity.setPose(Pose.SLEEPING, true);
                    update(entity, corpse);
                });
    }

    @Override
    public Entity hitbox(World world, Location anchor, Entity visual, Consumer<Entity> configure) {
        return world.spawn(
                anchor,
                Interaction.class,
                entity -> {
                    configure.accept(entity);
                    entity.setInteractionWidth(1.8f);
                    entity.setInteractionHeight(.8f);
                    entity.setResponsive(true);
                });
    }

    @Override
    public void update(Entity visual, Corpse corpse) {
        var items = corpse.items();
        var equipment = ((Mannequin) visual).getEquipment();
        equipment.setHelmet(items.get(39));
        equipment.setChestplate(items.get(38));
        equipment.setLeggings(items.get(37));
        equipment.setBoots(items.get(36));
        equipment.setItemInMainHand(items.get(corpse.heldSlot));
        equipment.setItemInOffHand(items.get(40));
    }
}
