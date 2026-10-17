# GTLCore — Minecraft 1.21.1 NeoForge

本分支是 GTLCore skyblock **1.2.3.2-fix4** 的原生 NeoForge 移植，配套
[GTLAdditions 1.21.1](https://github.com/FortyTwoCn/GTLAdditions/tree/1.21.1)。
机器、配方、菜单、物品组件、网络、AE2/GTCEu 集成及多方块均使用 1.21.1 原生 API。

## 安装

使用 **Minecraft 1.21.1、NeoForge 21.1.248 和 Java 21**。
当前模组下载：[GTLCore fix4](https://github.com/FortyTwoCn/GTLCore/releases/tag/neoforge-1.21.1-1.2.3.2-fix4-deps.20261017)、[GTLAdditions fix3](https://github.com/FortyTwoCn/GTLAdditions/releases/tag/neoforge-1.21.1-3.2.8Custom-fix3-deps.20261017)。AE2 至少 19.2.17、AE2WTLib 19.5.1、FTB Ultimine 至少 2101.1.7；旅行法杖冷却改为可选 Ender IO 8.2.12-beta 联动。完整说明见 [前置用途与升级步骤](migration/dependency-update-20261017.md)。

以下历史预览包提供其余固定依赖和注册脚本。运行包内的旧 Core、Additions、AE2、AE2WTLib 及 Ultimine 需要按上述说明替换，删除旧 Travel Anchors 和顶层 AE2WTLib API JAR：

1. [native-runtime-mods.zip](https://github.com/FortyTwoCn/GTLCore/releases/download/neoforge-1.21.1-1.2.3.2-fix3-preview.1/native-runtime-mods.zip)：解压到游戏目录，包含两个模组与固定依赖，共 35 个顶层运行 JAR。
2. [required-kubejs-profile.zip](https://github.com/FortyTwoCn/GTLCore/releases/download/neoforge-1.21.1-1.2.3.2-fix3-preview.1/required-kubejs-profile.zip)：解压到同一目录，保留其中的 `kubejs/` 与 `config/`。
3. 按附件 `README.zh-CN.md` 启动新世界；单独的模组 JAR 也在发行版中提供，联合运行包已包含它们。

KubeJS 附件提供 GregTech Leisure 中必要的真实注册脚本、资产和原始太空电梯运行配方片段。
完整整合包的任务、其他模组和所有服务端脚本不属于这个移植交付。

## 构建

将两个仓库的 `1.21.1` 分支放在相邻的 `GTLCore/`、`GTLAdditions/` 目录中。
在 Java 21 环境执行：

```sh
cd GTLCore
python3 migration/prepare_native_workspace.py prepare --build-modularui
./gradlew assemble check verifyGtceuBaseline verifyNativeRuntime --no-daemon
cd ../GTLAdditions
./gradlew assemble check exportNativeRuntime --no-daemon
```

API 基准是 [FortyTwoCn/GregTech-Modern 1.21](https://github.com/FortyTwoCn/GregTech-Modern/tree/1.21)
的固定提交 `c72dc16b52795cbb0456b6e15e13cc4a85c0ed1b`。
Gradle 8.14、ModDevGradle 2.0.141、Parchment 1.21.1/2024.11.17 及所有依赖由工程固定。
准备脚本获取并核验 11 个源码依赖、应用记录的补丁并构建参考使用的 ModularUI。

## 验证与已知问题

已验证独立源码构建、普通 NeoForge 安装器启动与世界保存重载、148 个原始多方块、
231 个独立客户端菜单、AE2 网络交互、付费加工及火箭/太空电梯旅行等。
范围和实际限制详见 [migration/README.md](migration/README.md)。

**这是预发布。** 完整联机压力流程曾在两版 Temurin Java 21 的
`InventoryChangeTrigger` C2 编译代码中发生 SIGSEGV，根因仍未确认。
压力验收显式使用诊断 JIT 参数；普通客户端默认 JIT 验收通过，长期稳定性尚未完全验证。
诊断参数不进入生产 JAR。

发行版附有日志、报告、截图、SHA256 和完整联合源码。将 `validation-evidence.zip`
解压至本仓库的 `migration/`，即可阅读迁移文档中的本地证据链接。
上游作者、源码基线、依赖许可证和整合包 CC BY-NC-SA 4.0 来源记录均保留在迁移文档及附件中。
