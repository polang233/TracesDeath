package cc.sbsj.mc.tracesdeath.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** Validated gameplay settings; parsed once during startup. */
public final class PluginSettings {
    public enum ClaimMode {
        RESTORE_SLOTS,
        FILL_INVENTORY
    }

    private final boolean ownerOnly;
    private final ClaimMode claimMode;

    private PluginSettings(boolean ownerOnly, ClaimMode claimMode) {
        this.ownerOnly = ownerOnly;
        this.claimMode = claimMode;
    }

    public static PluginSettings read(ConfigurationSection config) {
        String value = config.getString("loot.claim-all-mode", "restore_slots");
        try {
            return new PluginSettings(
                    config.getBoolean("loot.owner-only", false),
                    ClaimMode.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "loot.claim-all-mode 必须为 restore_slots 或 fill_inventory", exception);
        }
    }

    public boolean isOwnerOnly() {
        return ownerOnly;
    }

    public boolean isFillInventory() {
        return claimMode == ClaimMode.FILL_INVENTORY;
    }
}
