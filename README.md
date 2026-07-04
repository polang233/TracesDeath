# TracesDeath

Minecraft Paper 服务端死亡墓碑插件。玩家死亡后，插件将本应掉落在地上的物品收集起来，在死亡位置附近生成一个墓碑容器，方便玩家回来取回物品。

项目当前面向 Paper API `26.2`（Minecraft 26.2），构建使用 Java 25。

## 功能概览

### 多种存储类型

| 类型 | ID | 说明 |
|------|-----|------|
| 方块容器 | `block` | 使用箱子、木桶、潜影盒等方块作为墓碑容器 |
| 箱子矿车 | `minecart` | 使用箱子矿车实体作为移动容器 |
| 尸体实体 | `corpse` | 使用玩家头颅盔甲架 + 虚拟背包（TODO） |
| 自定义实体 | `custom_entity` | 预留模块，当前 Paper API 不支持自定义实体注册 |

### 虚拟 GUI 界面

- 54 格大箱子界面（6行×9列）
- 第1行显示装备类物品，第2行为分隔板，第3-6行显示物品栏内容
- 分隔板和标签物品不可被拿走或拖拽
- 关闭界面时自动同步缓存数据
- 物品拿空后可自动移除墓碑

### 交互模式

每个存储类型可独立配置交互方式：

- **点击方式**：右键 / 左键 / 两者皆可
- **交互行为**：
  - `OPEN_GUI` — 打开虚拟背包界面
  - `DIRECT_COLLECT` — 直接将物品获取到身上（类似墓碑 mod）
- **自动装备**：`DIRECT_COLLECT` 模式下，护甲类物品自动穿到对应部位
- **冲突处理**：当玩家已有装备或背包满时：
  - `DROP` — 直接丢到地上
  - `TRY_INVENTORY` — 先尝试放入背包，放不下再丢地上

### 数据持久化

- 物品数据存储在内存缓存 + YML 文件中
- 服务器异常重启后可从文件恢复墓碑数据
- 每 5 分钟自动保存一次

### 墓碑保护

- 防熔岩燃烧（方块容器不会被岩浆烧毁）
- 浮力特性（容器可浮在水面和熔岩上）
- 防爆保护
- 可配置仅所有者可打开

### 过期与清理

- 可配置墓碑存在时间（默认 10 分钟），0 为永不过期
- 定期自动检查并清理过期墓碑
- 过期时可选择将物品掉落或销毁
- 提前警告玩家墓碑即将消失

### 死亡不掉落兼容

- 可配置是否无视死亡不掉落规则
- `ignore-keep-inventory: true` — 即使开了死亡不掉落也创建墓碑
- `ignore-keep-inventory: false` — 尊重游戏规则，不掉落时不创建墓碑

## 命令

主命令 `/tracesdeath`，别名 `/td`、`/deathtrace`。

```
/td reload              重载配置文件和语言文件
/td types               查看已注册的存储类型
/td list [玩家名]       列出活动墓碑（可按玩家筛选）
/td info <ID>           查看墓碑详情（位置、存在时间、剩余时间等）
/td remove <ID> [drop]  移除指定墓碑（可选是否掉落物品）
/td clear               清除所有活动墓碑
/td debug create        在当前位置创建测试墓碑（用手持物品）
```

所有管理命令需要权限 `tracesdeath.admin`（默认 OP）。

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `tracesdeath.admin` | OP | 允许使用管理命令 |
| `tracesdeath.use` | 所有玩家 | 允许使用墓碑功能 |

## 配置文件

### config.yml

