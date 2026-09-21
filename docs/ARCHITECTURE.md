# TracesDeath 核心设计

TracesDeath 的主流程是死亡掉落接管、遗体展示、界面领取和取空清理。本文记录当前实现边界及扩展方式，功能状态见 [功能清单](FEATURES.md)，各类遗体的表现与保护边界见 [实体类型](ENTITY_TYPES.md)。

## 代码结构与职责

按职责分为 `commands`、`core`、`config`、`corpse`、`gui`、`storage`、`entity`、`compat`、`resourcepack` 和 `hook`。主类负责启动装配和停用清理，各包负责对应业务。

以下路径相对于 `src/main/java/cc/sbsj/mc/tracesdeath/`：

- `TracesDeath`：装配运行实例、注册命令与统计，负责启动、重载切换及停用。
- `core/PluginRuntime`：持有一代配置及其服务、监听、任务和下载服务，统一启动与清理。
- `commands/ReloadCommand`：检查管理员权限并执行配置重载。
- `commands/TracesDeathCommand`：分发 `list`、`locate`、`recover`，处理参数、发送者权限、文本输出和补全。
- `corpse/Corpse`：保存遗体身份、世界与位置、玩家皮肤、死亡时间、物品和待完成领取。
- `corpse/CorpseItems`：匹配死亡掉落与原始槽位，映射 GUI 槽位，计算领取结果及掉落清单。
- `corpse/CorpseService`：管理活动记录，串行执行死亡接管、权限与距离检查、领取、恢复和终结。
- `storage/CorpseStore`：读取版本化 YAML、刷新临时文件并原子替换，保存失败向调用者报告。
- `entity/CorpseEntities`：通过 `CorpseRenderer` 创建展示与点击实体，处理实体事件并修复缺失外观。
- `config/CorpseAppearance`：校验遗体类型、界面材质开关与版本要求。
- `config/PluginSettings`：解析领取权限与领取模式。
- `config/TombstoneConfiguration`：仅在选择墓碑时创建并读取 `tombstone.yml`，保留已有设置。
- `config/CustomTextureSettings`：读取墓碑模型编号、GUI 开关、界面字形和测试地址。
- `config/GuiItemSettings`：解析按钮材质、名称、动态 Lore 和模型编号，仅应用于装饰物品。
- `resourcepack/ResourcePackTestService`：导出内置材质 ZIP，响应手动测试请求并管理下载服务。
- `commands/ResourcePackTestCommand`：校验权限与目标玩家，调用测试服务。
- `resourcepack/ResourcePackHttpServer`：按固定路径提供内置 ZIP，停用时释放端口与线程。
- `gui/CorpseMenu`：渲染库存副本，管理分页和单人查看锁，将领取交给服务。

`entity/CorpseRenderer` 定义展示接口。三种实现各自独立：`ChestMinecartRenderer` 位于 `src/main`，`TombstoneRenderer` 位于 `src/display`，`MannequinRenderer` 位于 `src/mannequin`。它们只负责外观生成和更新，实体保护及生命周期由 `CorpseEntities` 统一处理。每个部件生成后必须调用 configure 加入同一 EntityGroup；组内任一部件缺失时整组重建，卸载、重载和取空时移除全部成员。墓碑默认由七个展示部件和一个点击实体组成：主体、四块结构石材、深色碑面和玩家头颅，物品记录仍只有一份。

`compat/bukkit/BukkitServerAdapter` 提供 Bukkit 基础行为，`compat/paper/PaperServerAdapter` 提供 Paper 的界面、皮肤和显示能力。版本判断与反射加载集中在 `ServerAdapterFactory`，GUI 和领取服务使用 `ServerAdapter` 接口。

`hook/Metrics` 保存 bStats 官方单文件实现，服务 ID 为 34148。主类负责启用和关闭统计；新增第三方集成按具体服务放入 `hook`。

命令和界面都通过 `CorpseService` 改变库存。物品计算集中在 `CorpseItems`，文件读写集中在 `CorpseStore`，实体装备只用于展示。

## 版本隔离

同一 JAR 包含三组字节码：`src/main` 使用 Java 8 与 Spigot 1.12.2 API，`src/display` 使用 Java 17 与 Paper 1.19.4 API，`src/mannequin` 使用 Java 21 与 Paper 1.21.9 API。`ServerAdapterFactory` 检查版本和 Paper 能力后反射加载对应实现，旧服务器不会加载新版类。

`ServerAdapter` 负责菜单、皮肤、地面定位及渲染器选择，`CorpseRenderer` 负责实体展示。显式选择不支持的类型时停止启用并报告原因；`auto` 选择可用的 Mannequin，否则选择箱子矿车。

## 数据模型

每具遗体有独立 UUID，记录死者 UUID 与名称、世界 UUID、坐标、朝向、选中的快捷栏槽、可移植的皮肤属性、死亡时间和物品槽位。

