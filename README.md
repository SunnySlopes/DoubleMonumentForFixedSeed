# Multi Monument Finder
# 固定种子多连海底神殿搜索工具

A GUI program for searching **double / triple / quadruple ocean monuments** in Minecraft Java Edition fixed seeds. Supports Minecraft Java Edition **1.18.2** through **26.3+** (the version picker labels the newest profile **26.2+**, i.e. 26.2 and later — 26.3+ uses the same worldgen).

用于在 Minecraft Java 版固定种子中搜索**二连 / 三连 / 四连海底神殿**的图形界面工具。支持 **1.18.2** 至 **26.3+**（版本下拉里最新档位标为 **26.2+**，表示 26.2 及以后；26.3+ 的世界生成与 26.2 相同）。

Search uses **cubiomes** via a native library (JNI): monument positions and **deep-ocean biome viability** (`isViableStructurePos` for monuments). Results may differ from older versions that used custom climate noise thresholds.

搜索通过 **cubiomes** 原生库（JNI）实现：计算神殿位置并判定**深海群系是否可行**（`isViableStructurePos`）。与旧版基于自定义气候噪声阈值的实现相比，结果可能略有不同。

The UI supports **Chinese / English** switching (single-seed tab). Default language follows the system locale.

界面支持**中 / 英**切换（单种子搜索 Tab）。默认语言随系统 Locale 自动选择。

---

## Requirements / 环境要求

- **Java 17+**
- **Windows / Linux / macOS** (native library built per platform)  
  **Windows / Linux / macOS**（需按平台编译原生库）
- To build the native library: **CMake**, **Ninja**, **MinGW gcc/g++** (e.g. MSYS2 `ucrt64`)  
  编译原生库需要：**CMake**、**Ninja**、**MinGW gcc/g++**（如 MSYS2 `ucrt64`）

---

## Build from source / 从源码构建

```bash
git submodule update --init --recursive
./gradlew buildNative MultiMonumentFinderJar
```

Output / 输出：`build/libs/MultiMonumentFinder-1.1.jar`

On Windows with MSYS2, ensure `C:\msys64\ucrt64\bin` (or `mingw64\bin`) is on `PATH` when running Gradle.

Windows + MSYS2 环境下，运行 Gradle 前请将 `C:\msys64\ucrt64\bin`（或 `mingw64\bin`）加入 `PATH`。

---

## Run / 运行

```bash
java -jar build/libs/MultiMonumentFinder-1.1.jar
```

Or use `run.bat` after building.  
或构建后使用 `run.bat`。

The JAR embeds native libraries under `/native/{windows|linux|macos}/`. You can also run with:

JAR 内嵌原生库，路径为 `/native/{windows|linux|macos}/`。也可指定库路径运行：

```bash
java -Djava.library.path=build/native -jar build/libs/MultiMonumentFinder-1.1.jar
```

---

## JNI smoke test / JNI 冒烟测试

```bash
./gradlew buildNative compileTestJava
java -Djava.library.path=build/native -cp "build/classes/java/main;build/classes/java/test" project.MonumentNativeSmokeTest
```

Optional args / 可选参数：`seed minX maxX minZ maxZ pairLevel biomeCheck minGroupArea dualAfk`
(e.g. `... project.MonumentNativeSmokeTest 123 -2 2 -2 2 3 true 6728 true` for a dual-dummy triple search; pass `0` for the built-in default threshold; the test prints CSV columns `x1,z1,x2,z2,area_dual,area_single,area_max,gate_x,gate_z,sx,sz,count,dx,dz,dual`).
例如：`... 123 -2 2 -2 2 3 true 6728 true` 表示三连双假人搜索（阈值传 0 用内置默认；测试输出 CSV 列 `x1,z1,x2,z2,area_dual,area_single,area_max,gate_x,gate_z,sx,sz,count,dx,dz,dual`）。

---

## How to Use / 使用方法

### Common controls / 通用按钮

