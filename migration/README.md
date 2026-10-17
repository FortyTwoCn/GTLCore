# GTLCore / GTLAdditions 原生 NeoForge 迁移

2026-10-17 前置更新：当前版本使用 AE2 19.2.17 / AE2WTLib 19.5.1，Travel Anchors 联动已迁移到可选的 Ender IO。当前依赖用途、原生版本和安装要求见 [前置更新说明](dependency-update-20261017.md)。下文的历史验收数据保留其原始版本，不能视为本次制品的验收结果。

状态：完整主源码已原生移植、构建并完成本文列出的本地验收。最终生产 JAR 在普通 NeoForge 安装器服务器中启动、保存 24 个维度并从磁盘重载；物理客户端完成菜单、指南公式、资源重载和无线终端检查。完整世界、148 个原始结构、独立 TCP 交互及关键付费加工另有明确证据。已知 JVM 崩溃的根因仍未确认，覆盖边界与诊断参数须一并阅读；这些记录不证明所有配方、并发玩家或长期负载均已验证。

最终证据：[独立构建](validation/fresh-build-final/evidence.json)、[完整世界及可选集成](validation/full-production-world/final-optional-integrations/evidence.json)、[独立联机与实际重启](validation/full-production-dedicated/wireless-rocket-elevator-bloom/evidence.json)、[普通安装器服务器](validation/packaged-server/packaged-server-evidence.json)、[普通安装器客户端](validation/packaged-client/evidence.json)。运行闭包共 35 个顶层 JAR，生产构建不包含测试模组。最后两处显示文本修正后，2,470 个生产类的字节码与完整运行验收版本逐项相同。

用户已取消 1.20.1 存档兼容性要求。仍需保证新建 1.21.1 世界中的库存、机器、配置及世界数据保存和重载；不提供跨版本存档转换器。

## 固定源码与开发环境

