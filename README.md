<p align="center">
  <img src="assets/logo-memorial.png" alt="TracesDeath 插件图标" width="192" height="192">
</p>

# TracesDeath

简体中文 · [English](README.en.md)

**轻量 Minecraft 遗体插件：死亡留下遗体，右键取回物品与经验，取空后自动消失。**

无需资源包即可显示带皮肤与装备的玩家遗体，或带死者头颅的组合墓碑。安装可选资源包后，墓碑获得专用纹理，遗体 GUI 显示苔石边框与装饰。

[![Version](https://img.shields.io/github/v/release/polang233/TracesDeath?label=Version&color=2ea44f)](https://github.com/polang233/TracesDeath/releases/latest)
![Minecraft 支持 1.12+ · 推荐 1.20+](https://img.shields.io/badge/Minecraft-支持_1.12%2B_·_推荐_1.20%2B-62b47a)
[![License](https://img.shields.io/github/license/polang233/TracesDeath?color=blue)](LICENSE)

[核心设计](docs/ARCHITECTURE.md) · [功能清单](docs/FEATURES.md) · [实体与材质配置](docs/ENTITY_TYPES.md)

## 下载

[GitHub Releases](https://github.com/polang233/TracesDeath/releases/latest) 提供插件 JAR 与可选资源包 `TracesDeath.zip`。

## 效果展示

**玩家遗体 · 无需资源包**

<p align="center">
  <img src="assets/screenshot-corpse.png" alt="带玩家皮肤与装备的躺卧遗体" width="800">
</p>

**不加载资源包：组合墓碑与原版界面**

<p align="center">
  <img src="assets/screenshots/showcase-vanilla.png" alt="无需资源包的组合墓碑与遗体界面" width="960">
</p>

**加载资源包：专用墓碑材质与 GUI 装饰**

<p align="center">
  <img src="assets/screenshots/showcase-resourcepack.png" alt="资源包的暖色墓碑与苔石界面" width="960">
</p>

## 核心功能

- 玩家模型、组合墓碑、兼容旧版本的箱子矿车三种外观。
- 装备与主手独立展示，支持点击、Shift 和一键拾取，按配置恢复原槽位或收入背包。
- 显示死亡信息、剩余物品与经验；经验默认保留 50%，可调比例，支持单独领取。
- 尊重死亡不掉落，支持按名称、Lore 排除绑定等特殊物品。
- 内置中英文语言文件，提示、命令与界面文案可配置并重载。
- 保存遗体记录，支持重启恢复，取空后清理全部展示部件。

## 开始使用

推荐使用 **1.20+**。将 JAR 放入服务端 `plugins` 后重启。箱子矿车支持 Bukkit/Spigot/Paper 1.12+，墓碑要求 Paper 1.19.4+，玩家模型要求 Paper 1.21.9+；Java 版本遵循所用服务端要求。

在 `config.yml` 的 `corpse.type` 选择 `auto`、`mannequin`、`tombstone` 或 `chest_minecart`。`auto` 优先使用可用的玩家模型。选择墓碑并执行 `/td reload` 后，自动生成 `tombstone.yml`，同时导出组合资源包到 `plugins/TracesDeath/resource-packs/`。

资源包适用于 1.20+ 客户端：将 ZIP 放入 `.minecraft/resourcepacks/`，在游戏设置中启用即可。玩家可自由选择是否安装。

`config.yml` 中用 `language: zh_CN` 或 `en_US` 切换语言，`death.keep-percent` 设置经验保留百分比。领取按钮点击取回全部物品与经验，鼠标指向按钮按 Q 只取经验；取走最后一件物品的人也会获得剩余经验。

常用命令：`/td list` 查看遗体，`/td locate` 查看位置，管理员用 `/td reload` 重载配置、`/td testpack [玩家]` 手动测试资源包。替换插件 JAR 后需要重启。

---

## 支持与反馈

- QQ 群：**620224543**
- [GitHub Issues：问题与建议](https://github.com/polang233/TracesDeath/issues)

**如果觉得好用，欢迎点个 ⭐ Star 支持一下！**

## 使用统计

[![TracesDeath bStats](https://bstats.org/signatures/bukkit/TracesDeath.svg)](https://bstats.org/plugin/bukkit/TracesDeath/34148)