| 中文         | English                 | Description / 说明                                    |
|------------|-------------------------|-----------------------------------------------------|
| **开始搜索**   | **Start Search**        | Start search / 开始搜索                                 |
| **暂停**     | **Pause**               | Pause search / 暂停搜索                                 |
| **继续**     | **Resume**              | Resume paused search / 继续已暂停的搜索                     |
| **停止**     | **Stop**                | Stop search / 停止搜索                                  |
| **重置搜索区域** | **Reset Search Area**   | Reset MinX/MaxX/MinZ/MaxZ to defaults / 将区域坐标恢复为默认值 |
| **导出**     | **Export**              | Export single-seed results / 导出单种子搜索结果              |
| **排序**     | **Sort**                | Sort results by distance to origin / 按到原点距离排序       |
| **选择文件**   | **Select File**         | Import seed list (batch tab) / 导入种子列表（批量 Tab）       |
| **导出完整结果** | **Export Full Results** | Export full batch output / 导出批量完整结果                 |
| **导出种子列表** | **Export Seed List**    | Export seeds that had hits / 导出有结果的种子列表             |

Click **开始搜索 / Start Search** to start, **暂停 / Pause** to pause, **停止 / Stop** to stop.

点击 **开始搜索 / Start Search** 开始，**暂停 / Pause** 暂停，**停止 / Stop** 停止。

Default thread count is the number of CPU cores (capped at runtime). If you enter more threads than CPU cores, a confirmation dialog offers to adjust automatically.

默认线程数为 CPU 核数（运行时会 clamp）。若输入超过 CPU 核数，会弹出确认框询问是否自动调整。

---

### Tab 1: Single seed search / 单种子搜索

| Field / 字段 | 中文                                       | English                                          |
|------------|------------------------------------- -----|--------------------------------------------------|
| Seed       | **种子**                                   | **Seed**                                         |
| Threads    | **线程数**                                  | **Thread Count**                                 |
| Version    | **版本** (26.2+ = 26.2 及以后，含 26.3+)        | **Version** (26.2+ = 26.2 and later, incl. 26.3+) |
| Pair Level | **相邻神殿数** (2 / 3 / 4)                    | **Pair Level**                                   |
| Min Area   | **最小可用面积（可选）** + 面积类型下拉（单人/双人/单门最小面积） | **Min usable area (optional)** + area type (single / dual / gate) |
| Search mode| **搜索单假人 / 搜索双假人 / 搜索单门**（可多选：决定计算哪些面积，仅 3/4 连） | **Single dummy / Dual dummy / Single gate** (multi-select: which areas to compute, 3/4 only) |
| Region     | **MinX / MaxX / MinZ / MaxZ (×512)**     | same                                             |
| Language   | **语言** (Chinese / English)               | **Language** (Chinese / English)                 |
| Option     | **找到符合条件的多连时立即暂停**                       | **Pause immediately when a valid pair is found** |
| Option     | **群系检查**                                 | **Biome check**                                  |

- Result format / 结果格式：the results panel is a **table** whose column header stays pinned while you scroll; **drag a header to reorder the columns** and use **列… / Columns…** to show/hide them. One row per hit, nine columns by default in this order — `单假人最优坐标 / 单假人最优面积 / 海洋群系类型 / 相邻神殿数 / 假人1坐标 / 假人2坐标 / 双假人最优面积 / 单门面积上限 / 单门地狱门坐标`. Points are plain `x z` numbers (no `/tp`, no `x 50 z`), the level is a bare number (no brackets), and there are no `面积=`-style labels - the header names the columns. Cells that do not apply (single-point mode has no second dummy; biome check off has no ocean type) show `-`. The batch tab prefixes a `种子 / Seed` column. The **列… / Columns…** picker sets what the table shows, and the text/CSV exports follow it (including the dragged order); its "Include header row in CSV" option applies to CSV only. Select rows and press **Ctrl+C** to copy them as tab-separated text (the column header is prepended); right-click for Copy all / Select all.  
  结果面板是一个**表格**：列头固定，滚动时始终可见；每命中一条占一行，固定 9 列并按下述顺序排列——`单假人最优坐标 / 单假人最优面积 / 海洋群系类型 / 相邻神殿数 / 假人1坐标 / 假人2坐标 / 双假人最优面积 / 单门面积上限 / 单门地狱门坐标`。坐标只写 `x z`（不带 `/tp`、不带 `x 50 z`），连数是纯数字（不带括号），也没有 `面积=` 之类的前缀——列名由表头给出。不适用的格子（单点模式没有第二假人、未开群系检查没有海洋类型）显示 `-`。批量页在最前面多一列 `种子`。**列…** 按钮选择显示哪些列，TXT/CSV 导出与之一致（连拖动后的顺序也一致）；其中的“CSV 包含表头行”只影响 CSV。选中行按 **Ctrl+C** 复制（会自动带上列头），右键菜单提供“复制全部 / 全选”。
