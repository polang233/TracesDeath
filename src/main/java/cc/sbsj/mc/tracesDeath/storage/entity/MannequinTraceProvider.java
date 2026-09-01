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
 * <p>
 * Mannequin 不是 Mob：装备槽可以放物品，但掉落概率 API 会直接抛异常，
 * 因此视觉装备的防掉落只能依赖无敌状态、死亡事件清空掉落和删除前清空装备。
 */
public final class MannequinTraceProvider implements TraceStorageProvider {
    public static final String VISUAL_ENTITY_ID = "visual-entity-id";
    public static final String INTERACTION_ENTITY_ID = "interaction-entity-id";
    public static final String VISUAL_YAW = "visual-yaw";
    public static final String ROLE_VISUAL = "visual";
    public static final String ROLE_INTERACTION = "interaction";

    /**
     * 睡姿模型以实体坐标为脚端、沿朝向侧面延伸约 1.8 格；生成点向反向回撤半个身长，
     * 让尸体视觉上居中在方块内。
     */
    private static final double VISUAL_BODY_LENGTH = 1.8;

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
        Location anchor = context.location().clone().add(0.5, 0.0, 0.5);
        float yaw = cardinalYaw(context.location().getYaw());
        Mannequin mannequin = null;
        Interaction interaction = null;
        try {
            mannequin = spawnVisual(anchor, yaw, context);
            interaction = spawnInteraction(anchor, context.traceId(), context.player().getUniqueId());
            Map<String, String> storageData = new LinkedHashMap<>();
            storageData.put(VISUAL_ENTITY_ID, mannequin.getUniqueId().toString());
            storageData.put(INTERACTION_ENTITY_ID, interaction.getUniqueId().toString());
            storageData.put(VISUAL_YAW, Float.toString(yaw));
            return PlacementResult.success(
                    context.lang().text("storage.mannequin.created"),
                    anchor,
                    storageData
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
        Location visualLocation = visualLocation(data);
        if (visualLocation != null && !visualLocation.getChunk().isLoaded()) {
            visualLocation.getChunk().load();
        }

        data.storageUuid(VISUAL_ENTITY_ID)
                .map(world::getEntity)
                .filter(entity -> isManagedEntity(entity, data.traceId()))
                .ifPresent(this::removeManagedEntity);
        data.storageUuid(INTERACTION_ENTITY_ID)
                .map(world::getEntity)
                .filter(entity -> isManagedEntity(entity, data.traceId()))
                .ifPresent(this::removeManagedEntity);

        removeManagedEntitiesInChunk(chunk, data.traceId());
        if (visualLocation != null) {
            removeManagedEntitiesInChunk(visualLocation.getChunk(), data.traceId());
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
        if (findInteraction(data, chunk) == null) {
            return false;
        }
        if (findVisual(data) != null) {
            return true;
        }

        Location visualLocation = visualLocation(data);
        if (visualLocation == null || sameChunk(visualLocation, location)) {
            return false;
        }
        Chunk visualChunk = visualLocation.getChunk();
        if (!visualChunk.isEntitiesLoaded()) {
            return true;
        }
        return findVisualInChunk(data, visualChunk) != null;
    }

    @Override
    public @NotNull Map<String, String> reconcile(@NotNull TraceData data) {
        Location location = data.location();
        Chunk chunk = location.getWorld().getChunkAt(location);
        if (!chunk.isEntitiesLoaded()) {
            return data.storageData();
        }

        Location visualLocation = visualLocation(data);
        Chunk visualChunk = visualLocation == null || sameChunk(visualLocation, location)
                ? chunk : visualLocation.getWorld().getChunkAt(visualLocation);
        if (!visualChunk.isEntitiesLoaded()) {
            visualChunk.load();
        }

        Mannequin visual = findVisual(data);
        Interaction interaction = findInteraction(data, chunk);

        if (visual == null) {
            visual = spawnVisual(location, visualYaw(data), data);
        } else {
            configureIdentity(visual, data.traceId(), data.playerId(), ROLE_VISUAL);
        }
        if (interaction == null) {
            interaction = spawnInteraction(location, data.traceId(), data.playerId());
        } else {
            configureInteraction(interaction, data.traceId(), data.playerId());
        }

        removeDuplicates(chunk, data.traceId(), visual.getUniqueId(), interaction.getUniqueId());
        if (visualChunk != chunk) {
            removeDuplicates(visualChunk, data.traceId(), visual.getUniqueId(), interaction.getUniqueId());
        }

        Map<String, String> result = new LinkedHashMap<>(data.storageData());
        result.put(VISUAL_ENTITY_ID, visual.getUniqueId().toString());
        result.put(INTERACTION_ENTITY_ID, interaction.getUniqueId().toString());
        result.putIfAbsent(VISUAL_YAW, Float.toString(visualYaw(data)));
        return result;
    }

    private Mannequin spawnVisual(Location anchor, float yaw, TraceContext context) {
        Mannequin mannequin = spawnVisualEntity(anchor, yaw);
        try {
            Component name = context.lang().component(
                    "storage.display-name", Map.of("player", context.player().getName()));
            configureVisual(
                    mannequin,
                    name,
                    ResolvableProfile.resolvableProfile(context.player().getPlayerProfile()),
                    context.traceId(),
                    context.player().getUniqueId()
            );
            if (context.player() instanceof Player online) {
                displayEquipment(mannequin, online);
            }
            return mannequin;
        } catch (RuntimeException exception) {
            discardVisual(mannequin);
            throw exception;
        }
    }

    private Mannequin spawnVisual(Location anchor, float yaw, TraceData data) {
        Mannequin mannequin = spawnVisualEntity(anchor, yaw);
        try {
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
        } catch (RuntimeException exception) {
            discardVisual(mannequin);
            throw exception;
        }
    }

    private Mannequin spawnVisualEntity(Location anchor, float yaw) {
        Location spawnLocation = visualLocation(anchor, yaw);
        Entity spawned = anchor.getWorld().spawnEntity(spawnLocation, EntityType.MANNEQUIN);
        if (!(spawned instanceof Mannequin mannequin)) {
            spawned.remove();
            throw new IllegalStateException("服务端未返回 Mannequin 实体");
        }
        return mannequin;
    }

    private void configureVisual(Mannequin mannequin, Component name, ResolvableProfile profile,
                                 UUID traceId, UUID ownerId) {
        mannequin.customName(name);
        mannequin.setCustomNameVisible(true);
        // Mannequin 的 description 渲染在名字下方（默认还带 "NPC" 文本）；置空即整体隐藏，避免出现第二行。
        mannequin.setDescription(null);
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
    }

    private Interaction spawnInteraction(Location location, UUID traceId, UUID ownerId) {
        Entity spawned = location.getWorld().spawnEntity(location, EntityType.INTERACTION);
        if (!(spawned instanceof Interaction interaction)) {
            spawned.remove();
            throw new IllegalStateException("服务端未返回 Interaction 实体");
        }
        try {
            configureInteraction(interaction, traceId, ownerId);
            return interaction;
        } catch (RuntimeException exception) {
            if (interaction.isValid()) {
                interaction.remove();
            }
            throw exception;
        }
    }

    private void configureInteraction(Interaction interaction, UUID traceId, UUID ownerId) {
        interaction.setInteractionWidth((float) plugin.traceConfig().mannequin().interactionWidth());
        interaction.setInteractionHeight((float) plugin.traceConfig().mannequin().interactionHeight());
        interaction.setResponsive(true);
        interaction.setPersistent(true);
        interaction.setGravity(false);
        interaction.setNoPhysics(true);
        interaction.setInvulnerable(true);
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
    }

    private Mannequin findVisual(TraceData data) {
        Entity byId = data.storageUuid(VISUAL_ENTITY_ID)
                .map(data.location().getWorld()::getEntity)
                .orElse(null);
        if (byId instanceof Mannequin mannequin && isManagedEntity(mannequin, data.traceId())) {
            return mannequin;
        }
        return findVisualInChunk(data, data.location().getChunk());
    }

    private Mannequin findVisualInChunk(TraceData data, Chunk chunk) {
        if (!chunk.isEntitiesLoaded()) {
            return null;
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Mannequin mannequin && isManagedEntity(mannequin, data.traceId())) {
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
        if (!chunk.isEntitiesLoaded()) {
            return null;
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Interaction interaction && isManagedEntity(interaction, data.traceId())) {
                return interaction;
            }
        }
        return null;
    }

    private void removeDuplicates(Chunk chunk, UUID traceId, UUID visualId, UUID interactionId) {
        if (!chunk.isEntitiesLoaded()) {
            return;
        }
        for (Entity entity : chunk.getEntities()) {
            if (!isManagedEntity(entity, traceId)) {
                continue;
            }
            if (!entity.getUniqueId().equals(visualId) && !entity.getUniqueId().equals(interactionId)) {
                removeManagedEntity(entity);
            }
        }
    }

    private void removeManagedEntitiesInChunk(Chunk chunk, UUID traceId) {
        if (chunk.isEntitiesLoaded()) {
            for (Entity entity : chunk.getEntities()) {
                if (isManagedEntity(entity, traceId)) {
                    removeManagedEntity(entity);
                }
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

    private void discardVisual(Mannequin mannequin) {
        if (mannequin == null) {
            return;
        }
        mannequin.getEquipment().clear();
        if (mannequin.isValid()) {
            mannequin.remove();
        }
    }

    /**
     * 尸体躺卧朝向：把死亡朝向取整到直角方向，保证尸体沿方块轴整齐摆放。
     */
    private static float cardinalYaw(float yaw) {
        float normalized = ((yaw % 360.0f) + 360.0f) % 360.0f;
        return Math.round(normalized / 90.0f) * 90.0f % 360.0f;
    }

    private float visualYaw(TraceData data) {
        String value = data.storageData().get(VISUAL_YAW);
        if (value != null) {
            try {
                return cardinalYaw(Float.parseFloat(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return cardinalYaw(data.location().getYaw());
    }

    /**
     * 睡姿模型的实际生成点：方块中心沿身体延伸反方向回撤半个身长。
     * 原版渲染中，睡姿实体以自身坐标为脚端，身体向 (−cos yaw, 0, sin yaw) 方向延伸。
     */
    private Location visualLocation(Location anchor, float yaw) {
        double radians = Math.toRadians(yaw);
        double offsetX = Math.cos(radians) * (VISUAL_BODY_LENGTH / 2.0);
        double offsetZ = -Math.sin(radians) * (VISUAL_BODY_LENGTH / 2.0);
        Location location = anchor.clone().add(offsetX, 0.0, offsetZ);
        location.setYaw(yaw);
        return location;
    }

    @Nullable
    private Location visualLocation(TraceData data) {
        return visualLocation(data.location(), visualYaw(data));
    }

    private static boolean sameChunk(Location first, Location second) {
        return (first.getBlockX() >> 4) == (second.getBlockX() >> 4)
                && (first.getBlockZ() >> 4) == (second.getBlockZ() >> 4);
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
