package cc.sbsj.mc.tracesdeath.entity;

import cc.sbsj.mc.tracesdeath.corpse.Corpse;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.function.Consumer;

public interface CorpseRenderer {
    Location visualLocation(Location anchor);

    /** Every spawned display part must call configure so it joins the managed entity group. */
    Entity spawn(World world, Location location, Corpse corpse, Consumer<Entity> configure);

    Entity hitbox(World world, Location anchor, Entity visual, Consumer<Entity> configure);

    void update(Entity visual, Corpse corpse);
}
