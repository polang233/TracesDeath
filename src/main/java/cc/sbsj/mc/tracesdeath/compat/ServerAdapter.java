package cc.sbsj.mc.tracesdeath.compat;

import cc.sbsj.mc.tracesdeath.config.CorpseAppearance;
import cc.sbsj.mc.tracesdeath.corpse.SkinProperty;
import cc.sbsj.mc.tracesdeath.entity.CorpseRenderer;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public interface ServerAdapter {
    boolean supportsResourcePack();

    Location findGroundLocation(Location location);

    List<SkinProperty> captureSkin(Player player);

    Inventory createInventory(InventoryHolder holder, String title, boolean textured);

    void configureMenuItem(String role, org.bukkit.inventory.ItemStack item);

    CorpseRenderer createCorpseRenderer(CorpseAppearance.CorpseType visual);
}
