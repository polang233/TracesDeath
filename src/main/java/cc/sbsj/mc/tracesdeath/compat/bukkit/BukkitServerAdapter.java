package cc.sbsj.mc.tracesdeath.compat.bukkit;

import cc.sbsj.mc.tracesdeath.compat.ServerAdapter;
import cc.sbsj.mc.tracesdeath.config.CorpseAppearance;
import cc.sbsj.mc.tracesdeath.corpse.SkinProperty;
import cc.sbsj.mc.tracesdeath.entity.ChestMinecartRenderer;
import cc.sbsj.mc.tracesdeath.entity.CorpseRenderer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.List;

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
        return Bukkit.createInventory(holder, 54, org.bukkit.ChatColor.DARK_RED + title);
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
        return new ChestMinecartRenderer();
    }
}
