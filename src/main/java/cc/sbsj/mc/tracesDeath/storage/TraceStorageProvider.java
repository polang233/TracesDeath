package cc.sbsj.mc.tracesDeath.storage;

import cc.sbsj.mc.tracesDeath.trace.TraceContext;
import cc.sbsj.mc.tracesDeath.trace.TraceData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 墓碑存储提供者接口
 * <p>
 * 定义了不同类型的墓碑容器（方块、实体等）的通用行为。
 * 所有存储类型都必须实现此接口以便被 TraceStorageRegistry 管理。
 */
public interface TraceStorageProvider {
    
    /**
     * 获取此存储提供者的唯一标识符
     * @return 提供者ID，如 "block", "mannequin"
     */
    @NotNull
    String id();
    
    /**
     * 检查此提供者是否支持给定的上下文配置
     * @param context 墓碑上下文
     * @return 如果支持返回 true
     */
    boolean supports(@NotNull TraceContext context);
    
    /**
     * 在指定位置放置墓碑容器
     * @param context 墓碑上下文，包含玩家信息、掉落物、位置等
     * @return 放置结果，包含成功状态和消息
     */
    @NotNull
    PlacementResult place(@NotNull TraceContext context);
    
    /**
     * 清理指定位置的墓碑容器
     * <p>
     * 默认实现为空操作，子类可根据需要覆盖
     * @param data 墓碑权威数据
     * @return 如果成功清理返回 true
     */
    default boolean cleanup(@NotNull TraceData data) {
        return false;
    }
    
    /**
     * 检查指定位置是否存在有效的墓碑容器
     * @param data 墓碑权威数据
     * @return 如果存在有效容器返回 true
     */
    default boolean isValid(@NotNull TraceData data) {
        return true;
    }
    
    /**
     * 获取此存储类型的显示名称
     * @return 人类可读的类型名称
     */
    @NotNull
    default String displayName() {
        return id();
    }
}