物品编号与 `PlayerInventory` 对齐：0–8 是快捷栏，9–35 是背包，36–39 是靴子、护腿、胸甲、头盔，40 是副手。41 起保存其他插件提供、无法匹配原槽位的额外掉落。主手直接引用选中的快捷栏槽。

接管数量以死亡事件实际掉落为准，通过逐份扣减匹配原槽位。若多格物品完全相同而其他插件只保留其中一份，事件数据不足以辨认原格，当前按槽位顺序匹配并保持数量一致。

`corpses/<UUID>.yml` 当前格式为 schema 3，兼容 schema 1、2。皮肤以 name/value/signature 属性列表保存，读取旧 Profile 时提取属性。物品仍使用对应服务端的 ItemStack 格式，高版本物品数据不能保证降级读取。死亡时间缺失时显示“未记录”；旧的 36 格领取快照按原范围核对，新记录覆盖背包、护甲、副手共 41 格。记录不保存实体 UUID。

读取损坏数据或不支持的格式时停止启用，保留文件。回退插件版本前恢复对应数据备份。

## 生命周期

1. 启动时先加载记录，再注册监听、创建菜单和实体服务；仅在相关区块已加载时展示遗体。
2. 死亡时检查 `keepInventory` 和 `tracesdeath.use`，匹配物品并保存记录。初次保存成功后清空接管的原生掉落，随后生成外观。
3. 玩家右键后检查世界、距离、所有者限制和待恢复状态，再取得查看锁。关闭界面或断线释放锁。
4. 点击领取在下一 tick 执行。每次领取重新校验访问条件，成功后刷新界面和装备外观。
5. 取空先保存完成标记，再关闭界面、移除活动记录和外观。完成文件保留，避免删除失败复活库存。
6. 区块卸载或插件停用时移除非持久化实体。区块加载、世界加载及共享修复任务依据活动记录重建外观。

支持的 API 使用 `setPersistent(false)`。1.12 没有此接口，空矿车可能随世界保存；启动与区块加载时按稳定 scoreboard tag 清理残留展示，再依据记录重建。显示失败保留物品记录，实体加载不强制加载其他区块。Paper 26.1 的普通与精确实体交互共享监听链，普通处理器先跳过精确事件，交由精确处理器负责取消和打开界面。

## 领取与恢复

`CorpseItems` 先计算目标库存，再由 `CorpseService` 保存 `PendingClaim`。记录包含领取者、库存范围、领取前后快照、遗体剩余物品、计划掉落物和 `drops-started`。

单格领取与 Shift 领取按背包实际空间扣减；玩家背包区域和操作按钮上的 Shift 不执行转移。装备占顶栏 0–4，5 为主手，6 为隔断，7 为信息，8 为拾取，9–17 为横向分隔。主手映射至 heldSlot，原快捷栏对应格不映射来源，保证每个库存槽只展示一次。信息按钮右键循环额外掉落页。`loot.claim-all-mode: restore_slots` 时，一键拾取恢复原槽位，将被替换的玩家物品列入掉落清单；`fill_inventory` 时收入背包，将溢出的遗体物品列入清单。额外掉落在恢复原槽位后尝试收入背包。

保存领取计划成功后才修改玩家库存并调用玩家保存。若有地面掉落，先持久化 `drops-started` 再生成物品，最后提交遗体结果。界面关闭后同步最终玩家库存。

保存失败时保留待完成记录，锁定对应遗体及领取者的后续领取。登录时依据快照恢复：与领取后相同则继续完成，与领取前相同则保留原库存；无法判断或已进入掉落阶段时交给管理员核对。

人工核对使用控制台 `td recover <UUID> delivered` 或 `td recover <UUID> not-delivered`，领取玩家必须离线。命令只记录审核结果，不修改玩家背包，也不补发地面物品。

插件文件、玩家存档和世界物品属于不同保存系统，不能组成单一事务。任意强杀、断电和外部存档回滚仍有一致性边界；新增物品处理方式必须明确失败后如何核对，不能自动重发结果不明的物品。

## 扩展方式

以下是后续功能的接入约定，当前代码结构以“代码结构与职责”一节为准。

### 命令与权限

新命令从 `commands` 包接入，补全与帮助同步维护。子命令逻辑增多时，在该包内拆成独立处理类，由根命令统一分发。发送者类型、参数格式与命令权限在入口检查；库存变更、待完成状态和恢复条件仍由服务检查。

### 界面与配置

界面布局扩展接入 `CorpseMenu`，物品格到来源格的关系集中维护在 `CorpseItems.sourceSlot` 与 `pages`。装饰和操作按钮不能占用真实物品的来源编号。新增筛选或多界面时，让页面保存查看状态、服务保存库存状态。