- **CSV export / CSV 导出**：**导出CSV** writes exactly the columns currently shown in the table (drag order included) plus an optional header row; use **列… / Columns…** to change the set, so partial exports are one click away.  
  **导出CSV** 写出当前表格里显示的列（含拖动后的顺序），可选是否带表头行；要增减列用 **列…** 按钮，因此部分列导出就是一勾的事。
- **`单门面积上限` / `area_max`**：the largest spawning area the group can reach when the number of AFK dummies is unbounded **but the same max−min ≤ 256 footprint still applies** — i.e. the most box blocks any 257×257 block window contains (multiset counting, computed exactly with a 2D prefix-sum sliding window). It is a reference ceiling, not something one/two dummies can necessarily reach: `area_single ≤ area_dual ≤ area_max ≤ level×3364` (6728 / 10092 / 13456 for 2/3/4 groups; a group whose boxes already fit one footprint has `area_max = level×3364`). Pairs always fit one footprint, so their `area_max` is 6728.  
  **`单门面积上限`（`area_max`）**：假人数不限、但**仍受 max−min ≤ 256 footprint 约束**时该组能取得的刷怪面积上限——即任意 257×257 方块窗口内 box 方块数的最大值（多重集计数，用二维前缀和滑窗精确求出）。它只是参考天花板，1/2 个假人不一定达到：`面积单门 ≤ 面积双门 ≤ area_max ≤ 连数×3364`（2/3/4 连分别为 6728 / 10092 / 13456；若该组所有 box 本来就装得进一个 footprint，则 `area_max = 连数×3364`）。2 连必然装得下，故其 `area_max` 恒为 6728。
- **`单门地狱门坐标` / gate portal**：the nether-side `x z` for that ceiling footprint — the **centre of mass of the blocks counted in the winning 257×257 window**, converted with Minecraft's overworld→nether rule `nether = overworld / 8` (floor). Build/link the portal at these coordinates so its overworld exit lands in the middle of the ceiling farm; the table columns are `(x, z)` only (no `y`, no `/tp`).  
  **`单门地狱门坐标`**：该上限 footprint 对应的**下界**侧 `x z`——取「上限窗口内计入方块的重心」，按 Minecraft 的主世界→下界规则 `下界 = 主世界 / 8`（向下取整）换算。在这两个坐标建门/连门，主世界出口就落在该上限农场的中心；表格只给 `x z`（不含 `y`，不带 `/tp`）。
- **Triple & quadruple search / 3/4 连搜索说明**：group members are at most 336 blocks apart and must pass deep-ocean biome sampling before the shared AFK area is computed; the usable area must strictly exceed the "Min Usable Area" threshold or the group is dropped (threshold applies to level 3/4 only, allowed range 6728~13455, invalid input pops a dialog and blocks the search).  
  成员间中心距 ≤ 336、且通过深海群系采样后才计算共享挂机面积；可用面积须严格大于「最小可用面积」阈值，不达标的组合会被剔除（阈值仅对 3/4 连生效，允许范围 6728~13455，非法输入会弹窗阻止搜索）。
- **Search modes / 搜索模式** (3/4 only, any combination): the switches decide **which areas are computed** — **搜索单假人** the single-point optimum, **搜索双假人** the two-point union (span ≤ 256), **搜索单门** the unlimited-dummy ceiling in one 257×257 footprint. The **area-type dropdown decides which of those areas the threshold applies to**; a group is reported only when *that* area passes (not "any of them"). The other computed areas are still filled into the row, e.g. with all three enabled and the dropdown on *single*, a group whose single area passes is reported with its dual points and ceiling alongside.
  **搜索模式**（仅 3/4 连，可任意多选）：三个开关决定**算哪些面积**——**搜索单假人** 算单点最优面积、**搜索双假人** 算双点并集（跨度 ≤256）、**搜索单门** 算一个 257×257 footprint 内不限人数的上限。**面积类型下拉决定阈值作用在哪种面积上**，只有该面积达标才输出（不是“任一达标”）。其余已算出的面积照样填进行里：例如三个都勾、下拉选“单人最小面积”，单点达标就会输出，同时带出双假人坐标与单门上限。
