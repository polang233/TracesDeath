# TracesDeath

面向 Paper 26.2 的死亡墓碑插件，默认使用高版本 Mannequin 玩家模型 NPC 展示死亡玩家，而不是盔甲架。

玩家死亡后，物品进入插件维护的唯一墓碑记录。世界中生成两个关联实体：

- Mannequin：显示玩家皮肤、死亡姿势和装备视觉副本。
- Interaction：提供独立、可配置的点击区域，解决躺卧 NPC 难以命中的问题。

两个实体 UUID 都会持久化。墓碑清空、过期或被管理员移除时，两者会一起删除；区块重新加载时会清理孤儿实体或补齐缺失实体。

## 当前能力

### Mannequin NPC

- 使用死亡玩家 Profile 和全部皮肤层。
- 固定睡眠姿势、无 AI、无重力、不可推动；朝向取整到直角并把生成点回撤半个身长，使尸体居中在方块内。
- 显示护甲、主手和副手的视觉副本。
- Mannequin 不是 Mob，掉落概率 API 不可用；视觉装备依赖无敌状态、死亡事件清空掉落和删除前清空装备来防掉落。
- 同时支持点击 Mannequin 本体和 Interaction 代理。
- 默认左右键都可交互；普通点击打开 GUI，潜行点击快速领取。
- 名字只渲染一行：description 渲染层保持隐藏，避免与自定义名称重复。

### 物品领取

- GUI 每页展示 45 个物品，支持翻页，不会因固定分类槽位而截断。
- GUI 由服务端控制，只允许取出，禁止 Shift 点击、拖拽和变相存入。
- 同一墓碑同一时间只允许一名玩家打开。
- GUI 和快速领取共用同一套差额结算。
- 背包只有部分空间时，以 Bukkit 返回的实际剩余数量更新墓碑。
- 可自动装备空的护甲槽；冲突物品可留在墓碑或按配置掉落。

### 数据和生命周期

- 墓碑物品只保存在 TraceData 中，NPC 装备不是第二份可领取库存。
- 每个墓碑使用独立 YAML 文件，包含 schema version、世界 UUID、存储类型及实体 UUID。
- 文件通过临时文件加原子替换写入。
- 初次持久化失败时删除已经生成的 NPC 和点击代理，并保留原生死亡掉落。
- 正常停服保存，重启后恢复活动索引并校验已加载的 NPC。
- 定时过期；可选择掉落或销毁剩余物品。
- keepInventory 始终按原版规则处理；当前版本不接管经验。

### 存储类型

| 类型 | 状态 | 说明 |
|---|---|---|
| mannequin | 默认 | Mannequin 外观加 Interaction 点击代理 |
| block | 兼容回退 | 空容器方块作为世界标记，物品仍使用虚拟记录 |

旧的 minecart、corpse、custom_entity 及盔甲架库存链路已经移除。

## 交互

Mannequin 默认配置为 BOTH：

- 普通左键或右键：打开只取不存 GUI。
- 潜行左键或右键：快速领取全部可容纳物品。
- 超过 7 方块：拒绝交互。
- owner-only 开启时：仅所有者、tracesdeath.admin 或 tracesdeath.admin.bypass 可访问。

## 命令

主命令 /tracesdeath，别名 /td、/deathtrace。

| 命令 | 说明 |
|---|---|
| /td reload | 重载配置和语言 |
| /td types | 查看已注册存储类型 |
| /td list [玩家名] | 列出活动墓碑 |
| /td locate [ID] | 查看自己的墓碑位置 |
| /td info ID | 查看墓碑详情 |
| /td remove ID [drop] | 移除指定墓碑，可选掉落剩余物品 |
| /td clear | 清除所有活动墓碑 |
| /td debug create | 使用手持物品创建测试墓碑 |
| /td debug synth 玩家名 [x y z] | 用离线玩家档案在世界坐标合成测试墓碑（控制台可用） |

管理命令需要 tracesdeath.admin。

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| tracesdeath.use | true | 玩家死亡时启用墓碑功能 |
| tracesdeath.admin | OP | 管理命令及所有者限制绕过 |
| tracesdeath.admin.bypass | OP | 仅绕过所有者限制 |

## 主要配置

~~~yaml
enabled: true

storage:
  type: mannequin

death:
  play-sound: true
  show-particles: true

expiration:
  time-seconds: 600
  cleanup-interval-seconds: 60
  drop-on-expire: true

protection:
  owner-only: false

interaction:
  max-distance: 7.0

types:
  mannequin:
    auto-remove-when-empty: true
    lava-proof: true
    hitbox:
      width: 1.8
      height: 1.2
    interaction:
      click-type: BOTH
      mode: OPEN_GUI
      auto-equip: true
      conflict-handling: TRY_INVENTORY

  block:
    material: CHEST
    auto-remove-when-empty: true
    lava-proof: true
    interaction:
      click-type: RIGHT_CLICK
      mode: OPEN_GUI
      auto-equip: true
      conflict-handling: TRY_INVENTORY
~~~

当 mode 为 OPEN_GUI 时，潜行点击仍会执行快速领取。conflict-handling 可选 TRY_INVENTORY 或 DROP。

## 核心结构

| 组件 | 职责 |
|---|---|
| TraceManager | 创建、恢复、过期和幂等终结 |
| TraceCacheManager | 内存记录、版本化 YAML 和原子写入 |
| MannequinTraceProvider | NPC、点击代理、实体定位、修复和删除 |
| MannequinEvents | NPC 交互、死亡拦截、区块加载和孤儿清理 |
| TraceInteractionService | 方块与 NPC 的统一权限、距离和交互入口 |
| TraceClaimService | 精确领取、自动装备和剩余量结算 |
| TraceGuiManager | 分页、只取不存和单墓碑会话锁 |

## 构建和测试

要求 Java 25。

~~~bash
./gradlew test
./gradlew build
./gradlew runServer
~~~

当前单元测试覆盖配置默认值与边界、数据/位置隔离、提供者元数据不可变性和 UUID 解析。NPC 的真实点击、区块卸载和死亡实体行为仍需在 Paper 测试服进行人工回归。
