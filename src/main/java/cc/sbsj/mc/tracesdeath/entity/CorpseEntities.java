package cc.sbsj.mc.tracesdeath.entity;

import cc.sbsj.mc.tracesdeath.corpse.Corpse;
import cc.sbsj.mc.tracesdeath.language.Messages;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** World objects are views of records. Version-specific visual APIs live in renderers. */
public final class CorpseEntities implements Listener {
    public static final String TAG_PREFIX = "tracesdeath:corpse:";
    private final JavaPlugin plugin;
    private final Map<UUID, Corpse> corpses;
    private final Map<UUID, EntityGroup> visible = new HashMap<>();
    private final Map<UUID, UUID> ids = new HashMap<>();
    private final CorpseRenderer renderer;
    private final BiConsumer<Player, UUID> open;
    private final Consumer<UUID> close;
    private final Method persistent;
    private final Messages messages;

    public CorpseEntities(
            JavaPlugin plugin,
            Map<UUID, Corpse> corpses,
            BiConsumer<Player, UUID> open,
            Consumer<UUID> close,
            CorpseRenderer renderer,
            Messages messages) {
        this.plugin = plugin;
        this.corpses = corpses;
        this.open = open;
        this.close = close;
        this.renderer = renderer;
        this.messages = messages;
        Method method;
        try {
            method = Entity.class.getMethod("setPersistent", boolean.class);
        } catch (NoSuchMethodException ignored) {
            method = null;
        }
        persistent = method;
    }

    public void reconcileAll() {
        for (Corpse corpse : new ArrayList<>(corpses.values())) show(corpse);
    }

    public void cleanupLoadedEntities() {
        for (World world : Bukkit.getWorlds()) cleanup(world.getEntities());
    }

    private void cleanup(Collection<Entity> entities) {
        for (Entity entity : entities) {
            if (!ids.containsKey(entity.getUniqueId()) && id(entity) != null) remove(entity);
        }
    }

    private Location anchor(Corpse corpse, World world) {
        return new Location(world, corpse.x, corpse.y, corpse.z, corpse.yaw, 0);
    }

    private boolean loaded(Location location) {
        return location.getWorld()
                .isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public void show(Corpse corpse) {
        World world = Bukkit.getWorld(corpse.world);
        if (world == null || corpse.empty()) return;
        Location anchor = anchor(corpse, world), model = renderer.visualLocation(anchor);
        if (!loaded(anchor) || !loaded(model)) return;
        EntityGroup current = visible.get(corpse.id);
        if (current != null && current.members.stream().allMatch(Entity::isValid)) {
            renderer.update(current.visual, corpse);
            return;
        }
        hide(corpse.id);
        List<Entity> created = new ArrayList<>();
        Consumer<Entity> configure =
                entity -> {
                    created.add(entity);
                    identity(entity, corpse.id);
                };
        try {
            Entity visual = renderer.spawn(world, model, corpse, configure);
            if (visual.isCustomNameVisible())
                visual.setCustomName(messages.text("entity.name", "name", corpse.name));
            Entity hitbox = renderer.hitbox(world, anchor, visual, configure);
            if (!visual.isValid()
                    || !hitbox.isValid()
                    || created.stream().anyMatch(entity -> !entity.isValid()))
                throw new IllegalStateException("遗体实体生成被取消");
            visible.put(corpse.id, new EntityGroup(visual, created));
        } catch (RuntimeException exception) {
            for (Entity entity : created) remove(entity);
            plugin.getLogger().severe("遗体外观创建失败，物品记录已保留: " + corpse.id + " " + exception);
        }
    }

    private void identity(Entity entity, UUID id) {
        if (persistent != null) {
            try {
                persistent.invoke(entity, false);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("设置实体保存状态失败", exception);
            }
        }
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.addScoreboardTag(TAG_PREFIX + id);
        ids.put(entity.getUniqueId(), id);
    }

    public void hide(UUID id) {
        EntityGroup group = visible.remove(id);
        if (group != null) for (Entity member : group.members) remove(member);
    }

    private void remove(Entity entity) {
        ids.remove(entity.getUniqueId());
        if (entity instanceof LivingEntity && ((LivingEntity) entity).getEquipment() != null) {
            ((LivingEntity) entity).getEquipment().clear();
        }
        entity.remove();
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(visible.keySet())) hide(id);
    }

    private UUID id(Entity entity) {
        UUID known = ids.get(entity.getUniqueId());
        if (known != null) return known;
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                try {
                    return UUID.fromString(tag.substring(TAG_PREFIX.length()));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent) return;
        interactCorpse(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void interactAt(PlayerInteractAtEntityEvent event) {
        interactCorpse(event);
    }

    private void interactCorpse(PlayerInteractEntityEvent event) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void vehicleDamage(VehicleDamageEvent event) {
        if (id(event.getVehicle()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void vehicleDestroy(VehicleDestroyEvent event) {
        if (id(event.getVehicle()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void vehicleEnter(VehicleEnterEvent event) {
        if (id(event.getVehicle()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void portal(EntityPortalEvent event) {
        if (id(event.getEntity()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void vehicleUpdate(VehicleUpdateEvent event) {
        UUID id = id(event.getVehicle());
        Corpse corpse = id == null ? null : corpses.get(id);
        if (corpse == null) return;
        World world = Bukkit.getWorld(corpse.world);
        if (world == null) return;
        Location target = anchor(corpse, world), current = event.getVehicle().getLocation();
        event.getVehicle().setVelocity(new Vector());
        if (!world.equals(current.getWorld()) || current.distanceSquared(target) > .0001)
            event.getVehicle().teleport(target);
    }

    private UUID inventoryId(InventoryHolder holder) {
        return holder instanceof Entity ? id((Entity) holder) : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void moveItems(InventoryMoveItemEvent event) {
        if (inventoryId(event.getSource().getHolder()) != null
                || inventoryId(event.getDestination().getHolder()) != null)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void nativeInventory(InventoryOpenEvent event) {
        UUID id = inventoryId(event.getInventory().getHolder());
        if (id == null) return;
        event.setCancelled(true);
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (player.isOnline()) open.accept(player, id);
                            });
        }
    }

    @EventHandler
    public void load(ChunkLoadEvent event) {
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (!event.getWorld()
                                    .isChunkLoaded(
                                            event.getChunk().getX(), event.getChunk().getZ()))
                                return;
                            cleanup(Arrays.asList(event.getChunk().getEntities()));
                            reconcileAll();
                        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(ChunkUnloadEvent event) {
        for (Map.Entry<UUID, EntityGroup> entry : new ArrayList<>(visible.entrySet())) {
            EntityGroup group = entry.getValue();
            if (group.members.stream()
                    .anyMatch(member -> inChunk(member.getLocation(), event.getChunk()))) {
                close.accept(entry.getKey());
                hide(entry.getKey());
            }
        }
    }

    private boolean inChunk(Location location, Chunk chunk) {
        return location.getWorld().equals(chunk.getWorld())
                && (location.getBlockX() >> 4) == chunk.getX()
                && (location.getBlockZ() >> 4) == chunk.getZ();
    }

    @EventHandler
    public void worldLoad(WorldLoadEvent event) {
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            cleanup(event.getWorld().getEntities());
                            reconcileAll();
                        });
    }

    private static final class EntityGroup {
        final Entity visual;
        final List<Entity> members;

        EntityGroup(Entity visual, List<Entity> members) {
            this.visual = visual;
            this.members =
                    Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(members)));
        }
    }
}
