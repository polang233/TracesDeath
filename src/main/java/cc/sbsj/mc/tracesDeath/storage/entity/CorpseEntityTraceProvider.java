package cc.sbsj.mc.tracesDeath.storage.entity;

import cc.sbsj.mc.tracesDeath.storage.PlacementResult;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageProvider;
import cc.sbsj.mc.tracesDeath.trace.TraceContext;
import cc.sbsj.mc.tracesDeath.trace.TraceEntityInventoryService;
import cc.sbsj.mc.tracesDeath.trace.TraceKeys;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;

/**
 * 尸体实体痕迹提供者
 * <p>
 * 使用玩家头颅盔甲架作为死亡痕迹容器，提供虚拟背包界面。
 */
public final class CorpseEntityTraceProvider implements TraceStorageProvider {
    private final TraceKeys keys;
    private final TraceEntityInventoryService inventories;

    public CorpseEntityTraceProvider(TraceKeys keys, TraceEntityInventoryService inventories) {
        this.keys = keys;
        this.inventories = inventories;
    }

    @Override
    public String id() {
        return "corpse";
    }
    
    @Override
    public @NotNull String displayName() {
        return "尸体实体";
    }

    @Override
    public boolean supports(TraceContext context) {
        String storageType = context.config().storageType();
        return storageType.equals(id()) || storageType.equals("player") || storageType.equals("entity");
    }

    @Override
    public PlacementResult place(TraceContext context) {
        Location location = context.location().clone().add(0.5, 0.0, 0.5);
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class, armorStand -> configure(armorStand, context));
        inventories.writeItems(stand, context.traceId(), asSizedArray(context.drops()));
        dropOverflow(context, location);
        return PlacementResult.success(context.lang().text("storage.corpse.created"));
    }
    
    @Override
    public boolean cleanup(@NotNull Location location, @NotNull UUID traceId) {
        // 在附近查找匹配的盔甲架实体
        for (Entity entity : location.getWorld().getNearbyEntities(location, 2, 2, 2)) {
            if (entity instanceof ArmorStand stand) {
                String storedId = stand.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
                if (traceId.toString().equals(storedId)) {
                    // 移除实体（物品已在虚拟库存中处理）
                    entity.remove();
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean isValid(@NotNull Location location, @NotNull UUID traceId) {
        for (Entity entity : location.getWorld().getNearbyEntities(location, 2, 2, 2)) {
            if (entity instanceof ArmorStand stand) {
                String storedId = stand.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
                if (traceId.toString().equals(storedId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void configure(ArmorStand stand, TraceContext context) {
        stand.customName(context.lang().component("storage.display-name", Map.of("player", context.player().getName())));
        stand.setCustomNameVisible(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCanPickupItems(false);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setSmall(false);
        stand.setMarker(false);
        stand.setBodyPose(new EulerAngle(Math.toRadians(90), 0, 0));
        stand.setHeadPose(new EulerAngle(Math.toRadians(90), 0, 0));
        stand.getPersistentDataContainer().set(keys.traceId(), PersistentDataType.STRING, context.traceId().toString());
        stand.getPersistentDataContainer().set(keys.traceType(), PersistentDataType.STRING, id());
        stand.getPersistentDataContainer().set(keys.owner(), PersistentDataType.STRING, context.player().getUniqueId().toString());
        stand.setItem(EquipmentSlot.HEAD, playerHead(context));
    }

    private ItemStack playerHead(TraceContext context) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(context.player());
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private ItemStack[] asSizedArray(List<ItemStack> items) {
        ItemStack[] contents = new ItemStack[54];
        for (int i = 0; i < Math.min(contents.length, items.size()); i++) {
            contents[i] = items.get(i).clone();
        }
        return contents;
    }

    private void dropOverflow(TraceContext context, Location location) {
        for (int i = 54; i < context.drops().size(); i++) {
            location.getWorld().dropItemNaturally(location, context.drops().get(i).clone());
        }
    }
}