| 工程 | 固定基线 |
| --- | --- |
| Core | [AaAdoniSsS/GTLCore，gtl-1431-skyblock](https://github.com/AaAdoniSsS/GTLCore/tree/gtl-1431-skyblock)，`5b6cd0ec6a9d2be99d9031404176e84b9cd342ea`，1.2.3.2-fix3 |
| Additions | [FortyTwoCn/GTLAdditions](https://github.com/FortyTwoCn/GTLAdditions)，`8caff5e93a5e65914d10dd176d48d66e7ec8c329`，3.2.8Custom-fix2 |
| GTCEu API / Gradle 基准 | [FortyTwoCn/GregTech-Modern，1.21](https://github.com/FortyTwoCn/GregTech-Modern/tree/1.21)，`c72dc16b52795cbb0456b6e15e13cc4a85c0ed1b` |
| GTMThings | [FortyTwoCn/GTMThings，1.21.1](https://github.com/FortyTwoCn/GTMThings/tree/1.21.1)，`a3fb3a88d9ea060aa703c8b627949606e907f6ed` |

Java 21、Gradle 8.14、ModDevGradle 2.0.141、NeoForge 21.1.248、Parchment 1.21.1/2024.11.17、Registrate MC1.21-1.3.0+67、Configuration 3.1.1、ModularUI 3.3.1-jei19.57-preview1、AE2 19.2.8、JEI 19.57.0.449、Kotlin 2.0.21、KotlinForForge 5.7.0。版本来自固定 GTCEu 参考；不加载旧 Forge JAR。

`gradle.properties` 指定相邻依赖源码路径和提交。先按 GTCEu 的 `patches/modularui/README.md` 构建固定补丁版 ModularUI 并发布到 Maven local，再执行组合构建。只读 API 参考为 `GregTech-1.21`；`GregTech-GTL-1.21` 使用同一提交，仅删除原参考元数据中不存在的可选 LDLib Mixin 声明（3 行）。没有修改 GT 的 Java API 或放宽严格注入。补丁、许可证、完整源码与资源哈希位于 `dependencies/`；准备脚本拒绝覆盖其他修改。

从迁移后的 Core checkout 可运行 `python3 migration/prepare_native_workspace.py prepare --build-modularui`，按属性和清单取得 11 个固定源码依赖、校验/应用记录的补丁，再调用固定 GT 参考自带的 ModularUI 单元测试和 Maven-local 发布脚本。已有 checkout 的提交或源码不符会报错并保留文件；不执行 reset。`python3 migration/prepare_native_workspace.py verify` 只校验，不访问网络、不编译或改动源码。该入口已在当前工作树及全新目录通过全部 11 个源码依赖的克隆、准备和哈希校验；全新目录没有复制编译输出，最终完整构建已通过并单独归档。Git 的对象 ID 缩写长度会随对象数量改变，校验只归一化 diff 的 index 对象头，完整补丁正文、固定提交和源码哈希仍须一致。

MAE2、Travel Anchors、AE2WTLib、Ad Astra Rocketed 和 GTMThings 由固定源码原生构建。AE2WTLib 的补丁隔离服务端构造时的客户端注册；Rocketed 保留原有三个等级的火箭、实体、渲染器和资源。`verifyNativeRuntime` 拒绝已发布依赖中的 Forge 元数据和重复 mod ID；这个静态检查不代替实际 FML/Mixin/世界运行。

## 整合包内容与可复现入口

用户提供的 [GregTech Leisure](https://www.curseforge.com/minecraft/modpacks/gregtech-leisure) 已固定到文件 8903963 / 1.4.5.2。原始下载 SHA256、注册脚本、适配和 CC BY-NC-SA 4.0 许可证见 [pack-reference](pack-reference/gregtech-leisure-1.4.5.2/README.md)。该整合包自带 Core 1.2.3.1-fix9，与本次 skyblock fix3 基线不同；不能据此声称完整整合包版本一致。

生产验收环境使用两个真实 KubeJS 注册脚本及 1,037 个原始资产，包含力场玻璃、线圈和研究物品。原始脚本单独保留，原生脚本适配 KubeJS 2101、标签和物品组件。没有用空物品、占位方块或删除所需配方绕过缺少内容。完整整合包的服务端配方、任务及其他脚本不在此验证配置中。

```sh
# 在 GTLCore 中准备独立运行目录；不会覆盖不匹配的已有文件。
python3 migration/pack-reference/prepare_native_profile.py \
  --archive /path/to/GregTech-Leisure-1.4.5.2.zip \
  --game-directory /path/to/native-profile --include-configs

# Java 21；两个模组分别执行。
./gradlew assemble verifyGtceuBaseline verifyNativeRuntime --no-daemon

# 局部生产类的原生回归（独立注册，不代替完整模组）。
./gradlew -p migration/recipe-tests test verifyNativeWorld --no-daemon

# 在 GTLAdditions 中执行完整生产模组世界验证。
./gradlew verifyNativeProductionWorld -Pnative_production_tests=true \
  -Pkotlin.compiler.execution.strategy=in-process --no-daemon

# 先用上面的脚本准备 GTLAdditions/migration/full-client，
# --seed-world 指向已在该组合中验证过的新 1.21.1 世界。
# 客户端需要正常图形环境和 JEI。
./gradlew verifyNativeProductionClient -Pnative_production_tests=true \
  -Pkotlin.compiler.execution.strategy=in-process --no-daemon
```

独立网络验证使用 `./gradlew verifyNativeProductionDedicated -Pnative_production_tests=true -Pkotlin.compiler.execution.strategy=in-process --no-daemon`。先分别准备 `GTLAdditions/migration/full-dedicated/server` 和 `client` 的源哈希验证配置，再为服务器加入原始太空电梯运行配方：

```sh
python3 migration/pack-reference/prepare_native_machine_recipes.py \
  --archive /path/to/GregTech-Leisure-1.4.5.2.zip \
  --game-directory ../GTLAdditions/migration/full-dedicated/server
```

此附加脚本只提取整合包 `misc.js:75-79` 的太空电梯配方，保留 400 tick、128 CWUt、UV 电压及电路 1，记录来源和输出哈希；其他整合包服务端配方仍不加载。独立验证入口先完成全部 ModDevGradle 编译和运行准备，再启动两个 JVM，通过真实 TCP 连接；完成后正常保存退出并从磁盘重启再次验证。端口 25576 仅绑定本机，离线模式仅用于隔离的开发验证目录，脚本不会覆盖其他服务器配置。内存有限时可先运行 `prepareNativeProductionDedicated`，退出 Gradle 后直接执行 `python3 migration/run_native_dedicated.py --launch build/native-dedicated/launch.json`。

完整世界验证也需要在 `GTLAdditions/migration/full-gametest` 准备真实注册脚本、资产和配置。测试模组只有显式开启 `native_production_tests` 才加入运行，不进入发布 JAR。测试任务要求明确的完成标记，Minecraft 进程返回 0 本身不算通过。每次只运行一个 Gradle，避免共享源码组合构建发生竞争。

## 当前已取得的运行证据

2026-10-05 的独立阶段记录：

| 验证 | 已证实的范围 |
| --- | --- |
| 完整主源码 | Core 和 Additions 的全部主源码原生 assemble/check 与最终运行闭包检查通过，最终制品和源码输入哈希单独归档 |
| 联合专用服务器 | 完整开发环境和标准 NeoForge 安装器均已启动并正常保存新世界。安装器使用普通发布 JAR、保存 24 个维度，并用第二个 JVM 重载实际磁盘世界；各验证配置的配方数量不同 |
| 完整机器注册 | Core 163 个（含两个原生 Wildcard 部件）、Additions 88 个自有实现的实际工厂；每种创建独立 trait、默认 NBT 往返、世界 BE 替换；分别 149 / 82 个服务端菜单，控制器绑定后才允许打开的部件单独标记 |
| 火箭 | 七个生产等级：实际材料燃料过滤、16,000 mB 容量和发射消耗、纯模拟、物品组件、发射台放置实体及 NBT 重载 |
| 原生合成 | 五张实际 Avaritia 9×9 配方，正确的 Native serializer、Tier 4 匹配/合成；拒绝低等级和缺少输入 |
| JEI | 完整生产配方注册；实际公开运行 API 的物品/流体收藏、去重和移除通过，没有依赖已删除的私有反射入口 |
| 完整生产客户端 | 第 36 轮：231 个独立菜单、148 个原始成型控制器及 24 类真实绑定部件菜单通过正常方块使用 C2S 打开和绘制，6 个线圈纹理菜单通过原生 ResourceLocation 同步；8 次维护仓点击、资源重载、602 个原始 Additions 翻译键和 26 类原生配方标签复验通过。没有 ModularUI SIZING、滚动内边距或缺失字段 Codec 错误 |
| 原始生产结构 | 第 25 轮完整生产世界：148 个实体多方块的最小原始预览结构实际放置、成型、部件绑定、拆除、修复及原生控制器掉落通过；虚拟模拟器单独验证不成型。另覆盖 3 个定义的 9 个较大重复页，包含各自最大页；14 项 required GameTest 全部通过 |
| 蒸汽真实加工 | 第 23 / 25 轮：原始蒸汽活塞锤结构、真实输入/输出仓及注册的圆石到砂砾配方，3 并行、原生服务端订阅 tick、输出堵塞拒绝启动；引擎暂停与控制器 NBT 重载恢复、超频控制变更后保持已启动配方的费用、输入/蒸汽/产物精确且只交付一次。暂停检查针对引擎 SUSPEND，工作开关按原生语义完成当前周期 |
| 原生星门连接 | 第 25 轮：完整 Classic 原始结构通过实际方块交互组装，真实星门网络模拟/连接、连接 NBT 恢复和关闭；携带命名组件的真实物品实体通过原生 Wormhole API 从主世界传到下界。此记录不证明玩家穿过门面或受限维度旅行 |
| 星阵压缩缓冲 | 第 19 轮：真实原始结构与输出仓、33 个物品实体的材料接收、立即标记区块保存、堵塞时保留一次概率结果、停止、原生 NBT 重载、实际控制器掉落组件及再次放置恢复、解堵后精确交付且不重复通过 |
| 原生 Wildcard Pattern | 完整依赖已移植并接入；五种序列化、组件和物品原生网络往返、实际供能 AE2/通配符仓配方展开、主输出索引及移除通过。第 22 轮实际编辑器的物品使用、精确 long 滚轮、C2S 保存、动态槽、小屏显示、重开、三类过滤器及六行滚动通过 |
| 独立客户端与专用服务器 | 第 8 / 9 轮：两个独立 JVM 的实际 TCP 连接，Infinity 初始/增量菜单、真实维护与 Distinct 点击、服务端精确数量及组件；原生 AE 物品/流体配置输入 Long.MAX_VALUE 和滚轮减一的精确 C2S/S2C、组件/库存和 JEI 悬停；正常保存后重启服务端与客户端验证实际磁盘恢复 |
| 联机实体组件与受限维度 | 第 12 轮独立服务器/客户端及实际磁盘重启：修复 GTCEu 原生 SpoilContext 流解码在物理客户端访问空 MinecraftServer 导致掉落物包断线；真实实体数据包的冻结计时、处理器元数据和超过 2^53 的 long 精确同步，服务端仍解析真实世界。真实连接玩家的非法直接进入 Lantea 被拦截，原生星门传送许可被消耗，客户端实际切换维度并保留物品组件。调用原生 Wormhole 接收 API，未验证步行穿过门面 |
| 原始配方 ID 与查找 | 第 41 轮完整世界：虚拟原料供给机保持原始 `gtlcore:virtual_ingredient_supply_machine`，真实 GT 查找库可找到它，没有错误的 assembler 副本；原生父配方重建及派生回调正常，14 项必需 GameTest 全部通过 |
| 可选 EMC 集成 | 不安装可选模组、只安装 ProjectE、同时安装 ProjectE/AppliedE 三种完整生产世界配置均通过。ExtendedAE 在 AppliedE 存在时注册 EMC 接口，战利品条件检查实际物品是否存在；真实接口只掉落一个物品。实际 AE2 CPU 的临时 EMC 样板在完成和取消时均从 AppliedE KnowledgeService 清理 |
| 新目录源码引导 | 准备脚本取得并校验 11 个固定源码依赖，按固定 GT 脚本构建补丁 ModularUI，其 13 项原有 JUnit 全部通过。新 Core/Additions 源码目录的 assemble/check 已通过；复用下载和 NeoForm 缓存，关闭 Gradle build cache，最终资源更新和两个工程的 check 也通过 |
| 局部联合回归 | 最新 514 项 JUnit 与 237 项实际 GameTest 全部通过；新增恒定耗时超频、动态无线库存的吞吐追踪及网格切换/断开验证，并验证实际自定义高阶线圈的纹理 Codec 往返和菜单构建。Mixin 明确覆盖原生机器的两个构造函数。它使用选定生产类和独立注册，不能作为完整生产结构或多人验证 |

机器模型直接从原生 MachineBuilder / MultiblockMachineBuilder 的最终模型生产器生成，覆盖 Core 扩展后的朝向和自定义模型；不替换为统一立方体。完整客户端第 18 轮和启用原生 Wildcard 的第 19 轮已通过；记录见 [第 18 轮证据](validation/full-production-client/evidence.json)、[第 19 轮证据](validation/full-production-client/wildcard-native/evidence.json)。完整生产世界第 16 轮的结构、非默认库存及外部集成证据见 [结构与世界证据](validation/full-production-world/structures-native/evidence.json)。此次修复包括动态渲染器的原生公共机器类型、部分仓口的完整原生染色外壳模型，以及聊天组件 Codec 返回 JSON 字符串时的 GUI 同步相等性判断。客户端遍历验证独立/未成型菜单；第 16 轮世界测试另行验证原始最小结构。两者不证明全部实际加工或结构最大变体通过。

## 后续验收与覆盖边界

- 多个并发客户端的观察者同步；有些部件原本不提供 GUI，另有代理需要真实链接后才允许打开。
- 原始结构更多重复组合、更多真实付费配方执行、模块绑定与输出堵塞状态。
- AE2/GTCEu 更多联合路径、完整星球地表着陆及星门门面穿越。
- 上述最终构建和普通安装器验收已完成；其余条目是当前证据未覆盖的范围，不应标记为已通过。

无穷仓非默认状态的配方分组缓存已修复，并在完整世界第 12 / 16 / 19 轮复验区分模式、染色、共享催化剂、超过 2^53 的精确数量和 Native SyncDataHolder。外部 Wildcard Pattern 已从固定 MIT 源码完整原生构建，补丁与 53 个源码/资源哈希在 `dependencies/wildcardpattern`；该依赖保持可选。ExtendedAE 的 EMC 战利品使用原生 `item_exists` 条件数据包，三个实际可选配置已通过。星阵压缩持久输入和待输出缓冲已通过完整世界检查，见 [第 19 轮证据](validation/full-production-world/compression-owned/evidence.json)；最新加载器与世界回归见 [514 项 JUnit / 237 项 GameTest 记录](validation/native-regressions-514/evidence.json)。独立客户端与专用服务器的真实连接、原生点击、AE 精确大整数与磁盘重启已通过，见 [独立菜单记录](validation/full-production-dedicated/native-menus/evidence.json) 和 [AE 精确记录](validation/full-production-dedicated/ae-exact/evidence.json)；客户端编辑器和布局复验见 [第 22 轮记录](validation/full-production-client/editor-reopen/evidence.json)。

实际蒸汽付费加工与较大结构页见 [第 23 轮记录](validation/full-production-world/paid-steam-variants/evidence.json)；完整生产星门网络和物品跨维度见 [第 25 轮记录](validation/full-production-world/stargate-native/evidence.json)。

独立第 12 轮的实体数据包、受限维度玩家传送与实际磁盘重启见 [联机与传送记录](validation/full-production-dedicated/protected-travel-spoilage/evidence.json)。兼容修复位于 Core 的严格 Mixin 中，固定 GT 参考的 Java 源码保持不变；客户端的空世界引用与原生磁盘 Codec 行为一致，腐败逻辑仍由服务端执行。

早期诊断、单项移植细节和独立客户端证据边界保存在 [historical-notes.md](historical-notes.md)。其中“主工程尚未编译”“无法加载世界”等描述属于历史阶段，不能代表当前工作树。

最新成型界面及翻译记录见 [完整客户端第 36 轮](validation/full-production-client/formed-localized/evidence.json)，配方 ID 与真实查找记录见 [完整世界第 41 轮](validation/full-production-world/native-recipe-ids/evidence.json)。可选配置见 [ProjectE 单独存在](validation/full-production-world/projecte-only/evidence.json)、[实际 AppliedE 生命周期](validation/full-production-world/appliede-lifecycle/evidence.json) 和 [可选模组缺席](validation/full-production-world/optional-absent/evidence.json)。新目录依赖引导见 [bootstrap 记录](validation/fresh-bootstrap/evidence.json)。

发布闭包必须保留官方 KotlinForForge `all` 库聚合 JAR，以及单独的 `kfflang`、`kfflib`、`kffmod`。前两种运行库没有模组 ID，不能按“没有 ID”丢弃；NeoForge 安装器已提供的 FML loader/earlydisplay 则不能复制进 mods。AE2WTLib 源码构建的嵌入 API 使用原生发布坐标 `de.mari_023:ae2wtlib_api`，与 ExtendedAE 的共享依赖身份一致，交给 NeoForge JarJar 选择。

Additions 的指南公式渲染需要 JLaTeXMath 的 Greek/Cyrillic 字体库，两者显式嵌入发布 JAR；不能依赖开发类路径提供字体。标准安装器客户端的启动准备入口为 Additions 的 `migration/prepare_packaged_client.py`，从普通安装器生成的 NeoForge/vanilla 版本元数据解析并校验官方库，不使用 ModDevGradle 游戏输出或验证模组。当前独立 JVM 验证使用 Temurin 21.0.8+9；此前 21.0.12.1 的一次 C2 编译器崩溃记录保留，未将该次运行记为通过。

开启 Bloom 的实际运行太空电梯暴露了固定 GT 基线的缓冲生命周期问题：空网格构建后，已结束的 BufferBuilder 仍留在区块缓存，下一次渲染抛出 `Not building!`。修复放在 Core 客户端 Mixin 中，释放空网格缓存、替换已结束的缓冲，在原生读写锁内完成四边形写入和烘焙，并序列化同一区块的并发编译；GT Java 源码不改动。专用客户端增加了真实原生 BufferBuilder 生命周期检查，并保留 Bloom 开启验证实际工作的电梯支架。第 33 轮的首次运行及实际磁盘重启均通过，见 [完整独立联机记录](validation/full-production-dedicated/wireless-rocket-elevator-bloom/evidence.json)。

第 33 轮还通过三个真实持有、链接及供能的原生无线终端；第一个终端验证真实 AE 网格插入/提取产生的组件吞吐数据。生存且非 OP 玩家通过原生物品使用、实体交互和空格键发射七级火箭，实际扣除 16,000 mB 燃料，通过原生星球选择包到达月球着陆器；原始结构和运行配方的太空电梯支付计算与 EU 后，经真实 GUI 按钮和星球选择包到达火星着陆器。命名货物组件、许可消耗和所有检查均在磁盘重启后重复通过。此记录使用下述明确开启的 JIT 诊断参数，不能代替默认 JVM 的稳定性验收。

太空电梯付费测试使用原始控制器和计算提供者结构、真实输入仓及光学管路。供电夹具将有限的能量预算转入真实能量仓，检查原始电压与计算请求扣费；预算有上限，未修改生产配方费用或伪造工作状态。该夹具不证明外部电池与能量线缆的完整供电路径。

真实无线终端打开路径在 Temurin 21.0.12.1 和 21.0.8+9 均出现过 `SIGSEGV`，崩溃帧为 Minecraft `InventoryChangeTrigger` 的 C2 编译 lambda；仅排除 `test` 后，崩溃还出现在它的转发函数。根因尚未确认。专用验证可显式增加 `-Pnative_inventory_trigger_jit_workaround=true`，对 `InventoryChangeTrigger*::*` 使用 `exclude` 和 `dontinline`，仍执行原始成就判定和全部真实菜单操作；默认不启用。运行证据必须注明是否使用此参数，不能把此配置的通过视为无参数环境稳定性证明。

联机夹具还需处理实际保存的原生着陆器：Ad Astra 只允许落地后解除乘骑，直接移动仍在空中的乘客会使其与远处着陆器的可 tick 区域分离。夹具等待真实碰撞落地再准备玩家，未删除保存的车辆或强行解除原生乘骑条件；旧的超时轮次不记为通过。火箭/电梯旅程检查真实星球选择包、维度切换、原生着陆器和货物组件，不将到达着陆器等同于已完成完整的星球地表着陆操作。

在 Additions 中执行 `./gradlew assemble check exportNativeRuntime --no-daemon` 后，可用 `python3 migration/stage_native_distribution.py --runtime build/distribution/native-runtime.json --directory /path/to/neo-server` 分发生产 JAR。该脚本检查嵌入依赖身份、SHA256、原生元数据和测试类，保留并拒绝覆盖不同的已有文件。标准安装器运行的验证脚本为 `migration/run_packaged_server.py`，需要已安装 NeoForge 21.1.248、真实注册/资产配置及已接受 EULA 的专用测试目录。源码目录的客户端/服务端通过记录和发布 JAR 的通过记录分别保留。
