package cc.sbsj.mc.tracesdeath.compat;

import cc.sbsj.mc.tracesdeath.appearance.CorpseAppearance;
import cc.sbsj.mc.tracesdeath.corpse.Corpse;
import cc.sbsj.mc.tracesdeath.corpse.SkinProperty;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Java 8 / Bukkit 1.12 boundary. No references to modern entity or text classes. */
public class BukkitServerAdapter implements ServerAdapter {
    @Override
    public boolean supportsResourcePack() {
        return false;
    }

    @Override
    public List<SkinProperty> captureSkin(Player player) {
        return Collections.emptyList();
    }

    @Override
    public Inventory createInventory(InventoryHolder holder, String title, boolean textured) {
        return Bukkit.createInventory(holder, 54, title);
    }

    @Override
    public Location findGroundLocation(Location source) {
        Location location = source.clone();
        int minimum = 0;
        try {
            minimum = (Integer) World.class.getMethod("getMinHeight").invoke(source.getWorld());
        } catch (NoSuchMethodException ignored) {
            // Pre-1.17 worlds start at zero.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("读取世界高度失败", exception);
        }
        int y =
                Math.max(
                        minimum + 1,
                        Math.min(source.getWorld().getMaxHeight() - 2, source.getBlockY()));
        while (y > minimum
                && !source.getWorld()
                        .getBlockAt(source.getBlockX(), y - 1, source.getBlockZ())
                        .getType()
                        .isSolid()) y--;
        location.setY(Math.max(minimum + 1, y));
        return location;
    }

    @Override
    public void configureMenuItem(String role, org.bukkit.inventory.ItemStack item) {}

    @Override
    public CorpseRenderer createCorpseRenderer(CorpseAppearance.CorpseType visual) {
        if (visual != CorpseAppearance.CorpseType.CHEST_MINECART)
            throw new IllegalArgumentException("此服务端仅支持箱子矿车遗体");
        return new MinecartRenderer();
    }

    public static final class MinecartRenderer implements CorpseRenderer {
        @Override
        public Location visualLocation(Location anchor) {
            return anchor.clone();
        }

        @Override
        public Entity spawn(
                World world, Location location, Corpse corpse, Consumer<Entity> configure) {
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
                cart.setCustomName(corpse.name + " 的遗体");
                cart.setCustomNameVisible(true);
                return cart;
            } catch (RuntimeException exception) {
                cart.remove();
                throw exception;
            }
        }

        @Override
        public Entity hitbox(
                World world, Location anchor, Entity visual, Consumer<Entity> configure) {
            return visual;
        }

        @Override
        public void update(Entity visual, Corpse corpse) {}
    }
}
