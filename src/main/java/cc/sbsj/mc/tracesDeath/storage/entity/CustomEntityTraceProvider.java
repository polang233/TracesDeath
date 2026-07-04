package cc.sbsj.mc.tracesDeath.storage.entity;

import cc.sbsj.mc.tracesDeath.storage.PlacementResult;
import cc.sbsj.mc.tracesDeath.storage.TraceStorageProvider;
import cc.sbsj.mc.tracesDeath.trace.TraceContext;

/**
 * 自定义实体存储提供者（预留模块，当前不可用）
 */
public final class CustomEntityTraceProvider implements TraceStorageProvider {
    @Override
    public String id() {
        return "custom_entity";
    }

    @Override
    public boolean supports(TraceContext context) {
        return context.config().storageType().equals(id());
    }

    @Override
    public PlacementResult place(TraceContext context) {
        return PlacementResult.failure(context.lang().text("storage.custom-entity.unavailable"));
    }
}
