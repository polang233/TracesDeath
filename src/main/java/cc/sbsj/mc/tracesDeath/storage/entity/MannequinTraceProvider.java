package cc.sbsj.mc.tracesDeath.storage.entity;

import cc.sbsj.mc.tracesDeath.storage.PlacementResult;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageProvider;
import cc.sbsj.mc.tracesDeath.trace.TraceContext;
import cc.sbsj.mc.tracesDeath.trace.TraceData;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import com.destroystokyo.paper.SkinParts;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * 使用 1.21.9+ Mannequin 实体展示玩家皮肤的死亡痕迹。
 */
public final class MannequinTraceProvider implements TraceStorageProvider {
    public static final String ENTITY_ID = "entity-id";

    private final TraceKeys keys;

    public MannequinTraceProvider(TraceKeys keys) {
        this.keys = keys;
    }

    @Override
    public String id() {
        return "mannequin";
    }

    @Override
    public @NotNull String displayName() {
        return "玩家模型";
    }

    @Override
    public boolean supports(TraceContext context) {
        return "mannequin".equals(context.config().storageType());
    }

    @Override
    public PlacementResult place(TraceContext context) {
        Location location = context.location().clone().add(0.5, 0.0, 0.5);
        Entity entity = location.getWorld().spawnEntity(location, EntityType.MANNEQUIN);
        if (!(entity instanceof Mannequin mannequin)) {
            entity.remove();
            return PlacementResult.failure(context.lang().text("storage.mannequin.spawn-failed"));
        }

        configure(mannequin, context);
        return PlacementResult.success(
                context.lang().text("storage.mannequin.created"),
                mannequin.getLocation(),
                Map.of(ENTITY_ID, mannequin.getUniqueId().toString())
        );
    }

    @Override
    public boolean cleanup(@NotNull TraceData data) {
        boolean removed = false;
        Entity stored = data.storageUuid(ENTITY_ID)
                .map(data.location().getWorld()::getEntity)
                .orElse(null);
        if (isTraceMannequin(stored, data.traceId())) {
            stored.remove();
            removed = true;
        }
        for (Entity entity : data.location().getWorld().getNearbyEntities(data.location(), 2, 2, 2)) {
            if (isTraceMannequin(entity, data.traceId())) {
                entity.remove();
                removed = true;
            }
        }
        return removed || stored == null;
    }

    @Override
    public boolean isValid(@NotNull TraceData data) {
        Entity stored = data.storageUuid(ENTITY_ID)
                .map(data.location().getWorld()::getEntity)
                .orElse(null);
        if (isTraceMannequin(stored, data.traceId())) {
            return true;
        }
        for (Entity entity : data.location().getWorld().getNearbyEntities(data.location(), 2, 2, 2)) {
            if (isTraceMannequin(entity, data.traceId())) {
                return true;
            }
        }
        return false;
    }

    private void configure(Mannequin mannequin, TraceContext context) {
        var name = context.lang().component("storage.display-name", Map.of("player", context.player().getName()));
        mannequin.customName(name);
        mannequin.setCustomNameVisible(true);
        mannequin.setDescription(name);
        mannequin.setProfile(ResolvableProfile.resolvableProfile(context.player().getPlayerProfile()));
        mannequin.setSkinParts(SkinParts.allParts());
        mannequin.setImmovable(true);
        mannequin.setAI(false);
        mannequin.setGravity(false);
        mannequin.setInvulnerable(true);
        mannequin.setSilent(true);
        mannequin.setCollidable(false);
        mannequin.setRemoveWhenFarAway(false);
        mannequin.setPose(Pose.SLEEPING);
        mannequin.setRotation(context.player().getLocation().getYaw(), 0.0f);

        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(context.player().getInventory().getHelmet());
            equipment.setChestplate(context.player().getInventory().getChestplate());
            equipment.setLeggings(context.player().getInventory().getLeggings());
            equipment.setBoots(context.player().getInventory().getBoots());
            equipment.setItemInMainHand(context.player().getInventory().getItemInMainHand());
            equipment.setItemInOffHand(context.player().getInventory().getItemInOffHand());
        }

        mannequin.getPersistentDataContainer().set(keys.traceId(), PersistentDataType.STRING, context.traceId().toString());
        mannequin.getPersistentDataContainer().set(keys.traceType(), PersistentDataType.STRING, id());
        mannequin.getPersistentDataContainer().set(keys.owner(), PersistentDataType.STRING, context.player().getUniqueId().toString());
    }

    private boolean isTraceMannequin(Entity entity, UUID traceId) {
        if (!(entity instanceof Mannequin)) {
            return false;
        }
        String storedId = entity.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
        return traceId.toString().equals(storedId);
    }
}
