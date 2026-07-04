package cc.sbsj.mc.tracesDeath.trace;

import cc.sbsj.mc.tracesDeath.config.TraceConfig;
import cc.sbsj.mc.tracesDeath.config.Lang;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 墓碑创建上下文，封装放置时所需的全部信息
 */
public record TraceContext(
        UUID traceId,
        Player player,
        Location location,
        List<ItemStack> drops,
        TraceConfig config,
        Lang lang
) {
}
