# 遗体客户端回归

脚本只连接 `127.0.0.1:25576`，使用离线 OP 玩家 `CoreBot`。它会更改测试世界规则、创建平台、清空测试玩家背包并停止测试服务器，只能在独立 `run-core` 世界运行。

1. 使用 Java 21，安装 Node.js 和脚本依赖：`npm install --prefix scripts`。
2. 在 `run-core/server.properties` 中设置 `server-ip=127.0.0.1`、`server-port=25576`、`online-mode=false`、`spawn-protection=0`。使用独立世界，视距和模拟距离可设为 3。
3. 用 `./gradlew.bat runServer` 启动并完成测试服设置。在控制台执行 `op CoreBot`。
4. 执行 `node scripts/smoke-corpse.cjs exercise`。脚本通过后输出 `PASS` 并停服。
5. 重启测试服，执行 `node scripts/smoke-corpse.cjs seed`，留下有物品的遗体并停服。
6. 再次启动后执行 `node scripts/smoke-corpse.cjs resume`，核对恢复后的槽位、领取与取空清理。

已有依赖时，也可让 `NODE_PATH` 指向其 `node_modules` 目录。

`exercise` 和 `resume` 验证装备、副手、背包、快捷栏、部分领取、满背包、关闭重开、离开区块后返回、取空消失和 keepInventory。离开测试点并不单独证明服务器已卸载区块；要验证卸载事件，需结合服务端区块状态。

真人客户端还需检查睡姿、皮肤、地面贴合与点击命中。自动脚本不验证渲染像素。
