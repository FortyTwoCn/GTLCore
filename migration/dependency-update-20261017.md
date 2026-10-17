# 1.21.1 前置更新

GTLCore 1.2.3.2-fix4 / GTLAdditions 3.2.8Custom-fix3 将旅行法杖冷却联动迁移到 Ender IO，并使用官方 AE2WTLib 19.5.1。

| 模组 | 当前用途及安装要求 |
| --- | --- |
| Ender IO | **可选**。针对 1.21.1 的 8.2.12-beta 验证；安装时沿用 `travelStaffCD` 配置，涵盖短距闪现、旅行锚传送和远程交互。不安装时不加载此 Mixin。 |
| Travel Anchors | 已移除前置和联动，无需安装旧的 GTL 移植版本。 |
| AE2WTLib | 必需，19.5.1 ≤ 版本 < 19.6。官方 JAR 自带匹配的 API，不要另放一份 API JAR。 |
| AE2 | 必需，至少 19.2.17；这是 AE2WTLib 19.5.1 自身的最低要求。 |
| Ad Astra | 必需，至少 1.16.26 的 **1.21.1 NeoForge** 发行版。Core 的星球、维度标记、基岩流体及机器结构，Additions 的结构和配方均引用其内容。 |
| Ad Astra Rocketed | Core 必需，`1.0.5-gtl.1` 原生构建。后期配方使用五、七级火箭，七种火箭燃料联动依赖其内容。 |
| SGJourney | Additions 必需，至少 0.6.49 的 **1.21.1 NeoForge** 发行版。星门、DHD、仪式、配方及多处 Mixin 使用它。 |
| MAE2 | Core 必需，`2.1.0-gtl.1` 原生构建。机器结构及后期研究/配方使用 `mae2:256x_crafting_accelerator`。 |

缺失这些必需内容时，将元数据改为可选只能掩盖加载器提示，随后仍会出现类缺失、配方或结构失效。Rocketed、MAE2 的 GTL 原生版本来自本仓库保留的源码适配，不可用同名 1.20.1 Forge JAR 代替。

Ender IO 8.2.12-beta 自带 EnderCore，若同时安装 FTB Ultimine，Ender IO 要求 Ultimine ≥ 2101.1.7。安装时需要遵守各模组自己的前置；本更新的构建配置也同步使用该 Ultimine 基线。

升级时替换旧 Core、Additions、AE2、AE2WTLib、Ultimine JAR，并移除旧 Travel Anchors。旧原生运行包如果带有独立的 `ae2wtlib_api-19.2.0.jar`，也要移除；19.5.1 主 JAR 已内嵌匹配的 API。

已核对的官方下载：[AE2 19.2.17](https://modrinth.com/mod/ae2/version/kfyIqgJ6)、[AE2WTLib 19.5.1](https://modrinth.com/mod/applied-energistics-2-wireless-terminals/version/CxSEpEnO)、[Ender IO 8.2.12-beta](https://modrinth.com/mod/enderio/version/2bHl1dCW)、[FTB Ultimine 2101.1.7](https://www.curseforge.com/minecraft/mc-mods/ftb-ultimine-forge/files/6846404)、[Ad Astra 1.16.26](https://modrinth.com/mod/ad-astra/version/9pnKHdCG)、[SGJourney 0.6.49](https://modrinth.com/mod/sgjourney/version/mZTmQ1wW)。均需选用 1.21.1 / NeoForge 文件。

构建不再替换为旧 AE2WTLib/Travel Anchors 源码版本。`dependencies/ae2wtlib`、`dependencies/travelanchors` 内的旧适配及其历史验证记录保留用于追溯，不再参与当前构建或源码准备。旧验证文档中的版本和结果仅对应当时的制品。

本次 Core 的 16 项 JUnit 实际执行通过，Core/Additions 构建及原生运行时检查通过。四个生产 JAR 在真实图形客户端中分别以不安装 Ender IO、安装 Ender IO 的组合进入世界，检查了无线终端升级槽、区域连锁挖掘及黑名单；Ender IO 闪现和旅行锚传送的冷却实际分别在 2/9、7 tick 到期。[验证记录及边界](dependency-update-20261017-evidence.json)保留了具体范围，未将这些结果视为整个整合包全部回归通过。

本次代码修改使用 OpenAI Codex 辅助完成。
