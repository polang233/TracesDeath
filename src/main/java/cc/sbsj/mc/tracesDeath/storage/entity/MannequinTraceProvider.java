package cc.sbsj.mc.tracesDeath.storage.entity;

import cc.sbsj.mc.tracesDeath.TracesDeath;
import cc.sbsj.mc.tracesDeath.storage.PlacementResult;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageProvider;
import cc.sbsj.mc.tracesDeath.trace.TraceContext;
import cc.sbsj.mc.tracesDeath.trace.TraceData;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import com.destroystokyo.paper.SkinParts;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 使用高版本 Mannequin 作为墓碑外观，并使用 Interaction 实体提供稳定点击区域。
 */
public final class MannequinTraceProvider implements TraceStorageProvider {
    public static final String VISUAL_ENTITY_ID = "visual-entity-id";
    public static final String INTERACTION_ENTITY_ID = "interaction-entity-id";
    public static final String ROLE_VISUAL = "visual";
    public static final String ROLE_INTERACTION = "interaction";

    private final TracesDeath plugin;
    private final TraceKeys keys;

    public MannequinTraceProvider(TracesDeath plugin, TraceKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    @Override
    public String id() {
        return "mannequin";
    }

    @Override
    public @NotNull String displayName() {
        return "玩家模型 NPC";
    }

    @Override
    public boolean supports(TraceContext context) {
        return id().equals(context.config().storageType());
    }

    @Override
    public PlacementResult place(TraceContext context) {
        Location location = context.location().clone().add(0.5, 0.0, 0.5);
        Mannequin mannequin = null;
        Interaction interaction = null;
        try {
            mannequin = spawnVisual(location, context);
            interaction = spawnInteraction(location, context.traceId(), context.player().getUniqueId());
            return PlacementResult.success(
                    context.lang().text("storage.mannequin.created"),
                    mannequin.getLocation(),
                    Map.of(
                            VISUAL_ENTITY_ID, mannequin.getUniqueId().toString(),
                            INTERACTION_ENTITY_ID, interaction.getUniqueId().toString()
                    )
            );
        } catch (RuntimeException exception) {
            removeManagedEntity(interaction);
            removeManagedEntity(mannequin);
            plugin.getLogger().severe("生成 Mannequin 墓碑失败: " + exception.getMessage());
            return PlacementResult.failure(context.lang().text("storage.mannequin.spawn-failed"));
        }
    }

    @Override
    public boolean cleanup(@NotNull TraceData data) {
        Location location = data.location();
        World world = location.getWorld();
        Chunk chunk = world.getChunkAt(location);
        if (!chunk.isLoaded()) {
            chunk.load();
        }

        data.storageUuid(VISUAL_ENTITY_ID)
                .map(world::getEntity)
                .filter(entity -> isManagedEntity(entity, data.traceId()))
                .ifPresent(this::removeManagedEntity);
        data.storageUuid(INTERACTION_ENTITY_ID)
                .map(world::getEntity)
                .filter(entity -> isManagedEntity(entity, data.traceId()))
                .ifPresent(this::removeManagedEntity);

        if (chunk.isEntitiesLoaded()) {
            for (Entity entity : chunk.getEntities()) {
                if (isManagedEntity(entity, data.traceId())) {
                    removeManagedEntity(entity);
                }
            }
        }
        return true;
    }

    @Override
    public boolean isValid(@NotNull TraceData data) {
        Location location = data.location();
        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return true;
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        if (!chunk.isEntitiesLoaded()) {
            return true;
        }
        return findVisual(data, chunk) != null && findInteraction(data, chunk) != null;
    }

    @Override
    public @NotNull Map<String, String> reconcile(@NotNull TraceData data) {
        Location location = data.location();
        Chunk chunk = location.getWorld().getChunkAt(location);
        if (!chunk.isEntitiesLoaded()) {
            return data.storageData();
        }

        Mannequin visual = findVisual(data, chunk);
        Interaction interaction = findInteraction(data, chunk);

        if (visual == null) {
            visual = spawnVisual(location, data);
        } else {
            configureIdentity(visual, data.traceId(), data.playerId(), ROLE_VISUAL);
        }
        if (interaction == null) {
            interaction = spawnInteraction(location, data.traceId(), data.playerId());
        } else {
            configureInteraction(interaction, data.traceId(), data.playerId());
        }

        removeDuplicates(chunk, data.traceId(), visual.getUniqueId(), interaction.getUniqueId());

        Map<String, String> result = new LinkedHashMap<>(data.storageData());
        result.put(VISUAL_ENTITY_ID, visual.getUniqueId().toString());
        result.put(INTERACTION_ENTITY_ID, interaction.getUniqueId().toString());
        return result;
    }

    private Mannequin spawnVisual(Location location, TraceContext context) {
        Entity spawned = location.getWorld().spawnEntity(location, EntityType.MANNEQUIN);
        if (!(spawned instanceof Mannequin mannequin)) {
            spawned.remove();
            throw new IllegalStateException("服务端未返回 Mannequin 实体");
        }

        Component name = context.lang().component(
                "storage.display-name", Map.of("player", context.player().getName()));
        configureVisual(
                mannequin,
                name,
                ResolvableProfile.resolvableProfile(context.player().getPlayerProfile()),
                context.traceId(),
                context.player().getUniqueId()
        );
        displayEquipment(mannequin, context.player());
        return mannequin;
    }

    private Mannequin spawnVisual(Location location, TraceData data) {
        Entity spawned = location.getWorld().spawnEntity(location, EntityType.MANNEQUIN);
        if (!(spawned instanceof Mannequin mannequin)) {
            spawned.remove();
            throw new IllegalStateException("服务端未返回 Mannequin 实体");
        }

        Component name = plugin.lang().component(
                "storage.display-name", Map.of("player", data.playerName()));
        configureVisual(
                mannequin,
                name,
                ResolvableProfile.resolvableProfile(Bukkit.getOfflinePlayer(data.playerId()).getPlayerProfile()),
                data.traceId(),
                data.playerId()
        );
        displayEquipment(mannequin, data.items());
        return mannequin;
    }

    private void configureVisual(Mannequin mannequin, Component name, ResolvableProfile profile,
                                 UUID traceId, UUID ownerId) {
        mannequin.customName(name);
        mannequin.setCustomNameVisible(true);
        mannequin.setDescription(name);
        mannequin.setProfile(profile);
        mannequin.setSkinParts(SkinParts.allParts());
        mannequin.setImmovable(true);
        mannequin.setAI(false);
        mannequin.setGravity(false);
        mannequin.setNoPhysics(true);
        mannequin.setInvulnerable(true);
        mannequin.setSilent(true);
        mannequin.setCollidable(false);
        mannequin.setPersistent(true);
        mannequin.setRemoveWhenFarAway(false);
        mannequin.setPose(Pose.SLEEPING, true);
        configureIdentity(mannequin, traceId, ownerId, ROLE_VISUAL);
        setNoDropChances(mannequin.getEquipment());
    }

    private Interaction spawnInteraction(Location location, UUID traceId, UUID ownerId) {
        Entity spawned = location.getWorld().spawnEntity(location, EntityType.INTERACTION);
        if (!(spawned instanceof Interaction interaction)) {
            spawned.remove();
            throw new IllegalStateException("服务端未返回 Interaction 实体");
        }
        configureInteraction(interaction, traceId, ownerId);
        return interaction;
    }

    private void configureInteraction(Interaction interaction, UUID traceId, UUID ownerId) {
        interaction.setInteractionWidth((float) plugin.traceConfig().mannequin().interactionWidth());
        interaction.setInteractionHeight((float) plugin.traceConfig().mannequin().interactionHeight());
        interaction.setResponsive(true);
        interaction.setPersistent(true);
        interaction.setGravity(false);
        configureIdentity(interaction, traceId, ownerId, ROLE_INTERACTION);
    }

    private void configureIdentity(Entity entity, UUID traceId, UUID ownerId, String role) {
        entity.getPersistentDataContainer().set(
                keys.traceId(), PersistentDataType.STRING, traceId.toString());
        entity.getPersistentDataContainer().set(
                keys.traceType(), PersistentDataType.STRING, id());
        entity.getPersistentDataContainer().set(
                keys.owner(), PersistentDataType.STRING, ownerId.toString());
        entity.getPersistentDataContainer().set(
                keys.entityRole(), PersistentDataType.STRING, role);
    }

    private void displayEquipment(Mannequin mannequin, Player player) {
        EntityEquipment equipment = mannequin.getEquipment();
        equipment.setHelmet(cloneOrNull(player.getInventory().getHelmet()));
        equipment.setChestplate(cloneOrNull(player.getInventory().getChestplate()));
        equipment.setLeggings(cloneOrNull(player.getInventory().getLeggings()));
        equipment.setBoots(cloneOrNull(player.getInventory().getBoots()));
        equipment.setItemInMainHand(player.getInventory().getItemInMainHand().clone());
        equipment.setItemInOffHand(player.getInventory().getItemInOffHand().clone());
        setNoDropChances(equipment);
    }

    private void displayEquipment(Mannequin mannequin, List<ItemStack> items) {
        EntityEquipment equipment = mannequin.getEquipment();
        ItemStack mainHand = null;
        ItemStack offHand = null;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Material type = item.getType();
            if (type.name().endsWith("_HELMET") && equipment.getHelmet() == null) {
                equipment.setHelmet(item.clone());
            } else if ((type.name().endsWith("_CHESTPLATE") || type == Material.ELYTRA)
                    && equipment.getChestplate() == null) {
                equipment.setChestplate(item.clone());
            } else if (type.name().endsWith("_LEGGINGS") && equipment.getLeggings() == null) {
                equipment.setLeggings(item.clone());
            } else if (type.name().endsWith("_BOOTS") && equipment.getBoots() == null) {
                equipment.setBoots(item.clone());
            } else if (type == Material.SHIELD && offHand == null) {
                offHand = item.clone();
            } else if (mainHand == null && isHandDisplayItem(type)) {
                mainHand = item.clone();
            }
        }
        equipment.setItemInMainHand(mainHand);
        equipment.setItemInOffHand(offHand);
        setNoDropChances(equipment);
    }

