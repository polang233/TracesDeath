package cc.sbsj.mc.tracesDeath.trace;

import cc.sbsj.mc.tracesDeath.config.Lang;
import cc.sbsj.mc.tracesDeath.storage.entity.TraceInventoryHolder;
import cc.sbsj.mc.tracesDeath.util.InventoryUtil;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 尸体实体的虚拟背包服务，处理物品存储和交互
 */
public final class TraceEntityInventoryService implements Listener {
    private static final int INVENTORY_SIZE = 54;

    private final TraceKeys keys;
    private final Lang lang;

    public TraceEntityInventoryService(TraceKeys keys, Lang lang) {
        this.keys = keys;
        this.lang = lang;
    }

    public void writeItems(Entity entity, UUID traceId, ItemStack[] items) {
        byte[] bytes = ItemStack.serializeItemsAsBytes(items);
        entity.getPersistentDataContainer().set(keys.traceId(), PersistentDataType.STRING, traceId.toString());
        entity.getPersistentDataContainer().set(keys.traceType(), PersistentDataType.STRING, "corpse");
        entity.getPersistentDataContainer().set(itemKey(), PersistentDataType.STRING, Base64.getEncoder().encodeToString(bytes));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isCorpse(entity)) {
            return;
        }

        event.setCancelled(true);
        Inventory inventory = createInventory(entity);
        event.getPlayer().openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (isCorpse(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TraceInventoryHolder holder)) {
            return;
        }
        Entity entity = findEntity(holder.entityId());
        if (entity == null) {
            return;
        }
        if (InventoryUtil.isEmpty(event.getInventory())) {
            entity.remove();
            return;
        }
        writeItems(entity, readTraceId(entity), event.getInventory().getContents());
    }

    private Inventory createInventory(Entity entity) {
        Component title = entity.customName() == null ? lang.component(lang.text("storage.inventory-title")) : entity.customName();
        Inventory inventory = Bukkit.createInventory(new TraceInventoryHolder(entity.getUniqueId()), INVENTORY_SIZE, title);
        ItemStack[] stored = readItems(entity);
        inventory.setContents(Arrays.copyOf(stored, INVENTORY_SIZE));
        return inventory;
    }

    private ItemStack[] readItems(Entity entity) {
        String encoded = entity.getPersistentDataContainer().get(itemKey(), PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) {
            return new ItemStack[INVENTORY_SIZE];
        }
        try {
            return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException ignored) {
            return new ItemStack[INVENTORY_SIZE];
        }
    }

    private boolean isCorpse(Entity entity) {
        String type = entity.getPersistentDataContainer().get(keys.traceType(), PersistentDataType.STRING);
        return "corpse".equals(type);
    }

    private UUID readTraceId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(keys.traceId(), PersistentDataType.STRING);
        if (value != null) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return entity.getUniqueId();
    }

    private Entity findEntity(UUID entityId) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private org.bukkit.NamespacedKey itemKey() {
        return new org.bukkit.NamespacedKey(keys.traceId().getNamespace(), "trace_items");
    }
}
