# TracesDeath

![TracesDeath](https://raw.githubusercontent.com/polang233/TracesDeath/main/assets/logo-memorial-128.png)

**轻量 Minecraft 遗体插件：死亡留下遗体，右键取回物品，取空后自动消失。**

无需资源包即可显示玩家尸体或带死者头颅的组合墓碑。安装可选资源包后，墓碑换上暖色砂岩纹理，GUI 增加苔石边框与装饰。

[![版本](https://img.shields.io/github/v/release/polang233/TracesDeath?label=Version&color=2ea44f)](https://github.com/polang233/TracesDeath/releases/latest)
![推荐版本](https://img.shields.io/badge/推荐-1.20%2B-f4a940)
[![下载量](https://img.shields.io/github/downloads/polang233/TracesDeath/total?label=Downloads&color=2196f3)](https://github.com/polang233/TracesDeath/releases)

[下载插件与资源包](https://github.com/polang233/TracesDeath/releases/latest) · [项目源码](https://github.com/polang233/TracesDeath) · [问题反馈](https://github.com/polang233/TracesDeath/issues)

## 主要特点

- 三种外观：玩家模型、组合墓碑、兼容旧版本的箱子矿车。
- 装备与主手独立显示，支持点击、Shift 和一键拾取；可恢复原槽位或收入背包。
- 显示死亡者、死亡时间、死亡位置和剩余物品格数。
- 保存遗体记录，重启后恢复，取空后统一清理展示部件。
- 支持所有者限制、墓碑 GUI 物品配置和管理员 `/td reload`。

## 实测效果

### 玩家遗体，无需资源包

![玩家遗体](https://raw.githubusercontent.com/polang233/TracesDeath/main/assets/screenshot-corpse.png)

### 不加载资源包

![组合墓碑](https://raw.githubusercontent.com/polang233/TracesDeath/main/assets/screenshots/tombstone-vanilla.png)

![原版界面](https://raw.githubusercontent.com/polang233/TracesDeath/main/assets/screenshots/gui-vanilla.png)

### 加载可选资源包

![暖色墓碑](https://raw.githubusercontent.com/polang233/TracesDeath/main/assets/screenshots/tombstone-resourcepack.png)

![材质界面](https://raw.githubusercontent.com/polang233/TracesDeath/main/assets/screenshots/gui-resourcepack.png)

## 版本与安装

推荐 **1.20+**。箱子矿车支持 Bukkit/Spigot/Paper 1.12+，墓碑要求 Paper 1.19.4+，玩家模型要求 Paper 1.21.9+。Java 版本遵循所用服务端要求。

将 JAR 放入 `plugins` 后重启。在 `config.yml` 的 `corpse.type` 选择 `auto`、`mannequin`、`tombstone` 或 `chest_minecart`。选择墓碑并执行 `/td reload` 后，自动生成 `tombstone.yml` 并导出组合资源包。

资源包供 1.20+ 客户端使用：将 `TracesDeath.zip` 放入 `.minecraft/resourcepacks/`，在游戏设置中启用即可。插件不自动下发，也不要求玩家安装。

常用命令：`/td list` 查看遗体，`/td locate` 查看位置；管理员用 `/td reload` 重载配置、`/td testpack [玩家]` 手动测试资源包。

[详细配置说明](https://github.com/polang233/TracesDeath/blob/main/docs/ENTITY_TYPES.md)

## 支持与统计

作者：**Polang**。项目使用 [GNU GPLv3](https://github.com/polang233/TracesDeath/blob/main/LICENSE) 开源，欢迎反馈问题和贡献代码。觉得好用的话，欢迎点个 [⭐ Star](https://github.com/polang233/TracesDeath)！

默认启用 bStats 基础统计，遵循服务端全局设置。

[![TracesDeath 使用统计](https://bstats.org/signatures/bukkit/TracesDeath.svg)](https://bstats.org/plugin/bukkit/TracesDeath/34148)
