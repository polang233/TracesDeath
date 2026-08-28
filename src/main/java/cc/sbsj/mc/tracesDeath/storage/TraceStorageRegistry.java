package cc.sbsj.mc.tracesDeath.storage;

import cc.sbsj.mc.tracesDeath.trace.TraceContext;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 存储提供者注册表，管理所有墓碑存储类型
 */
public final class TraceStorageRegistry {
    private final Map<String, TraceStorageProvider> providers = new LinkedHashMap<>();

    public void register(TraceStorageProvider provider) {
        providers.put(provider.id(), provider);
    }

    public Optional<TraceStorageProvider> find(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    public Collection<TraceStorageProvider> providers() {
        return providers.values();
    }

    // 根据配置选择对应的存储提供者并放置墓碑
    public PlacementResult place(TraceContext context) {
        TraceStorageProvider provider = providers.get(context.config().storageType());
        if (provider == null || !provider.supports(context)) {
            return PlacementResult.failure(context.lang().text("storage.unsupported-type", Map.of("type", context.config().storageType())));
        }
        return provider.place(context);
    }
}