```yaml
debug: false          # 调试日志
enabled: true         # 全局开关

storage:
  type: block         # block / minecart / corpse / custom_entity

death:
  ignore-keep-inventory: false  # 是否无视死亡不掉落
  clear-drops: true             # 清除原生物品掉落
  clear-experience: false       # 清除经验球
  play-sound: true              # 创建墓碑时播放音效
  show-particles: true          # 创建墓碑时显示粒子

placement:
  search-radius: 3    # 搜索可放置位置的半径
  force-place: false   # 找不到位置时强制放在死亡点

expiration:
  time-seconds: 600            # 墓碑存在时间（秒），0=永不
  cleanup-interval-seconds: 60 # 清理检查间隔
  drop-on-expire: true         # 过期时掉落物品
  warn-before-expire: true     # 提前警告
  warn-time-seconds: 30        # 提前警告时间

protection:
  owner-only: false      # 仅所有者可打开
  allow-break: false     # 允许破坏容器
  explosion-proof: true  # 防爆

types:
  block:
    material: CHEST
    auto-remove-when-empty: true
    lava-proof: true
    buoyant: true
    interaction:
      click-type: RIGHT_CLICK       # RIGHT_CLICK / LEFT_CLICK / BOTH
      mode: OPEN_GUI                # OPEN_GUI / DIRECT_COLLECT
      auto-equip: true
      conflict-handling: TRY_INVENTORY  # DROP / TRY_INVENTORY

  minecart:
    entity-type: CHEST_MINECART
    auto-remove-when-empty: true
    lava-proof: true
    buoyant: true
    interaction: { ... }

  corpse:
    auto-remove-when-empty: true
    lava-proof: true
    buoyant: true
    interaction: { ... }
```

### lang.yml

支持 `&` 颜色代码和占位符替换：

| 占位符 | 说明 |
|--------|------|
| `{player}` | 死亡玩家名称 |
| `{material}` | 容器方块材质 |
| `{type}` | 存储类型 |
| `{types}` | 已注册类型列表 |
| `{label}` | 命令标签 |
| `{seconds}` | 剩余秒数 |

## 架构设计

### 模块化存储系统

所有存储类型实现统一的 `TraceStorageProvider` 接口：

```
TraceStorageProvider
├── id()           提供者标识符
├── supports()     检查是否支持当前配置
├── place()        放置墓碑容器
├── cleanup()      清理墓碑
├── isValid()      验证墓碑有效性
└── displayName()  人类可读名称
```

通过 `TraceStorageRegistry` 统一管理，新增类型只需实现接口并注册。

### 核心组件

| 组件 | 职责 |
|------|------|
| `TracesDeath` | 主插件类，初始化所有组件 |
| `TraceManager` | 墓碑生命周期管理（创建、移除、过期清理） |
| `TraceCacheManager` | 物品数据缓存 + YML 持久化 |
| `TraceGuiManager` | 虚拟 GUI 界面管理 |
| `TraceStorageRegistry` | 存储提供者注册与分发 |
| `TraceConfig` | 配置管理，类型安全访问 |
| `Lang` | 语言文件管理，占位符替换 |
| `PlayerEvents` | 监听玩家死亡事件 |
| `TraceInteractEvents` | 处理玩家与墓碑的交互 |
| `TraceContainerEvents` | 容器关闭时的自动清理 |
| `TraceProtectionEvents` | 防熔岩、防火焰保护 |

### 数据流

```
玩家死亡 → PlayerEvents
  → TraceManager.createTrace()
    → TraceStorageProvider.place()     放置方块/实体标记
    → TraceCacheManager.createTrace()  保存物品到缓存+YML
  → 清除原生掉落物

玩家交互 → TraceInteractEvents
  → OPEN_GUI: TraceGuiManager.openTraceGui()
  → DIRECT_COLLECT: 直接分配到玩家背包/装备栏

GUI关闭 / 物品拿空 → 更新缓存 → 自动清理判断
```

## TODO

- [ ] **尸体实体（corpse）适配虚拟缓存系统**：当前 `CorpseEntityTraceProvider` 仍使用旧的 PDC 序列化方式存储物品，尚未接入 `TraceCacheManager` + `TraceGuiManager` 体系。需要改为与 `block` 类型一致的缓存+GUI方案
- [ ] **箱子矿车（minecart）适配虚拟缓存系统**：同上，`MinecartChestTraceProvider` 仍直接将物品放入矿车库存
- [ ] **owner-only 权限检查**：配置中有 `owner-only` 选项，但 `TraceInteractEvents` 中尚未实现所有者验证逻辑
- [ ] **跨世界追踪**：墓碑位置固定，不支持跨世界传送
- [ ] **区块卸载处理**：实体型墓碑在区块卸载时可能丢失
- [ ] **自定义实体**：等待 Paper 暴露稳定的自定义实体注册接口

自动启动 Paper 26.2 测试服务器并加载插件。

## 环境要求

- **Minecraft**: 26.2 / Paper API 26.2+
- **Java**: 25+（Paper 26.x 最低要求）
