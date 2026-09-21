package cc.sbsj.mc.tracesdeath.entity;

import cc.sbsj.mc.tracesdeath.corpse.Corpse;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

public final class ChestMinecartRenderer implements CorpseRenderer {
    @Override
    public Location visualLocation(Location anchor) {
        return anchor.clone();
    }

    @Override
    public Entity spawn(World world, Location location, Corpse corpse, Consumer<Entity> configure) {
        EntityType type;
        try {
            type = EntityType.valueOf("CHEST_MINECART");
        } catch (IllegalArgumentException ignored) {
            type = EntityType.valueOf("MINECART_CHEST");
        }
        StorageMinecart cart = (StorageMinecart) world.spawnEntity(location, type);
        try {
            configure.accept(cart);
            cart.setMaxSpeed(0);
            cart.setSlowWhenEmpty(true);
            cart.setFlyingVelocityMod(new Vector());
            cart.setDerailedVelocityMod(new Vector());
            cart.getInventory().clear();
            cart.setCustomNameVisible(true);
            return cart;
        } catch (RuntimeException exception) {
            cart.remove();
            throw exception;
        }
    }

    @Override
    public Entity hitbox(World world, Location anchor, Entity visual, Consumer<Entity> configure) {
        return visual;
    }

    @Override
    public void update(Entity visual, Corpse corpse) {}
}
