package cc.sbsj.mc.tracesDeath.trace;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * PDC 键名常量，用于在方块/实体上存储墓碑元数据
 */
public final class TraceKeys {
    private final NamespacedKey traceId;
    private final NamespacedKey traceType;
    private final NamespacedKey owner;
    private final NamespacedKey entityRole;

    public TraceKeys(Plugin plugin) {
        this.traceId = new NamespacedKey(plugin, "trace_id");
        this.traceType = new NamespacedKey(plugin, "trace_type");
        this.owner = new NamespacedKey(plugin, "owner");
        this.entityRole = new NamespacedKey(plugin, "entity_role");
    }

    public NamespacedKey traceId() {
        return traceId;
    }

    public NamespacedKey traceType() {
        return traceType;
    }

    public NamespacedKey owner() {
        return owner;
    }
    
    public NamespacedKey entityRole() {
        return entityRole;
    }
}
