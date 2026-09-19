package cc.sbsj.mc.tracesDeath.corpse;

import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.destroystokyo.paper.SkinParts;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

/** Disposable world views. Only CorpseStore owns durable state. */
public final class CorpseEntities implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Corpse> corpses;
    private final Map<UUID, Pair> visible = new HashMap<>();
    private final NamespacedKey key;
    private final java.util.function.BiConsumer<Player, UUID> open;
    private final Consumer<UUID> close;

    public CorpseEntities(JavaPlugin plugin, Map<UUID, Corpse> corpses,
                          java.util.function.BiConsumer<Player, UUID> open, Consumer<UUID> close) {
        this.plugin = plugin;
        this.corpses = corpses;
        this.open = open;
        this.close = close;
        key = new NamespacedKey(plugin, "corpse-id");
    }

    public void reconcileAll() {
        for (Corpse corpse : List.copyOf(corpses.values())) show(corpse);
    }

    private Location anchor(Corpse corpse, World world) {
        return new Location(world, corpse.x, corpse.y, corpse.z, corpse.yaw, 0);
    }

    private Location modelLocation(Corpse corpse, World world) {
        double angle = Math.toRadians(corpse.yaw);
        return anchor(corpse, world).add(Math.cos(angle) * .9, 0, -Math.sin(angle) * .9);
    }

    private boolean loaded(Location location) {
        return location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public void show(Corpse corpse) {
        World world = Bukkit.getWorld(corpse.world);
        if (world == null || corpse.empty()) return;
        Location anchor = anchor(corpse, world), model = modelLocation(corpse, world);
        if (!loaded(anchor) || !loaded(model)) return;
        Pair current = visible.get(corpse.id);
        if (current != null && current.visual.isValid() && current.hitbox.isValid()) {
            equipment(current.visual, corpse);
            return;
        }
        hide(corpse.id);
        Mannequin visual = null;
        Interaction hitbox = null;
        try {
            // Configure before addition to the world, including non-persistence.
            visual = world.spawn(model, Mannequin.class, entity -> {
                identity(entity, corpse.id);
                entity.setProfile(ResolvableProfile.resolvableProfile(corpse.profile));
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
                equipment(entity, corpse);
            });
            hitbox = world.spawn(anchor, Interaction.class, entity -> {
                identity(entity, corpse.id);
                entity.setInteractionWidth(1.8f);
                entity.setInteractionHeight(.8f);
                entity.setResponsive(true);
            });
            visible.put(corpse.id, new Pair(visual, hitbox));
        } catch (RuntimeException exception) {
            remove(visual);
            remove(hitbox);
            plugin.getLogger().severe("遗体外观创建失败，物品记录已保留: " + corpse.id + " " + exception);
        }
    }

    private void identity(Entity entity, UUID id) {
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, id.toString());
    }

    private void equipment(Mannequin visual, Corpse corpse) {
        var items = corpse.items();
        var equipment = visual.getEquipment();
        equipment.setHelmet(items.get(39));
        equipment.setChestplate(items.get(38));
        equipment.setLeggings(items.get(37));
        equipment.setBoots(items.get(36));
        equipment.setItemInOffHand(items.get(40));
        equipment.setItemInMainHand(items.get(corpse.heldSlot));
    }

    public void hide(UUID id) {
        Pair pair = visible.remove(id);
        if (pair != null) {
            remove(pair.visual);
            remove(pair.hitbox);
        }
    }

    private void remove(Entity entity) {
        if (entity == null) return;
        if (entity instanceof Mannequin mannequin) mannequin.getEquipment().clear();
        entity.remove();
    }

    public void shutdown() {
        for (UUID id : List.copyOf(visible.keySet())) hide(id);
    }

    private UUID id(Entity entity) {
        String value = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value == null) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent event) {
        // Paper 26.1 shares this handler list with precise clicks. Let interactAt own them.
        if (event instanceof PlayerInteractAtEntityEvent) return;
        UUID id = id(event.getRightClicked());
        if (id == null) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            open.accept(event.getPlayer(), id);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void interactAt(PlayerInteractAtEntityEvent event) {
        UUID id = id(event.getRightClicked());
        if (id == null) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) open.accept(event.getPlayer(), id);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (id(event.getEntity()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        if (id(event.getEntity()) == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler
    public void load(EntitiesLoadEvent event) {
        // Remove any stale tagged objects before reconstructing views. Never trust saved entity IDs.
        for (Entity entity : event.getEntities()) if (id(entity) != null) remove(entity);
        Bukkit.getScheduler().runTask(plugin, this::reconcileAll);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(ChunkUnloadEvent event) {
        for (var entry : List.copyOf(visible.entrySet())) {
            Pair pair = entry.getValue();
            if (inChunk(pair.visual.getLocation(), event.getChunk()) || inChunk(pair.hitbox.getLocation(), event.getChunk())) {
                close.accept(entry.getKey());
                hide(entry.getKey());
            }
        }
    }

    private boolean inChunk(Location location, Chunk chunk) {
        return location.getWorld().equals(chunk.getWorld()) && location.getBlockX() >> 4 == chunk.getX()
                && location.getBlockZ() >> 4 == chunk.getZ();
    }

    @EventHandler
    public void worldLoad(WorldLoadEvent event) { Bukkit.getScheduler().runTask(plugin, this::reconcileAll); }

    private record Pair(Mannequin visual, Interaction hitbox) {}
}