    private Mannequin findVisual(TraceData data, Chunk chunk) {
        Entity byId = data.storageUuid(VISUAL_ENTITY_ID)
                .map(data.location().getWorld()::getEntity)
                .orElse(null);
        if (byId instanceof Mannequin mannequin && isManagedEntity(mannequin, data.traceId())) {
            return mannequin;
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Mannequin mannequin && isManagedEntity(entity, data.traceId())) {
                return mannequin;
            }
        }
        return null;
    }

    private Interaction findInteraction(TraceData data, Chunk chunk) {
        Entity byId = data.storageUuid(INTERACTION_ENTITY_ID)
                .map(data.location().getWorld()::getEntity)
                .orElse(null);
        if (byId instanceof Interaction interaction && isManagedEntity(interaction, data.traceId())) {
            return interaction;
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Interaction interaction && isManagedEntity(entity, data.traceId())) {
                return interaction;
            }
        }
        return null;
    }

    private void removeDuplicates(Chunk chunk, UUID traceId, UUID visualId, UUID interactionId) {
        for (Entity entity : chunk.getEntities()) {
            if (!isManagedEntity(entity, traceId)) {
                continue;
            }
            if (!entity.getUniqueId().equals(visualId) && !entity.getUniqueId().equals(interactionId)) {
                removeManagedEntity(entity);
            }
        }
    }

    private boolean isManagedEntity(@Nullable Entity entity, UUID traceId) {
        if (entity == null) {
            return false;
        }
        String type = entity.getPersistentDataContainer().get(
                keys.traceType(), PersistentDataType.STRING);
        String storedId = entity.getPersistentDataContainer().get(
                keys.traceId(), PersistentDataType.STRING);
        return id().equals(type) && traceId.toString().equals(storedId);
    }

    private void removeManagedEntity(@Nullable Entity entity) {
        if (entity == null) {
            return;
        }
        if (entity instanceof Mannequin mannequin) {
            mannequin.getEquipment().clear();
        }
        if (entity.isValid()) {
            entity.remove();
        }
    }

    private void setNoDropChances(EntityEquipment equipment) {
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
    }

    private boolean isHandDisplayItem(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || type == Material.BOW
                || type == Material.CROSSBOW
                || type == Material.TRIDENT;
    }

    private ItemStack cloneOrNull(@Nullable ItemStack item) {
        return item == null ? null : item.clone();
    }
}