- **Dual-dummy mode / 双假人模式**：tick **同时搜索双假人挂机最优点与最大单门可利用刷怪面积** (3/4 only) to search the optimal **two** AFK points instead of one: a spawning block counts when within 128 blocks of *either* dummy, and `面积` becomes the union of both. **The counted area must satisfy max−min ≤ 256 per axis** (a single 128-disk spans exactly 256, i.e. 257 block columns, so only dual pairs sprawled wider than one disk are excluded). Only groups where the dual pair beats the single optimum are reported; every result also carries the optimal **single-dummy** area and point (`单假人最优面积` / `单假人最优坐标`; that point is one attaining cell of the optimum and may differ by a few blocks from the single-mode AFK point at the same area) plus the single-gate ceiling `单门面积上限` (`area_max`) and its nether portal `单门地狱门坐标`. The same min-area threshold applies to the two-point union — raise it accordingly (e.g. 8500+ for triples; 10092 = 3 full boxes is a natural target, 13456 the quad ceiling). Solver: alternating exact maximization on `union(a,b) = cov(a) + |disk(b)\disk(a)|` — each half-step is exact, the joint optimum is a seeded heuristic (checked against brute-force restarts: 39/40 random groups matched exactly, worst gap 1 block). Cost ≈7–10× a single-point solve per candidate group (measured over 400 random groups), so the whole search slows down only in proportion to how many groups pass the prefilter.  
  勾选**同时搜索双假人挂机最优点与最大单门可利用刷怪面积**（仅 3/4 连）后，搜索最优的**两个**假人挂机位置：刷怪方块只要落在任一假人 128 格内即计入，`面积` 为两点并集，且**计入面积的每轴 max−min ≤ 256**（单盘投影恰好 256，即 257 列，因此只会排除比单盘更散的双点布局）。仅报告双门严格优于单门的组；每条结果同时给出**单门**最大可用面积与最优点（`单假人最优面积` / `单假人最优坐标`；该点是最优解的一个取到点，与单点模式给出的重心点可能相差数格而面积相同），以及**单门面积上限**（`area_max`）与它的下界门坐标（`单门地狱门坐标`）。阈值作用于双点并集——请相应调高（三连建议 8500 起，10092 = 三盒全覆盖是自然目标，四连上限 13456）。求解采用恒等式 `union(a,b) = cov(a) + |disk(b)\disk(a)|` 的交替精确最大化：每个半步都是精确最优，整体为多起点启发式（与暴力随机重启对照：40 组中 39 组完全一致，最大差 1 格）。单个候选组的双点求解约为单点求解的 7–10 倍（400 组实测），整体搜索耗时的增加取决于通过预筛的组数。

---

### Tab 2: Batch seed search / 批量种子搜索

Import a text file (one seed per line), configure region and thread count, then click **开始搜索 / Start Search**. The tool searches each seed in order and prints seeds with at least one valid pair.

导入文本文件（每行一个种子），设置区域与线程数后点击 **开始搜索 / Start Search**。程序按顺序搜索每个种子，并输出存在有效多连的种子及其结果。

The batch tab also provides **Pair Level** and **Min Usable Area** settings (same meaning as Tab 1; the threshold applies to level 3/4 only).

批量 Tab 同样提供**相邻神殿数**与**最小可用面积**设置（含义与 Tab 1 相同，阈值同样仅对 3/4 连生效）。

---

## Project layout / 项目结构

| Path                                             | Description / 说明                                                                                                               |
|-------------------------------------- ------------|--------------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/project/`                         | Swing UI (`DoubleMonumentForFixedSeed`), JNI bridge (`MonumentFinderBridge`), runner (`MonumentSearchRunner`) / 界面、JNI 桥接、搜索编排 |
| `src/test/java/project/`                         | JNI smoke test (`MonumentNativeSmokeTest`) / JNI 冒烟测试 |
| `src/main/resources/messages_*.properties`       | UI i18n strings (zh_CN / en_US) / 界面国际化文案                                                                                      |
| `monument_search.cpp` / `monument_search_core.c` | C++ search and cubiomes calls / C++ 搜索与 cubiomes 调用                                                                            |
| `jni/`                                           | JNI glue and CMake target `monumentFinderLibJ`                                                                                 |
| `cubiomes/`                                      | git submodule ([xpple/cubiomes](https://github.com/xpple/cubiomes))                                                            |
