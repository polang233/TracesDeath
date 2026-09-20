<p align="center">
  <img src="assets/logo-memorial-128.png" alt="TracesDeath 插件图标" width="128" height="128">
</p>

# TracesDeath

**玩家死亡后留下遗体，右键取回物品，取空后自动消失。**

可选择玩家模型、资源包墓碑或箱子矿车，保留背包、快捷栏、护甲与副手的槽位布局，支持点击、Shift 领取和一键恢复。

[![GitHub](https://img.shields.io/badge/GitHub-Source-181717?logo=github&logoColor=white)](https://github.com/polang233/TracesDeath)
![Minecraft](https://img.shields.io/badge/Minecraft-1.12%2B-62b47a)
![Java](https://img.shields.io/badge/Java-8%2B-e76f00)

[核心设计](docs/ARCHITECTURE.md) · [功能清单](docs/FEATURES.md) · [实体类型](docs/ENTITY_TYPES.md) · [问题与建议](https://github.com/polang233/TracesDeath/issues)

## 效果展示

<p align="center">
  <img src="assets/screenshot-corpse.png" alt="带玩家皮肤和手持装备的躺卧遗体" width="900">
</p>

## 核心功能

- **三种外观**：Mannequin 玩家模型、资源包墓碑、兼容旧版本的箱子矿车。
- **原槽位界面**：装备与物品分区展示，背包和快捷栏保留死亡时的位置。
- **一键领取**：默认恢复到原槽位，也可配置为收入背包。
- **遗体信息**：查看死亡者 ID、UUID、死亡时间、位置和剩余物品数量。
- **重启恢复**：保存遗体记录，重启后重新显示；物品取空后自动清理遗体。

## 开始使用

将 JAR 放入服务端的 `plugins` 目录，完整重启。箱子矿车支持 Bukkit/Spigot/Paper 1.12+，墓碑要求 Paper 1.19.4+，Mannequin 要求 Paper 1.21.9+。Java 按所用服务端要求选择。

右键遗体打开界面，点击或 Shift 点击物品逐格领取，或点击领取按钮一键领取。`/td list` 查看遗体，`/td locate` 查看自己的遗体位置。

```yaml
corpse:
  # auto、mannequin、tombstone、chest_minecart
  type: auto
loot:
  owner-only: false
  # restore_slots 恢复原槽位；fill_inventory 收入背包
  claim-all-mode: restore_slots
```

`auto` 在支持的 Paper 上选用 Mannequin，其余选用箱子矿车。设置 `corpse.type: tombstone` 并重启后，会生成 `tombstone.yml`，其中可以配置墓碑模型、GUI 材质和手动测试地址。其他遗体类型不会生成或读取此文件；已有文件会保留。

启用墓碑模式后在 `plugins/TracesDeath/resource-packs/` 导出包含 GUI 和墓碑素材的内置包，玩家将对应版本 ZIP 放入客户端 `.minecraft/resourcepacks/`，并在游戏资源包设置中启用即可。墓碑配置默认开启 GUI 装饰。GUI 保留原版按钮，加载材质后增加界面装饰；墓碑加载材质后显示自定义模型。管理员可用 `/td testpack [玩家]` 手动测试。按钮材质、名称、Lore 与 CustomModelData 可在 `tombstone.yml` 的 `gui.items` 中调整。详见[材质设置](docs/ENTITY_TYPES.md#资源包与界面)。

修改配置后重启生效。单格领取时，背包放不下的物品保留在遗体中。

插件默认启用 bStats 基础使用统计（服务 ID：34148），遵循服务端的 bStats 全局设置。

---

由 **Polang** 开发。欢迎通过 [Issues](https://github.com/polang233/TracesDeath/issues) 反馈问题或建议，请附上服务端版本、插件版本和相关日志。

如果这个插件对你有帮助，欢迎在 [GitHub](https://github.com/polang233/TracesDeath) 点个 Star。
