# 历史阶段记录

以下记录保存迁移过程中的阶段结果，不代表当前工作树状态。当前状态与运行入口见 README.md。

# Minecraft 1.21.1 NeoForge 移植状态

**状态：移植进行中。Core 完整 main 的 compileJava/assemble 已通过；Additions 完整 main 尚未编译通过。当前工作树仍不是可发布版本。**

Core 真实专用服务器已通过模组构造、注册和 common setup，并进入数据包加载；尚未成功加载世界。当前缺少匹配 skyblock fix3 的 KubeJS 注册内容（例如 `kubejs:force_field_glass`），已向用户询问原始整合包来源。禁止用空物品或删配方替代这些内容。

用户已明确取消 **1.20.1 存档兼容性**要求。验收面向新建的 1.21.1 世界，仍需验证本版本机器、库存、配置和世界数据的保存及重载。没有提供跨版本存档转换器。

## 固定源码基线

| 工程 | 基线 |
| --- | --- |
| GTLCore | [AaAdoniSsS/GTLCore，gtl-1431-skyblock](https://github.com/AaAdoniSsS/GTLCore/tree/gtl-1431-skyblock)，`5b6cd0ec6a9d2be99d9031404176e84b9cd342ea`，1.2.3.2-fix3 |
| GTLAdditions | [FortyTwoCn/GTLAdditions](https://github.com/FortyTwoCn/GTLAdditions)，`8caff5e93a5e65914d10dd176d48d66e7ec8c329` |
| GTCEu API | [FortyTwoCn/GregTech-Modern，1.21](https://github.com/FortyTwoCn/GregTech-Modern/tree/1.21)，`c72dc16b52795cbb0456b6e15e13cc4a85c0ed1b` |
| 原生 GTMThings | [FortyTwoCn/GTMThings，1.21.1](https://github.com/FortyTwoCn/GTMThings/tree/1.21.1)，`a3fb3a88d9ea060aa703c8b627949606e907f6ed` |

Core 使用用户补充的 skyblock fix3 源码；原始工作分支保留。GTLAdditions 原来固定引用的 1.20.1 Core JAR 已改为源码依赖。

## 构建基准

以下版本取自上述固定 GTCEu 源码，没有使用发行版 GTCEu JAR 代替该 fork：

| 组件 | 版本 |
| --- | --- |
| Minecraft / Java | 1.21.1 / 21 |
| Gradle / ModDevGradle | 8.14 / 2.0.141 |
| NeoForge | 21.1.248 |
| Parchment | 1.21.1，2024.11.17 |
| GTCEu / AE2 | 8.0.0 源码 / 19.2.8 |
| Registrate | MC1.21-1.3.0+67 |
| Configuration | 3.1.1-neoforge |
| ModularUI | 3.3.1-jei19.57-preview1 |
| JEI | 19.57.0.449 |
| Kotlin / KotlinForForge NeoForge | 2.0.21 / 5.7.0 |

ModularUI 必须按 GTCEu 仓库的 `patches/modularui/README.md` 从固定源码应用补丁后构建。该补丁的源码准备、测试及 Maven local 发布已经执行成功；固定 GTCEu 的 `compileJava` 也已通过。

`migration/dependencies/gtmthings` 是原生 GTMThings 的构建适配工程。它直接编译固定源码及资源，不修改 GTMThings 工作树，并使用同一 GTCEu 源码和修补后的 ModularUI。GTMThings 上游自己的 GameTest 和客户端验证报告不等于本工作树的运行验证。

`gradle.properties` 指定依赖源码路径与提交。GT 的只读参考目录是 `GregTech-1.21`，实际组合构建使用同一提交的 `GregTech-GTL-1.21` 工作树。固定参考的元数据声明了一个已经不存在的可选 `gtceu-ldlib.mixins.json`，同时加载原生 LDLib 时会导致 FML 中止。因此仅删除这 3 行失效声明，补丁和文件哈希记录在 `dependencies/gtceu/patch-manifest.json`；Java/API、有效 Mixin 和注入要求保持参考版本。准备脚本不会覆盖其他依赖修改。

Core/Additions 的 `verifyGtceuBaseline` 同时验证 HEAD、完整工作树 diff 与此唯一补丁、元数据及补丁哈希，拒绝额外源码修改。GTMThings 的 `verifyNativeSource` 检查其固定提交。更新基线后需要重新核对 API。

## 已落实的改动

- 两个目标工程采用 Java 21、ModDevGradle、原生 NeoForge 元数据、客户端/服务端/数据生成运行配置；不加载 `libs/` 中的旧 Forge JAR。
- Forge 事件、DeferredHolder、常用注册表和 RecipeOutput 的基础调用已开始迁移。主源码仍存在未迁移的 GTCEu 和 LDLib API，见审计报告。
- tick 回调按原始开始/结束阶段迁移为 NeoForge Pre/Post 事件；图计算预算在 Pre 重置、Post 执行，吞吐缓存及 JFR 统计在两阶段分别维护。GUI 打开入口使用原生 `Player.openMenu`，旧世界 ID 重映射已按最新范围删除。这些调用尚未随完整模组运行验证。
- 数据包目录改为 1.21 的单数命名；原版合成结果采用 `id`。8 个 OBJ 模型使用 NeoForge 21.1.248 实际注册的 `neoforge:obj`，其 `flip_v` 等参数由该版本源码确认。
- 9 个 Core 机器采用原生 API，并通过独立 API 编译：温度传感器、中子传感器、大型蒸汽仓、超大流体仓、中子加速器、方块总线、存储机器、等级外壳机器和高级装配线。无能量多方块基类使用原生 recipe trait 和 ModularUI。实际注册、全部 GUI 和其他子类仍需验证；另有 98 个构造函数只完成 BlockEntityCreationInfo 参数迁移，不能算作完整机器迁移。
- 159 个结构模板转为原生 MultiblockPatternBuilder 工厂，保持原始行数据、尺寸、重复范围和方向；旧、新 builder 的方向参数顺序不同，已明确转换。对固定重复段位于控制器前方的两个结构补上原生起点偏移。快照与几何校验不证明所有机器谓词绑定正确。
- 自定义结构匹配数据按位置记录等级和计数，失败分支可回滚，形成/拆解时复位。等级外壳、装配线重复段和存储能力通过 4 项服务端世界测试，覆盖混合等级失败、修复重形成、分支回滚、能力与 NBT 保存。
- Core 的 81 类配方和 Additions 的 26 类配方使用原生 queued entry 注册，保留 gtceu 命名空间、容量和类型 ID。默认配置下 Core 的可选原始虚空采矿类型关闭，实际绑定 80 类。声音、线圈候选、数据说明和覆盖层使用原生 API；独立客户端已验证 JEI 真实渲染与数量/概率显示；全部配方布局仍需验证。
- 物品保留原有 gtceu:long Ingredient ID；流体通过原生 gtlcore:long_fluid Ingredient 保留真实 long 数量。JSON、NBT、网络 codec、精确数量修改、原生槽位交易、并行输入/输出及重复库存预留通过测试。AE 缓冲槽直接使用 GenericStack 的 long 数量；long 配方接口仍有使用有界 MEStorage 库存的单元验证；新增真实 AE 网格、创造能源元件和存储盘验证了生产 MEIO 及分子装配器的实际插入/提取、加工输出和本版本实体重载。
- 切割机、电路组装机、等离子热交换、光刻阵列和拆解回调接到原生 recipe builder，保留电压阈值、派生 ID 和数量。原生 GT 已兼容两种焊锡倍数字段，回调直接使用其现有接口。回调所需 7 个材料直接复用生产定义，其原生流体 ID 已验证。
- 无穷磁盘及快速无穷磁盘的物品数据使用 vanilla CustomData；物品键采用 AE2 19 的注册表感知序列化。相关物品、库存和终端数量辅助类通过独立 API 编译。实际 AE2 网格和终端运行尚未验证。
- SavedData 使用原生 Factory 和 HolderLookup.Provider。库存按当前服务端取得世界数据；客户端 tooltip 只读取物品组件。损坏数据不会在读取时被自动改写成部分库存。
- Core 的 39 个无线 AE/JEI/图形相关消息已初步改为 CustomPacketPayload、StreamCodec 和 NeoForge PayloadRegistrar；Glodium 消息也已按原生接口调整。它们仍需随完整 Core 编译、启动和网络回归验证。
- 背包/嵌套容器使用原生实体和物品能力查找无线终端；Curios 使用 9.4.2 的 Optional API，并在依赖存在时才加载其 API 实现。终端采用 AE2 19 的实时槽位定位器、有效性检查和连接状态；三个无线终端物品、菜单宿主以及相关查找/打开服务通过独立 API 编译。
- AE2WTLib 终端注册迁移为 `AddTerminalEvent`，使用与 DeferredRegister 相同的物品实例和固定翻译键，避免回调顺序造成未绑定 holder 或错误翻译键。WUT 升级槽 Mixin 的类路径与终端定义参数已更新；其 API 编译通过，实际注入和终端切换 GUI 尚未验证。
- 对已发布原生 LDLib JAR 核对后，迁移了 9 个 Core、5 个 Additions 的唯一对应包路径，记录在 `migration/ldlib-package-moves.json`。能力、流体类型和 GUI 行为仍需逐项迁移。
- Additions 的机器增量/维度快照消息已迁移到原生 payload。3 个 codec 测试通过，覆盖字段、维度 ID、空快照、快照复制和非法数量。
- Additions 超级并行仓继承原生 ParallelHatchPartMachine，使 GTCEu 8 能发现它；保留 Int.MAX_VALUE 上限和共享行为，并通过 Kotlin API 编译。
- Additions 的 SGJourney 集成已迁移到原生 0.6.49；19 个通用和 2 个客户端 Mixin 通过独立 API 编译，7 项 NeoForge/FML 回归测试通过。独立客户端测试验证三个 DHD 菜单、经典环组装为 Pegasus、保存及重启后的世界重载，详见 `GTLAdditions/migration/README.md`。此结果不证明完整 Core/Additions 可启动。

- 真实 GT 机器注册使用原生 deferred builder 流程，恢复 GT 自身研究及 AE2 注册；23 个共用 GCYM 定义绑定同一原生 entry，保留独立 `gtceu:mega_blast_furnace`。24 个 Core GCYM 的结构预览、合金炉线圈、蒸馏塔 1/12 层必需输出及粉碎塔完整配方循环已在独立真实世界验证。
- 原生 IV 至 MAX 并行仓保留 Core 的 `4^(tier-2)` 上限及共享行为，使用原生输入限制和 GUI。GCYM 预览通过原生候选/数量选项补入显式 IO 仓，不改变形成限制或覆盖用户选择。线圈显示使用原生 ModularUI 服务端同步值；全部 GCYM GUI 尚未完成真实客户端验证。
- 简单燃料发电机、原生大型内燃机和燃气轮机的实际注册、完整结构、真实仓室和燃料循环已验证；化学引擎辅助流体只在接受的工作 tick 消耗，涡轮转子只执行一次原生磨损。无线模式使用真实 GTMThings 账户，验证等待/恢复及本版本保存重载。
- 独立真实客户端已验证 ModularUI 批处理、独立输入及锁定设置的 C2S 操作和 native SaveField 恢复。客户端记录各自保留当时源码哈希，不能用早期截图证明后续所有机器 GUI。128 位能量统计、十进制边界及 2,048 次 BigInteger 对照往返已通过验证。
- 生产主工程的 MachineEntry、原生模型方法、材料/机器字段归属及结构计数绑定继续迁移，变更清单在 `native-machine-model-moves.tsv`、`gtceu-field-moves.json`、`native-pattern-bindings.tsv`；这部分尚未随完整模组加载。
- AE2 处理样板使用 19.2.8 的原生编码和数据组件，保留稀疏槽位、输出顺序和物品组件；数量缩放使用精确 long 运算，拒绝非整除、溢出和原生单位上限之外的结果。电路及模具过滤、lore、NBT 与网络往返通过 8 项测试。
- 分子装配器控制器、recipe trait、输出队列和 Additions 无穷模式注入采用原生 trait 生命周期及 SaveField。12 项单元测试和 2 项真实世界测试覆盖组件键、long 数量、输出等待、接受的工作 tick、进度保存重载、结构破坏及修复；早期队列世界测试使用测试 IO；新增测试直接使用生产 MEIO 基类、108 槽样板仓和分子装配 IO，覆盖真实 AE 节点生命周期、配方服务发布、正常机器 tick 加工、存储盘输出及超大数量保存重载。原生菜单验证打开数据包中的槽位数量、两端槽位编号、第二页原生点击、物品组件及重命名/可见性 C2S handler；实际客户端渲染、共享工具弹窗、AE CPU 完整提交作业及多人连接仍待验证。
- Core 与原生 GT 的 ME 总线/流体仓使用同一 all-facing 规则；旋转使用新朝向参数，保存重建后恢复节点所有者及网络暴露面。原有 IModifiableSyncOffset 方法改为 getMEUpdateInterval/setMEUpdateInterval，避免与原生 MetaMachine.getOffset() 的 long tick 分散值冲突；15 个生产调用方及严格 Mixin 的变更记录在 native-ae-refresh-api.json。
- 普通及高级等离子发电的流体加速只在实际接受配方后消耗辅助流体；中子活化配方使用精确 MeV 预算及原生 recipe trait。相关行为已纳入局部回归，完整机器注册和客户端仍待验证。

- 普通 ME 样板仓、代理仓和库存样板仓使用原生 trait、SaveField、AE2 19 服务及 ModularUI。验证覆盖真实 AE 存储盘与控制器加工输出、电路 0、组件键、long 待处理库存、催化剂/缓存保存、代理重绑定、复制/剪切一次性转移，以及重叠配方需求的整体预留。普通样板仓另有实际客户端窗口、三列配置按钮、108 槽分页/滚动与催化剂弹窗的 C2S/S2C 验证；代理/库存样板仓客户端与 AE CPU 完整提交作业尚待验证。
- 虚拟供应机保留 288 个真实槽位，只向真实 AE 网格发布封装令牌，不消耗实物库存；移除库存会刷新原生 AE 缓存。原生数据组件保存、方块物品重放、实体重建及掉落已验证，没有用最多 256 槽的 vanilla CONTAINER 裁掉末尾槽位。虚拟输入编码直接使用原生 GTRecipe/RecipeHolder 的非消耗物品/流体和组件。
- 样板盒保留每页 36 槽、最多 360 槽，采用原生组件和固定槽位表分页；手持槽锁定、末页点击、本版本保存重载、配置调小后已分配页的保留及生产样板仓双向转移已验证。样板修改器改为每个物品保存独立参数，原生 GUI 在服务端校验数字；重复缩放保留物品组件和实际样板数量，拒绝溢出及容量超限。
- 结构写入器改为原生手持菜单与每件物品独立组件，导出 native MultiblockPatternBuilder；六方向代码编译及真实 3×2×4 非对称世界结构匹配已验证。样板生成器使用原生 RecipeHolder、组件键与 long 数量，配方筛选、冲突分析、原生菜单按钮及服务端生成已验证。
- 混合备货舱与混合输入舱保留 64 对物品/流体槽和四页布局，改为原生 trait、SaveField、ModularUI 与 AE2 19。备货舱使用虚拟网络引用和四种自动拉取模式；输入舱持有真实缓存，拒收余量保留，拆机只回收网络实际接收的数量，残余通过原生掉落物品组件恢复。真实存储盘、配方预留/消耗、网络拒收、配置复制及掉落验证已通过。
- 六个原生菜单已在连接集成服务器的真实客户端绘制，验证副手打开、屏幕范围、分页/旋转的原生输入事件 C2S 和第 64 槽库存 S2C。修正了背景缺失、文字重叠和缩放后超屏；日志、截图及独立源码哈希见 validation/native-recipes/item-menus-client。终端等级选择使用测试方块，本结果不等于完整生产注册或多人连接验证。
- 自定义材质渲染使用原生 LDLib IItemRendererProvider；特殊模型保护注入原生 TagPrefixItemModelGenerator。真实客户端验证注册的旋转材质物品、生产虚拟物品/流体/未绑定图标连续绘制 60 帧，截图见 validation/native-recipes/renderer-client。测试材质使用独立模型资源；此结果不证明 Avaritia cosmic/halo 加载器或全部生产材质已迁移。

- 原生 RecipeDB 搜索保留独立输入总线/ME 槽分组、共享流体及幽灵电路，并补充组件流体的公开索引扩展；Core 与 Additions 的搜索 Mixin 已在同一原生 FML 中组合验证。移除旧 GTRecipeLookup/RecipeIterator 注入目标，不修改参考 GT Java。Additions 的 7 个搜索调用方已按新公共接口调整，完整 MutableRecipesLogic 与其调用方尚未编译；多候选行为目前用原生 RecipeLogic 的接口契约验证，详见 native-recipe-search-api.json。
- GT 原生物品库存总线、流体输入/库存仓及 Core 标签过滤库存总线/仓已改为原生数量化 Ingredient、AE2 组件键、SaveField 和 ModularUI。覆盖重复配置去重、重叠需求预留、停止访问、重载后槽所有者恢复、拒收余量和真实掉落/再放置；虚拟库存不写入机器掉落。标签过滤保留 ExtendedAE 通配表达式、白/黑名单和数量排序；两个生产菜单与标签弹窗的实际客户端 C2S/S2C 证据在 validation/native-recipes/tag-stock-client。远程 ME chamber 的标签/电路/间隔编辑器仍需迁移。
- 可调自动维护、三级净化维护及重力仓继承原生维护部件基类，保留自动无故障行为、0.2–1.2 耗时倍率、原始取整规则和 0–100 重力设置。每个净化部件附加独立原生 provider trait，解绑只清除仍属于自己的提供者；法则类型包含无菌和普通类型。原生 GravityCondition 的 MapCodec 保留 reverse 与高度阈值，并通过 JSON/NBT/网络往返和轨道/空岛高度检查。6 项单元、3 项世界测试覆盖成型、配方修改、条件、菜单 C2S 与保存重建；客户端证据单独记录，见 native-maintenance-api.json。
- 机器共用 MachineUtil/MachineIO 使用原生 builder、tick IO 和 NeoForge FluidStack，临时辅助输入不污染配方路由缓存。真实世界验证组件输入、满仓输出拒收、电路 0 非消耗、精确耗能及非法预算；专用服务端工作线程取得服务端 RecipeManager。护甲检查直接核对注册 ID，避免缺失整套 KubeJS 物品时产生重复 map key。
- 样板快速上传服务使用原生多方块子结构状态和 RecipeHolder，保留原生 AE2 加工/合成/锻造/切石类型、long 缩放、组件键、代理配方和大型化学复制关系。真实 AE 网络验证目标发现、精确插入、编码者元数据去重、撤回一次、结构失效/恢复和拒绝加载远程区块。编码者信息采用原生 CustomData；选目标菜单已初步改为注册表感知 codec，屏幕滚轮使用 1.21 四参数回调。完整无线入口、选择菜单实际客户端与代表堆叠之外的标签候选匹配仍未验证。


- 原生 HPCA 部件保留计算、散热、耗能和损坏配置；真实通电控制器验证损坏保存、替换部件与结构修复。有限/无限光学计算机使用实际能量仓、每 tick 预算与主机关系，验证断电暂停、恢复与保存重建。模拟器的原生蓝图库存和虚拟控制器已通过世界测试，完整客户端三维预览仍待迁移。图作业 codec 验证完整组件键、精确延迟数量、暂停及重复库存所有权拒绝；图执行器完整作业路径未验证。
- 全部 30 个基岩流体定义接入原生注册与生产数据生成器，生成 JSON 与已移植生产资源逐项一致。原生注册、数量/耗尽设置、维度过滤、JSON/NBT 及世界数据重载有验证；没有验证实际星球维度中的钻井。基岩钻机使用原生成型/失效回调和九格下方监听，真实仓室验证输入门控与产物、方块变化、控制器重建及卸载后的监听解绑。
- 无穷/快速无穷磁盘的创造克隆注入原生 AbstractContainerMenu。实际 ChestMenu 验证独立 UUID 与库存、组件、超过 long 的精确 Int128 数量、本版本 SavedData 往返，以及生存模式拒绝克隆；完整 AE 终端显示仍未验证。
- Jade 保留原生普通产物逻辑，long 产物单独使用注册表感知堆叠 codec 和 long 数量；完整组件键分组、饱和合并与概率计算保持精确。单元测试和实际原生 provider 的世界测试通过，实际连接客户端的 tooltip 绘制仍未验证。
- 太空电梯、超时空装配线及各自模块已初步迁移构造、原生生命周期、模块连接、修改器和 ModularUI 同步显示；电梯通过 Ad Astra 的 ContentMenuProvider 发送行星菜单内容。四个控制器在全量 javac 中没有自身诊断，但完整注册、模块重载、GUI、菜单与网络仍未运行验证。维护仓/AE 仓模型注册已初步改用原生 hull/overlay API，新增两个维护模型；实际生产模型烘焙和指南关联仍待完成。


- 矿石/粉末处理器接入 GT 1.21 逐材料入口，保留原有综合处理/电动爆聚 ID、Core 矿石倍率、副产物和浸洗数量；原生后续洗矿/离心处理器仍执行。4 项单元测试和真实世界配方加载检查通过。参考 fork 的 tag/Ingredient 高炉 helper 错用熔炉 builder，通过 Core 的严格 VanillaRecipeHelperMixin 修正，3 个重载均验证独立 smelting/blasting ID、200/100 tick 和产物数量；参考 GT Java 不变。加入这些生产 Mixin 后加载 40,720 条配方，先前无此钩子的 43,224 条属于另一验证范围；不能把数量下降直接判断为内容丢失。完整 pack 的材料注册与所有实体机器配方运行仍待验证。
- 无线光学数据仓继承原生 OpticalDataHatchMachine，服务端数据组件绑定保留原数据棒其他信息，SaveField 保存对端坐标。实际 Data Bank、能量/维护/数据仓验证两种点击顺序、精确耗能、断电/恢复、跨维度拒绝、远程不加载区块、循环研究路径终止和重载；其原生 Jade provider 验证真实绑定坐标。完整研究数据棒与真实客户端 Jade 显示仍待联调。
- 雷电与末影水晶发电机直接附加原生 emitter trait，保留原有容量、电压、安培数、频率及过充。实际世界 tick 验证四分之一充能、水晶发电脉冲、来源移除、保存及实体重建后的订阅恢复；实际过充请求原生爆炸并移除机器，测试仅取消周边爆炸伤害以隔离其他 GameTest。完整生产模型与客户端表现仍未验证。
- 温室保留钢化玻璃封闭与实际天空光照门控。屠宰场使用原生实体战利品上下文及 AE 假玩家，保留满仓待处理掉落、玩家排除、刷牛数量和模式保存；实际菜单 C2S 校验及三项世界测试通过，两个机器的生产注册和实际客户端窗口仍待验证。
- 多方块预览改用原生 IBlockPattern、MultiPredicate、机器方块实体和稀疏几何，在 ModularUI 中嵌入 LDLib 场景。单元验证覆盖 48 种朝向/镜像、空气过滤、可变蒸馏塔及自定义外壳候选；真实客户端验证原生高炉/蒸馏塔、翻页、全屏/拖动/恢复、GT 的实际 JEI 类别布局和精确材料数量；严格生产 JEI 注入比较全部 172 个类别、31,320 条展示配方的 ID，并验证资源包重载后的重建。世界预览使用 MeshData、原生层筛选和生命周期，验证自定义玻璃外壳、过期清理。7 张截图、日志及独立输入哈希见 validation/native-recipes/preview-client；完整 Core 注册、模块/模拟器预览、可选 EMI/REI 与多人连接尚未验证。


- 结构检测器使用原生 IInteractionItem，并保留副手物品与其他组件。检测为每个子结构建立独立原生 PatternState，恢复共享模板的重复段设置；三项世界测试覆盖 48 种朝向/镜像、实际错误位置及不修改已形成结构的缓存。Glodium 高亮消息采用实际原生 buffer codec，保存维度、不可变坐标和 15 秒截止时间；真实客户端收包高亮仍待验证。
- 多方块更新由原生索引和生命周期处理，撤下重复的旧 Level/MultiblockState 注入。世界测试验证线圈 ACTIVE 与染色更新保持成型、IO 仓实际替换导致失效、修复后自动重新形成与绑定，以及控制器移除后的索引清理。失败检测会保留原生位置索引用于监听修复；它不等同于已经形成的结构。
- 机器模式选择改为原生 ModularUI 列表、同步弹窗与服务端动作，校验模式范围、真实主机身份及玩家距离；严格注入原生 GTMuiWidgets 按钮入口。实际客户端验证七种配方类型、原生滚轮访问第七项、点击 C2S、选中状态 S2C 和保存重建后的恢复，三张截图与独立源码哈希见 validation/native-recipes/recipe-mode-client。批处理配置已由原生 RecipeLogic trait 附加，撤下重复的失效旧注入。
- AE 配置侧栏在原生面板构建阶段分列，每列最多四项，保留全部原生控件、弹窗和同步 handler。普通样板仓客户端验证九项控件、三列、可见性与重命名 C2S/S2C；27/108 槽主面板、原生标题、侧栏和弹窗均检查缩放后的屏幕边界。默认模式页仍含 72 个物理槽位，可视区域显示三行并使用原生滚轮；样板盒显式配置的每页 36 槽与四行布局保留。两页末尾槽位的中键催化剂弹窗与样板数据已验证，计数居中显示。
- 原生 GT 的通用对象数组转换器无法序列化 cacheRecipe 的 boolean[]；该 Core 字段通过公开字段接口绑定独立原生转换器，原地更新 final 数组并拒绝无效长度、截断或非法标记。四项单元测试覆盖 648 个标记与原子拒绝，一项世界测试通过实际 SyncDataHolder 验证标记及其他字段不丢失。仅对 Core 模式槽应用严格客户端绘制注入，恢复原有样板产物预览，同时保留真实编码样板交易与中键行为；实际金锭绘制有客户端证据。七张截图、日志与当时源码哈希见 validation/native-recipes/configurator-columns-client，完整注册、通配样板仓和多人连接仍待验证。
- 通用机器 tick 通过严格 WrapOperation 包裹原生 trait 执行，保留 ManagedSyncBlockEntity 的保存与脏字段同步步骤；运行节奏交给原生区块 tick，撤下旧 SERVER_TICK 属性及墙钟抑制循环。五项新增世界测试验证取消/启动延期后的原生同步与本版本 SaveField、AE/普通机器独立数量预算、执行时间预算、关键机器豁免与真实区块 tick 恢复。超限计算阵列使用启动关键标记，完整控制器本身仍待验证。
- 线剪与软锤入口改为 ExtendedUseOnContext。真实已注册工具验证批处理切换、软锤完成一个当前配方周期后暂停、重复设置拒绝和普通点击恢复。LDLib 实际异步服务通过原生 markAsChanged 入口排队到服务端线程，已移除或替换的主机不会收到延期变更。
- 性能监视器改为原生 ModularUI 和服务端拥有的列表同步，保留相同耗时的不同机器，按 40 个世界 tick 刷新，计算实际采样数的平均微秒耗时。三项单元测试覆盖原生 buffer/list codec、不可变位置和非法耗时；世界测试覆盖实际 native tick 采样。真实客户端通过 12 行受控服务端数据的 S2C、同耗时排序、刷新/释放、滚轮末行按钮与 ExtendedAE/Glodium 维度/坐标/15 秒截止时间验证；长名称单行截断，保留完整耗时及悬浮信息。三张截图、日志及该运行的独立输入哈希见 validation/native-recipes/performance-client；尚未验证完整注册、完整控制器或物理多人连接。
- 鸿蒙之眼使用原生 SaveField、成型/失效回调、配方修改器与 GTMThings 无线账户。两项世界测试验证实际放置者与数据棒绑定、物理氢/氦转入 long 储量、精确整数启动能量、纯搜索、缺少输入/能量/速率/输出空间时不扣启动资源，以及成功周期只扣一次。原生服务器菜单序列化和超过 double 精确整数范围的储量保存已验证；原始大型结构、完整注册及实际客户端仍待验收。
- 戴森球使用原生保存字段、结构回调、EnergyStack/CWU 修改器和服务端 ModularUI 显示。三项世界测试验证接收器/实际光照门控、发射上限和维修、断电恢复、夜晚订阅启动/停止、真实光纤连接到计算提供器、准确 EU/CWU 消耗、输出堵塞时不消耗计算资源或推进损伤，以及周期标记保存和重复失效只减少一个球体。保持原生等待时进度回退规则；输出功率聚合使用饱和运算。验证使用缩小结构及测试用外部接收器外壳，原始大型结构、完整注册和客户端未验证。

## 原生依赖

已通过 Modrinth 的 Minecraft/loader 元数据及实际 JAR 元数据核对并固定的依赖包括 LDLib、Ad Astra、ExtendedAE、Glodium、GuideME、ME Requester、AE2WTLib，以及 Additions 的 SGJourney。Ad Astra 的 ResourcefulLib、ResourcefulConfig 和 Common Storage Lib 已显式加入。

AE2 的 Maven POM 会带入 1.20.1 Forge GuideME；目标工程及构建适配工程排除了它，并采用 NeoForge GuideME 21.1.19。AE2WTLib 固定 19.2.0，其实际要求允许 AE2 19.2.8；较新的 AE2WTLib 不能不核对依赖直接升级。原生实现包含的 `ae2wtlib_api` 另以 `de.mari_023:ae2wtlib_api:19.2.0` compileOnly 提供编译 API；运行时由其已有嵌套 JAR 提供。API 对照源码为 [19.2.0 的固定提交](https://github.com/Mari023/AE2WirelessTerminalLibrary/tree/e62cbf9e409f060d6f164987559237b0eae9434c/ae2wtlib_api)。

实际专用服务端验证发现已发布 AE2WTLib 19.2.0 的构造函数会解析客户端配置界面类，导致服务端加载失败。`dependencies/ae2wtlib` 改为组合编译上述同一固定提交的完整主模组和 API，仅应用 `client-registration.patch` 将配置界面、菜单屏幕监听器及客户端 tick 隔离到客户端入口。补丁哈希见 `patch-manifest.json`；构建拒绝额外源码修改。两个目标模组的原制品坐标都映射到此源码构建，保留全部资源、有效 Mixin 和无线终端功能，API 继续嵌套打包。修补后的主模组/API 已与 ExtendedAE 一同通过专用测试服务器及客户端加载；Core 无线终端的完整操作仍待验证。

`verifyNativeRuntime` 扫描**已发布的外部**运行依赖，拒绝旧 Forge 元数据 JAR 和重复 mod ID。源码组合工程由其固定基线和原生构建配置核对。此检查不证明所有必需 mod 已齐备，也不检查实际 Mixin 注入。

仍未解决的外部集成：

- MAE2 已从固定 Forge 源码 `48538ec812580bd5abdbcda4c6e0d3ad79e0ff16` 完整移植到 NeoForge/AE2 19，保留 256x accelerator、云室、多重 P2P、设置卡和资源。Travel Anchors 已从 `f2731ad8e5f66b1accdfc0a91bedfd88091c1657` 移植到原生 LibX 6。两者完整编译并通过 Core 专用服务器模组构造；机器/部件实际操作、客户端和新世界保存重载仍待验证。补丁、162/63 个源码与资源文件哈希及许可证记录在 `dependencies/{mae2,travelanchors}/patch-manifest.json`。干净工作树复现与意外修改拒绝已验证。
- FTB Ultimine 2101.1.1 已由固定的完整原生源码接入；真实形状注册和保护规则有服务端世界测试，完整玩家挖掘、客户端形状与可选 ranks 运行仍待验证。
- Re:Avaritia 已固定原生 NeoForge 1.4.2 制品；50 个生产 cosmic/halo 模型已实际加载并绘制，独立证据见 validation/native-recipes/avaritia-client。完整生产材质、护甲与外部 KubeJS 遮罩仍待验证。

## 通配符样板总成与物料返还

普通与库存型通配符总成使用原生 AE2 19 处理样板、组件键、long 数量、GT recipe trait、SaveField 和 ModularUI。保留原有两种机器 ID、电路零号的可复用语义、动态样板及全部产物；库存型共用普通库存总成的事务预留与失败回滚，额外 AE 输入不会被重复配置或重叠谓词重复使用。缓存重载分别核对样板输入/输出与完整配方快照，允许额外备货和可复用催化剂。

返还队列逐段保存物料，多个同键槽位的总量可以超过 Long.MAX_VALUE。四项新增单元测试和四项实际世界测试覆盖部分转入、组件保存、拒绝损坏账本、恢复前更换样板、真实 AE 存储盘部分接收，以及实际拆机与 BlockItem 放置。机器掉落只保存真实输入及待输出/返还物料；拆机时的样板清空回调不会再次把这些物料送回网络。

六张通配符客户端截图与独立源码哈希位于 validation/native-recipes/wildcard-client/，验证两种生产菜单、136 种实际材料的服务端轮播、只读物品/流体预览、完整 long 流体悬停提示、可见性/重命名 C2S/S2C、四页库存配置、最后一格组件流体、配置重载与刷新间隔。在无窗口管理器的测试显示器中，悬停通过原版 MouseHandler 回调驱动，并在实际控件悬停的渲染帧捕获。该客户端运行先于新增返还队列；其当时源码哈希独立保留，新增返还行为由后续世界测试验证。

通配符外部依赖仍未完成。[LeoDreamer2004/Wildcard-Pattern 的 gtl 分支](https://github.com/LeoDreamer2004/Wildcard-Pattern/tree/gtl)所检查提交 a7e0820ea3fbc251fcbc89aa1061c4f5151a862e 仍是 Forge 1.20.1。Core 新增原生协议适配器，按其 in/out/filter 数据解码五种组件，并随资源保留 MIT 许可；测试仅提供同 ID 的原生样板物品与原始模型。外部编辑器、外部模组及原始生产注册入口仍未验证，不能将局部测试作为通配符依赖已完成移植的证据。

## 当前验证结果

| 检查 | 结果与边界 |
| --- | --- |
| 固定 GTCEu compileJava | 通过；未在本任务中复验其完整运行 |
| 原生 GTMThings API 编译 | 通过；使用固定 GTCEu 与修补后 ModularUI |
| Core 机器、AE 存储、无线终端及 WUT Mixin API 编译 | 通过；只验证指定生产类，未验证全部 Core 或实际 Mixin 注入 |
| Additions 超级并行仓 Kotlin API 编译 | 通过；未验证实际多方块注册和 GUI |
| Core 持久化 JUnit | 14 个通过：保存、组件、AE 键、网络键和 long 物品 Ingredient codec |
| Core/Additions 配方与机器 JUnit | 465 个通过；使用选定生产类、真实 GT/NeoForge 注册与严格 Mixin，详见 validation/native-recipes/manifest.json |
| 原生专用服务端 GameTest | 227 个通过；覆盖 HPCA、模拟器、光学计算机、基岩流体/钻机、磁盘克隆、Jade、材料配方、无线数据仓、环境发电机、温室、屠宰场、通用机器 tick、工具、性能监视器、鸿蒙之眼、戴森球及两种通配符样板总成，加载 40,791 条配方（包含受控测试材料配方）并保存新世界；未加载完整 Core/Additions |
| 多方块预览客户端 | 通过；7 张截图及实际 ModularUI 输入、GT JEI 布局、世界预览/层筛选/过期/自定义外壳；不证明全部 Core 机器或可选 EMI/REI 可运行 |
| 机器模式/AE 配置客户端 | 通过；分别保存三张与七张截图及独立源码哈希，覆盖原生鼠标/滚轮、C2S/S2C、末尾槽位、产物绘制及本版本保存恢复；未验证完整注册或多人连接 |
| 性能监视器客户端 | 通过；三张截图、实际 S2C 列表刷新与释放、滚轮、末行点击、正确维度/坐标与 15 秒高亮截止时间；受控计时数据用于 GUI，实际采样另有世界测试 |
| 通配符总成客户端 | 通过；六张截图、实际轮播、精确 long 悬停、两种菜单及配置同步；外部编辑器和完整机器注册未验证 |
| 原生基岩流体生产数据生成 | 30 个 JSON 逐项匹配生产资源；不证明星球维度与钻井运行 |
| Additions SGJourney JUnit / 客户端 | 7 个通过；三个菜单、Pegasus 组装、新世界及保存重载有独立证据 |
| Additions payload JUnit | 3 个通过；不等于客户端/服务端收发验证 |
| 外部依赖 Forge JAR / 重复 mod ID 检查 | 两个工程通过 |
| 资源及静态注册字符串盘点 | Core 1340、Additions 551 个资源；575 + 133 个基线静态注册字符串没有丢失 |
| 主 Java 语法解析 | 1,234 个 Java 文件解析通过；不等于语义编译 |
| Core 全量 compileJava / assemble | **通过**：完整 main，日志 gtlcore-native-assemble-3.log/4.log；不等于客户端或世界加载成功 |
| Additions 全量 compileKotlin | **失败**：Core 依赖编译已通过；上次完整 Kotlin 检查报告 1,948 项诊断、138 个文件（gtladditions-full-native-4.log），主要为机器 trait、GUI、配方和 builder 语义 |
| 完整 Core/Additions 启动及世界 | **未通过**：Core 已进入数据包加载，缺少对应整合包 KubeJS 内容；Additions 主工程仍未构建。上述独立测试模组的世界结果不等于整包结果 |
| GUI、配方、AE2 网格、多方块和集成回归 | **未完成** |

API 编译和 JUnit 使用明确的独立验证源集/工程，发行 JAR 仍只使用完整 `main`。没有删除功能源码、把主工程替换成测试入口，或降低 Mixin 注入要求来获得绿色构建。

## 复现

在移植工作树旁准备固定依赖源码目录：

```sh
git clone --branch 1.21 https://github.com/FortyTwoCn/GregTech-Modern.git GregTech-1.21
git -C GregTech-1.21 checkout c72dc16b52795cbb0456b6e15e13cc4a85c0ed1b
git clone --branch 1.21.1 https://github.com/FortyTwoCn/GTMThings.git GTMThings-1.21
git -C GTMThings-1.21 checkout a3fb3a88d9ea060aa703c8b627949606e907f6ed
```

使用完整 JDK 21，在 GregTech 源码根目录执行：

```sh
python scripts/build_modularui.py
./gradlew compileJava
```

在 Core 根目录创建带唯一元数据补丁的 GT 工作树，再运行局部验证：

```sh
python migration/dependencies/gtceu/prepare.py
git clone https://github.com/Mari023/AE2WirelessTerminalLibrary.git ../AE2WTLib-1.21
git -C ../AE2WTLib-1.21 checkout e62cbf9e409f060d6f164987559237b0eae9434c
python migration/dependencies/ae2wtlib/prepare.py
python migration/dependencies/native_source.py prepare --manifest migration/dependencies/mae2/patch-manifest.json --source ../MAE2-GTL-1.21
python migration/dependencies/native_source.py prepare --manifest migration/dependencies/travelanchors/patch-manifest.json --source ../Travel-Anchors-GTL-1.21
./gradlew assemble :mae2-native:assemble :travelanchors-native:assemble verifyNativeRuntime migrationRegression
./gradlew :gtmthings-native:compileJava
./gradlew compileNativeMachineApiJava migrationRegression verifyNativeRuntime
./gradlew -p migration/recipe-tests test
./gradlew -p migration/recipe-tests verifyNativeWorld
./gradlew -p migration/recipe-tests verifyNativeWorldgenData
./gradlew -p migration/recipe-tests -PwildcardValidation=true verifyNativeWildcardClient
python migration/archive_native_recipes.py --gradle-log /absolute/path/to/successful-combined-test-world-data.log
./gradlew -p migration/recipe-tests -PmodeValidation=true verifyNativeRecipeModeClient
./gradlew -p migration/recipe-tests -PcolumnsValidation=true verifyNativeConfiguratorColumnsClient
./gradlew -p migration/recipe-tests -PwildcardValidation=true verifyNativeWildcardClient
python migration/archive_native_recipes.py --gradle-log /absolute/path/to/successful-combined-test-world-data.log --performance-client-gradle-log /absolute/path/to/successful-performance-client.log
# 只有当次实际重跑的客户端才传相应选项；既有客户端证据保留原有独立源码哈希。
./gradlew -p migration/recipe-tests -PwildcardValidation=true verifyNativeWildcardClient
python migration/archive_native_recipes.py --gradle-log /absolute/path/to/successful-combined-test-world-data.log --configurator-columns-client-gradle-log /absolute/path/to/successful-columns-client.log
./gradlew -p migration/recipe-tests -PpreviewValidation=true verifyNativePreviewClient
./gradlew -p migration/recipe-tests -PwildcardValidation=true verifyNativeWildcardClient
python migration/archive_native_recipes.py --gradle-log /absolute/path/to/successful-combined-test-world-data.log --preview-client-gradle-log /absolute/path/to/successful-preview-client.log
./gradlew -p migration/recipe-tests -PrendererValidation=true verifyNativeRendererClient
./gradlew -p migration/recipe-tests -PtagStockValidation=true verifyNativeTagStockClient
./gradlew -p migration/recipe-tests -PmaintenanceValidation=true verifyNativeMaintenanceClient
python migration/audit.py --baseline 5b6cd0ec6a9d2be99d9031404176e84b9cd342ea --output migration/audit-report.json
```

生产验收命令仍必须通过：

```sh
./gradlew build
./gradlew runData
./gradlew runServer
./gradlew runClient
```

**目前不能把局部验证成功写成上述生产命令成功。** 本次工作区的详细日志在 `/workspace/cloud-setup/`，测试 XML 位于 `migration/tests/build/test-results/test/`。日志和测试构建目录不进入发行 JAR。配方测试工程也直接编译 8 个已迁移的 Additions Kotlin 生产文件，未加载两个完整目标模组。

## 后续必须完成的工作

1. 全量迁移 MachineEntry、注册 builder、原生机器构造、recipe trait 和能力注册；核对实际注册 ID 及高阶内容配置。
2. 完成模板与实际机器注册、谓词绑定、子结构和预览的联调；现有模板及匹配数据迁移只覆盖部分机器生命周期。
3. 迁移其余 LDLib GUI 到 ModularUI，覆盖 AE 库存槽、催化剂、过滤器、分页、终端、物品和流体输入，并验证 C2S 操作。
4. 核对所有 Mixin 的原生目标、描述符、字段和注入次数，迁移客户端渲染、模型加载、事件和专用服务端隔离。
5. 迁移配方/Ingredient/Data Components、动态数据包、JEI/EMI/KubeJS 和第三方依赖 API，解决 MAE2 等原生依赖缺口。
6. 完成 Additions 的完整 Java/Kotlin 编译，检查其修改 Core/GTCEu 定义的生命周期和处理顺序。
7. 在实际客户端及专用服务端创建新世界，逐项测试机器工作、配方产物、多方块形成/拆解、AE2 网格、GUI/网络同步及本版本保存重载。

`audit.py` 只对明确的数据目录、原版合成结果和 OBJ loader 迁移做归一化；其他资源丢失或内容变化会失败。报告中的未迁移 API、LDLib 和 NBT 调用是待核对清单，不会自动令内容盘点失败。静态字符串和文件数量均不能证明实际注册数量、配方可用性或功能完整性。

联合范围还迁移并验证 GT/GTMThings 三种原生 ME 输出部件，覆盖精确 long 组件输出、重复键容量预留、真实控制器配方、回流周期及实际拆装恢复；三个客户端菜单另有 9 张截图和独立源码哈希。最新全量 Core 编译为 950 项诊断，仍阻塞完整 Additions。

共享物品/流体催化剂已改用原生组件键和独立 long 需求匹配，实际控制器配方、重载及清空缓存通过验证。研磨球仓采用原生工作回调、保存字段和 ModularUI，世界测试验证实际 40 点伤害、创造模式豁免、菜单绑定与闲置/工作中拆机掉落规则。

## 超级样板仓与 FOA 模式

Additions 超级样板仓与代理使用原生 GT block entity、SaveField/SyncToClient、AE2 19 组件样板和 ModularUI。保留默认 162 槽及可配置的 1 至 1800 槽容量、页码和物理槽位索引；单一滚动视口修复横纵滚动后的裁剪和点击。原有三条特殊配方按当前原生 RecipeManager 查询，匹配完整组件和 Ingredient 谓词；额外循环容器、非循环产物倍率、1 至 30 倍限制及精确 long 运算保留。数据棒使用独立且带维度的原生组件标记，普通/超级绑定互不混用，研究数据和其他组件不丢失。

新增五项 JUnit 与四项世界测试验证实际 AE 网格发布、控制器消耗与一次性产物交付、原生菜单 C2S、保存重载、原始特殊规则的更新，以及仅含代理的控制器通过原生 RecipeHelper 完成配方。三个客户端菜单和九张截图验证 162/1800/1 槽布局、FOA 和页码双向同步、两个滚动方向、末格组件样板产物预览与催化剂中键弹窗。各运行日志和独立输入哈希见 Core 的 validation/native-recipes/super-pattern-client/。验证采用独立注册入口，完整 FOA 控制器、模块、原始生产注册和物理多人连接仍未验证。两个主工程尚未完成全量构建与启动。

### 原生标签过滤与传输覆盖板

物品/流体标签过滤器改用 GT 1.21 的统一 TagFilter，保留 Core 的无命名空间表达式、布尔运算和错误拒绝规则，缓存、组件保存及 GUI 继续由原生 GT 负责。实际仓口能力验证纯模拟、组件传输、条件修改后缓存失效及保存重载。

无限水覆盖板保留原生安装、每 20 tick 供水及订阅生命周期，供水量仍为 Integer.MAX_VALUE。输送带、机械臂、泵和流体调节器接入原生构造流程，保留十五个电压等级的 Core 速率；ULV 调节器保留 long 预算，通过多次原生 int 能力调用传输超过 300 亿 mB、十四种组件流体，无预算刷新重复产出。八个生产盖板定义保留原 gtceu 注册路径，改为原生 Holder 延迟注册及延迟客户端 renderer 工厂。

新增 3 项 JUnit、7 项世界测试通过，涵盖真实大仓口、订阅移除/重载及设置恢复。原始 Core 盖板物品注册、盖板客户端渲染与物理多人连接仍待完整主工程联调。证据日志为 gtl-native-cover-transfer-full-validation-3.log；该轮 395/176/30 结果已归档，旧客户端证据保持各自运行时的独立源码哈希。最新两个主工程构建仍失败，Core 为 830 项诊断，Additions 受 Core 编译阻塞。


## 原生无限流体钻机

Core 无限钻机接入原生附加 RecipeLogic、基岩流体 Holder、真实能量仓及 long 流体 Ingredient，保留 256 倍产量、最多高一个电压等级和 50% 超频增产。共享计算先用 long 乘法再除以剩余次数，消除 int 溢出；Core 不消耗矿脉次数。原生 GT 钻机通过严格注入保留自身耗电与消耗矿脉生命周期，配方产量支持 long，其原生 int 显示接口采用饱和值。

完成后暂停、满输出等待、恢复交付及运行中保存重载经过实际控制器验证；两种暂停标记均由原生 SaveField 保存，等待输出不会额外耗电、丢弃产物或提前消耗矿脉。3 项新增 JUnit 与 7 项世界测试通过，联合验证为 398/183/30。三种实际客户端菜单验证鼠标启停 C2S/S2C、只完成一轮及准确耗电、精确 long 服务端显示和实体重载。九张截图、日志和独立源码哈希见 Core 的 validation/native-recipes/fluid-rig-client；紧凑原生滚动列表使标题栏、侧栏和背包在 854×480、GUI 缩放 2 下完整显示。

测试使用独立注册入口、受控矿脉及缩小结构；原始生产注册、完整结构、星球生成及物理多人连接仍待整模组联调。Core 全量编译仍有 950 项诊断，完整 Additions 仍受其阻塞；两个局部原生 API、回归及外部运行依赖检查通过。

## 高级无限钻机与原生工具等级

高级无限钻机使用原生附着 recipe trait、SaveField、条件 tick 订阅和 ModularUI，保留七种线圈的温度上限、中子素/振金钻头速率、5×5 区块查询及原始温度/电压/超频公式。long 流体和 EU 精确缩放，启动前检查全部 20 tick 能量；完成后暂停、满仓等待和运行中重载不会丢弃产物或重复扣电。四种冷却液按原顺序消耗 20,000 mB，烈焰按原公式消耗；实际世界 tick 验证热量订阅拆解/恢复、温度传感器及 300 tick 熔毁。原生 GT 工具标签数组扩展到等级 10，保留原有 0–6 级 ID，实际工具组件验证低等级与等级 10 的掉落资格。

新增 6 项 JUnit、9 项世界测试通过；联合结果为 404/192/30，日志 gtl-native-advanced-fluid-rig-full-validation-8.log。七张真实客户端截图及独立哈希位于 validation/native-recipes/advanced-fluid-rig-client，验证高速开关双向同步、带组件钻头的真实鼠标取放、准确一轮耗电、只读热量/long 产量、原生滚动与长文字换行，以及重载后重开菜单。测试采用独立注册 ID、简化结构和受控的稀疏材料定义；完整生产材料、原始结构、原始注册及物理多人连接仍未验证。两个主工程仍未完成全量构建；本轮 Core 全量错误从 950 降至 917，Additions 受 Core 阻塞。

## CWU 并行与碎岩机流体条件原生迁移

CWU capability 实现原生 getMaxParallelByInput，预览使用实际光学提供器的模拟预算，按提供器身份去重并使用 long 聚合；不分配 CWU 或扣电。实际光学网络世界测试验证原生 ParallelLogic 调用正确限制并行。碎岩机条件迁移到原生 AdjacentFluidCondition：原始 fluidA/fluidB 数据每次解析，形成的多方块检查所有输入槽位，排除输出仓且不消耗环境流体；普通机器保留原始水平邻接规则，显式原生条件保留其空间语义与 codec，反向条件只应用一次。

新增 4 项 JUnit、3 项世界测试通过，联合结果 408/195/30，日志 gtl-native-cwu-rock-full-validation-4.log。证据已归档为 935 个输入文件、237 项证据；此前客户端证据保留各自运行时的源码哈希。本轮两个工程的指定原生 API 检查通过；Core 全量诊断为 909 项，Additions 仍受 Core 编译阻塞，完整模组启动尚未通过。

## 裂变反应堆原生热量与配方生命周期

生产控制器改用原生保存/同步字段、结构回调及 RecipeModifier。燃料与冷却单元计数、邻接关系来自实际接受的原生谓词位置；配方预览不扣输入/电力或写运行热量，接受配方时才保存有效并行和 FRheat。保留原有冷却需求、邻接升温、冷却加速、825K 钠钾输出切换、空闲散热/损伤维修和过热爆炸规则，使用 long 中间量及精确流体 Ingredient。冷却交换先检查完整输出与输入，避免输出堵塞扣掉冷却剂；零输出不会扣水，红石输出限制在 0..15。

付费配方在产物堵塞时保留完整进度和完成后暂停，恢复时只交付一次。仓口卸载使热量/配方订阅停止，重载重新绑定原生结构与当前实体并保留已付费周期。4 项新增 JUnit、7 项新增世界测试通过，联合结果 412/202/30，日志 gtl-native-fission-full-validation-7.log。归档含 941 个输入文件、238 项证据；旧客户端证据仍保持其运行时的独立源码哈希。验证使用真实 GT 能量/物品/流体仓口、简化结构及稀疏冷却材料；原始完整结构、生产材料注册和裂变 GUI 客户端仍未验证。


## AE2 吞吐量监控原生迁移

生产监控部件改用 AE2 19 的物品交互、RegistryFriendlyByteBuf、注册表感知 NBT 和原生顶点接口，保留统计周期、插入/提取中较大方向的显示规则及原纹理路径。真实 AE 存储 Mixin 保留手动合成库存预留，嵌套监听去重和 CompositeStorage 拓扑更新通过原生接口验证。GT MetaMachine 已直接继承 BlockEntity，来源解析优先保留机器朝向，避免被通用实体分支吞掉。

3 项新增 JUnit、3 项新增世界测试通过。玩家实际配置监控项后，在通电 AE 电缆主机、真实驱动器和 GT 来源节点中验证模拟不记账、组件键、精确交易、来源维度/坐标/朝向、采样、设置及部件/主机重载。联合结果 415/205/30，日志 gtl-native-throughput-full-validation-5.log；归档含 952 个输入文件、239 项证据，旧客户端证据保持独立源码哈希。测试使用独立部件与配置物品注册；完整生产注册、无线终端流量和客户端趋势图标仍未验证。

GT 原有机器/激光仓口配方加载器已初步采用原生 MachineEntry 和 GTCEu.Mods 接口，保留配方 ID、数量及高等级未启用时的空入口检查；完整生产配方加载尚未验证。最新完整 Core 构建仍有 855 项诊断，Additions 被 Core 编译阻塞，日志分别为 gtlcore-full-native-throughput.log 和 gtladditions-full-native-throughput.log。


## 仓室终端注册表感知快照

生产分块传输改用 RegistryFriendlyByteBuf，保留 128 KiB 分块与 16 MiB 完整快照上限。发送端先完成全部编码；接收端在全部块按顺序收到、成功解码且没有剩余字节后才发布菜单内容。目录图标、文本、AE 物品/流体键及 long 数量采用 1.21 原生 codec，原消息 ID 保留。无线快照消息修正原生 IPayloadContext 分发；普通机器远程菜单交互初步采用原生物品/无物品两阶段接口及原生触及距离。

5 项新增 JUnit 验证组件键、空堆叠、带样式的翻译文本、Long.MAX_VALUE 数量、跨块重组、乱序/缺块/版本冲突、未完成快照替换、越界/截断/尾随数据及失败后恢复。联合 420/205/30 检查通过，日志 gtl-native-chamber-snapshot-full-validation.log；归档含 954 个输入文件、240 项证据。完整仓室菜单、无线消息注册及客户端实际网络收发仍未验证；专项 codec 通过不代表整个无线系统已完成移植。


## 2026-10-04 原生 ExtendedAE、无限单元箱子与消音仓

原始 ExtendedAE 样板修改器的 item、host、menu、screen 和已注册原生数据包直接使用于验证。Core 范围修改、整数倍率上限、精确大数替换、强制插入/删除、交换、名称/物理数量及组件持久化已迁移；非结构元数据复制不会恢复旧样板配方。实际客户端从明确范围初始状态操作范围倍率、翻页、中键弹窗、键入 Long.MAX_VALUE 和替换动作，通过真实 C2S/S2C，并检查输出与名称保留。4 张截图与独立源码哈希见 validation/native-recipes/extendedae-client；完整合成样板替换和物理多人连接仍未验证。

AE2 已删除的 ICellGuiHandler 入口改为严格注入原生 MEChestBlockEntity.openGui：两种 Core 无限单元进入原生存储菜单，普通单元保持原生菜单及类型过滤能力。世界测试使用实际服务器玩家和原生网络包接收器，未声称箱子客户端界面已验收。消音仓严格注入原生 modifyRecipe，保留原生前方空气与恢复规则，向所有实际绑定控制器同步堵塞原因并处理脱离控制器的状态；真实合金高炉结构验证堵塞、解除与脱离。

旧 LDLib 1 库存能力包装器在原生 LDLib 2 中已不存在，能力直接走 NeoForge；原有 long 存储适配保留。服务器停止阶段的查找保护移到 GTCapabilityHelper 原生入口，正常能力查找已随世界测试执行，真实停止阶段尚未单独验证。Ad Astra 火箭按原生 Common Storage Lib 的 FluidResource/Predicate 迁移 7 种燃料与 16000 mB 容量/耗量。编译使用原始嵌套 API JAR，外层制品、3 个内层 JAR 哈希与真实方法签名见 native-adastra-api.json；完整火箭材质注册、启动和耗油未验证。

本轮联合结果为 437 项 JUnit、216 项世界测试及 30 项世界生成检查，日志 gtl-native-extended-chest-full-validation-22.log。归档包含 1418 个输入文件和 287 项证据；此前客户端证据保留各自源码哈希。研磨球掉落测试已改用覆盖实际放置范围的 32³ 模板，并只统计本次产生的实体，避免相邻场景干扰。主工程仍未编译或整包启动成功；专项验证不代表完整迁移完成。

## 2026-10-04：探矿器、无线能源与创造能源

原生探矿器保留负坐标路标、打开时区块锚点和只清除自动路标的行为，实际普通玩家通过 FTB/Architectury 数据包传送；4 张截图及独立源码哈希见 validation/native-recipes/prospector-client。原生配方能源显示改用任意精度整数计算总量和 CWU 数量；单元验证已通过，配方能源行的实际 JEI 绘制尚未验证。

中子加速器实现原生无线接收接口，实际封面只按接受的电量扣账户，停机、同 tick 拒收、速率、容量、订阅及新版本保存重载已验证。监控器使用原生共享传输快照，保留团队/全部范围、带样式的统计与维度/位置高亮，并限制行数、防止超大数溢出；视口尺寸、内容滚动和标题已适配当前 ModularUI。

两种原生创造仓保留无限输入行为，并使用原生 recipe trait、SaveField 和菜单。真实客户端验证完整等级列表、精确奇数 long 与 Long.MAX_VALUE 输入、零安培、加号 C2S/S2C 及重载，监控器验证实际范围切换、滚轮和高亮。11 张截图及独立源码哈希见 validation/native-recipes/energy-client。完整控制器绑定、全部无线接收等级和物理多人连接尚未验证。

联合回归通过 447 项 JUnit、221 项世界测试及 30 项原生世界生成检查，日志 gtl-native-wireless-creative-energy-full-validation-2.log；归档包含 1444 个输入文件、312 项证据。主工程完整构建和整包启动仍未通过，以上均为明确范围的验证。

## 2026-10-04：原生多方块重叠数量限制

旧 TraceabilityPredicate 注入迁移到原生 MultiPredicate。只处理根级、无子逻辑的 OR 谓词：所有匹配的有数量限制分支按原生身份计数，无限制分支只用于未匹配的位置，最大数量错误不会被普通外壳分支绕过。原生 AND/XOR、嵌套逻辑和最小数量规则保留。

4 项新增 JUnit 检查全局/层计数、优先级、单次测试、层检查开关及 AND/XOR；2 项真实 BlockPattern 世界测试检查重复层回退、失败计数/缓存回滚、超量拒绝及修复后重新检测。联合 451/223/30 通过，日志 gtl-native-limited-predicate-full-validation-2.log。完整生产结构注册仍未完成。

## 2026-10-04：原生聚变热量事务

聚变修改器改为纯函数，启动热量由原生 RecipeLogic 的实际输入处理预留并提交，失败及异常退款。需要恢复热量时先匹配 tick 内容，再包住原生 tick IO；原生散热、仓口、渲染与配方周期保留。能源/热量文本采用原生只读同步行，客户端绘制尚未验证。

4 项新增 JUnit 验证原生修改器重复求值、输入失败/异常退款、只付热量差、最大整数与异常数据；2 项世界测试构建原生注册 LuV 完整聚变环并验证真实仓口、输出堵塞、一次付费加工、SaveField 重载及冷却后再次付费。联合 455/225/30 通过，日志 gtl-native-fusion-full-validation-1.log。Core 高级聚变控制器的完整迁移和物理多人连接仍待完成。

## 2026-10-04：原生大型锅炉燃料与加载生命周期

温度燃烧比例采用精确分数，节流只由原生 LargeBoilerRecipeLogic 计算一次，时长和已燃烧进度使用有界整数。原生服务端节流入口限制 25..100，加载时恢复高温蒸汽订阅并重算节流派生值。

4 项新增 JUnit 验证原生修改器、分数燃料及整数边界；2 项世界测试构建原生完整青铜锅炉，使用 LargeBoilerLogic 的真实煤配方验证一次燃烧、节流切换及非法值约束，再通过正常服务器 tick 验证热态蒸汽、SaveField 重载与水汽比例。热态由受控 fixture 设置，完整冷启动升温、全部锅炉等级及物理 GUI 尚未验证。联合 459/227/30 通过，日志 gtl-native-boiler-full-validation-3.log。

## 2026-10-04：完整 Core 构建与真实加载链

完整生产 `main` 已完成 NeoForge 语义编译和 assemble。真实 runServer 通过 mod construction、注册和 common setup；到达数据包加载后因缺少对应 KubeJS 内容中止，日志 `gtlcore-full-native-server-13.log`。Gradle 的 runServer 即使在 Minecraft 加载失败时也可能返回成功，因此验收必须检查 `Done`、实际世界数据与保存重载，而不是只看 Gradle 返回值。

本轮修正原生 tick/parallel、线缆配方、StorageAccounting、车辆流体容量、配置/tooltip/menu buffer 及 LDLib 模型 Mixin。原生 AE 记忆卡用 `gtlcore:auto_expand` DataComponent 保存设置，无线终端回调与 DeferredRegister 共用同一个延迟实例。ME Requester 固定兼容 AE2 19.2.8 的原生 1.2.0 (`E46AplqZ`)；1.3 及以上要求更高的 AE2/WTLib，不符合固定基准。

`preflight/NativeMixinTargets.java` 只读检查实际原生 classpath：328 个可找到目标无字段/方法签名诊断，仍有 6 个可选目标缺失待核对（AE2 crafting tree、FTB JEI）。该检查不验证客户端运行、常量命中数、局部变量捕获或 Mixin 间交互；这些最新修改尚未包含在此前 465 项联合测试的冻结证据内。