`config.yml` 管理遗体类型与领取规则。选择墓碑后，`tombstone.yml` 管理模型、自定义 GUI 和测试地址；其他类型不访问该文件。入口校验后向服务传入解析结果。`/td reload` 先严格解析 YAML、检查配置与版本、验证遗体文件，再关闭当前界面、取消旧任务与监听并重建服务。准备阶段失败保留现有实例；启用阶段失败则按旧配置从已保存记录恢复，回退也失败时停用插件并保留记录。bStats 实例跨配置重载保留。客户端资源包需自行重新加载或手动测试发送。

### 玩法规则与生命周期

世界限制、保护时间、领取权限、过期和数量上限应由服务统一执行。自动清理、管理员操作与玩家领取必须遵守同一套库存终结规则，并处理正在打开的界面和待完成领取。

### 展示与外观

墓碑、资源包模型等展示沿用遗体记录。当前提供 Mannequin、ItemDisplay 墓碑和箱子矿车，由 `CorpseRenderer` 定义生成、位置、点击范围和更新；外观选择不改变库存保存和领取结算方式。每种展示明确区块加载、卸载及插件停用的清理责任。

### 资源包模块

构建把 GUI 背景、字体、墓碑模型与纹理打入同一资源包。资源包统一采用 Minecraft 1.20+ 格式。GUI 与墓碑的开关及渲染逻辑分别管理，普通 Gradle 构建直接使用已检入素材。默认 GUI 原画位于 `resource-pack/source/corpse-panel.png`；`scripts/export-gui-texture.ps1` 将完整底图等比例导出为 176×138 逻辑尺寸的 4 倍精度纹理，物品与文字由游戏动态绘制。

`TombstoneConfiguration` 根据所选类型决定是否读取专用文件，`CustomTextureSettings` 保存解析结果。墓碑模式可通过 `gui.enabled` 启用自定义 GUI。按钮始终为原版物品，墓碑通过 CustomModelData 指向模型。客户端资源决定最终显示，领取流程与材质状态无关。

墓碑模式启用时导出资源包，默认开启 GUI 装饰。其他类型仅在执行测试命令时按需导出。`/td testpack` 在权限和目标玩家检查后调用测试服务，首次请求时启动内置下载端口，发送内置组合包的带 SHA-1 的资源包请求。服务不注册加入或加载状态监听；停用时关闭 HTTP 服务。资源包可自行安装或合并到服主的现有包。

### 存储与对外接口

新增持久化字段时定义缺失值的含义，需要改变语义时升级 schema 并验证旧记录。引入数据库时围绕记录读取、原子保存、完成状态和待完成领取替换存储实现，保留服务的状态转换。

开放 API、事件或其他插件集成时，明确调用线程、可否取消以及事件发生在保存前还是保存后。对外查询提供快照，修改通过服务执行。后台日志、统计和通知消费已确认的结果。

## 验证与构建

构建使用 Java 21：`./gradlew.bat test build`。`./gradlew.bat runServer` 使用独立 `run-core` 目录运行 Paper 1.21.9；其他版本使用对应服务端要求的 Java。

变更领取逻辑需覆盖部分空间、满背包、装备冲突、物品守恒、准备与完成阶段保存失败、掉落中断和登录恢复。界面与命令变更需覆盖来源槽位、权限、重复点击、会话关闭和多人访问。外观变更需覆盖区块及世界生命周期。

客户端回归脚本保留在 `scripts`：

- `compat-smoke.cjs <java> <paper.jar> <plugin.jar> <协议版本> <外观类型> <新测试目录> <端口> <已接受的eula.txt>`：验证同一 JAR 的创建、Shift 领取、防存入、强制停服恢复与取空清理。已通过 Paper 1.12.2 箱子矿车、1.19.4 墓碑、1.21.9 Mannequin。
- `smoke-corpse.cjs [exercise|seed|resume]`：连接 `127.0.0.1:25576` 的独立测试服，使用离线 OP 玩家 `CoreBot`，验证死亡、单格领取、取空清理和保留遗体后的重启。测试服需设置对应端口和 `spawn-protection=0`。
- `restart-corpse.cjs <java> <paper.jar> <plugin.jar> <协议版本> <新测试目录> <已接受的eula.txt>`：自动创建测试服，强制结束自己启动的 Java 子进程，连续重启并比对库存、文件与掉落。默认端口 25577，可用 `RESTART_PORT` 调整。
- 重启脚本默认验证原位恢复；`CLAIM_ALL_MODE=inventory` 验证背包收纳与溢出。`RESTART_RUNTIME_CACHE` 可复用隔离测试服的运行时文件。26.1.1、26.1.2 对应 Java 25 和 bot 协议 `26.1`。

脚本依赖通过 `npm install --prefix scripts` 安装，也可用 `NODE_PATH` 指向已有依赖。脚本会更改规则、清空测试玩家背包并停服，只运行在隔离测试目录。服务端日志和阶段 JSON 用于核对结果；真人客户端继续验收皮肤、姿势、地形贴合与点击范围。
