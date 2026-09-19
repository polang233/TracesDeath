<p align="center">
  <img src="assets/logo-memorial-128.png" alt="TracesDeath 插件图标" width="128" height="128">
</p>

# TracesDeath

**玩家死亡后留下一具带皮肤与装备的遗体，右键取回物品，取空后自动消失。**

基于 Mannequin 的 Paper 遗体插件，保留背包、快捷栏、护甲与副手的槽位布局，支持逐格领取和一键恢复。

[![GitHub](https://img.shields.io/badge/GitHub-Source-181717?logo=github&logoColor=white)](https://github.com/polang233/TracesDeath)
![Paper](https://img.shields.io/badge/Paper-1.21.9%2B-62b47a)
![Java](https://img.shields.io/badge/Java-21%2B-e76f00)

[核心设计](docs/ARCHITECTURE.md) · [功能清单](docs/FEATURES.md) · [问题与建议](https://github.com/polang233/TracesDeath/issues)

## 效果展示

<p align="center">
  <img src="assets/screenshot-corpse.png" alt="带玩家皮肤和手持装备的躺卧遗体" width="900">
</p>

## 核心功能

- **玩家遗体**：显示死亡玩家的皮肤与装备，取走物品后同步更新外观。
- **原槽位界面**：装备与物品分区展示，背包和快捷栏保留死亡时的位置。
- **一键领取**：默认恢复到原槽位，也可配置为收入背包。
- **遗体信息**：查看死亡者 ID、UUID、死亡时间、位置和剩余物品数量。
- **重启恢复**：保存遗体记录，重启后重新显示；物品取空后自动清理遗体。

## 开始使用

将 JAR 放入 Paper 服务端的 `plugins` 目录，完整重启服务器。最低 API 版本为 1.21.9；Java 版本按对应 Paper 服务端的要求选择。

右键遗体打开界面，点击物品逐格领取，或点击箱子按钮一键领取。`/td list` 查看遗体，`/td locate` 查看自己的遗体位置。

```yaml
# 仅允许死者本人或管理员领取
owner-only: false

# false：恢复原槽位，原槽位已有的物品掉在脚下
# true：收入背包，装不下的遗体物品掉在脚下
claim-all-to-inventory: false
```

修改配置后重启生效。单格领取时，背包放不下的物品保留在遗体中。

---

由 **Polang** 开发。欢迎通过 [Issues](https://github.com/polang233/TracesDeath/issues) 反馈问题或建议，请附上服务端版本、插件版本和相关日志。

如果这个插件对你有帮助，欢迎在 [GitHub](https://github.com/polang233/TracesDeath) 点个 Star。
