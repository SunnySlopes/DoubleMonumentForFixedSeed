# Double Monument For Fixed Seed  
# 固定种子多连海底神殿搜索工具

A GUI program for searching **double / triple / quadruple ocean monuments** in Minecraft Java Edition fixed seeds. Supports Minecraft Java Edition **1.18.2** through **26.2**.

用于在 Minecraft Java 版固定种子中搜索**二连 / 三连 / 四连海底神殿**的图形界面工具。支持 **1.18.2** 至 **26.2**。

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

Output / 输出：`build/libs/MultiMonumentFinder-1.0.jar`

On Windows with MSYS2, ensure `C:\msys64\ucrt64\bin` (or `mingw64\bin`) is on `PATH` when running Gradle.

Windows + MSYS2 环境下，运行 Gradle 前请将 `C:\msys64\ucrt64\bin`（或 `mingw64\bin`）加入 `PATH`。

---

## Run / 运行

```bash
java -jar build/libs/MultiMonumentFinder-1.0.jar
```

Or use `run.bat` after building.  
或构建后使用 `run.bat`。

The JAR embeds native libraries under `/native/{windows|linux|macos}/`. You can also run with:

JAR 内嵌原生库，路径为 `/native/{windows|linux|macos}/`。也可指定库路径运行：

```bash
java -Djava.library.path=build/native -jar build/libs/MultiMonumentFinder-1.0.jar
```

---

## JNI smoke test / JNI 冒烟测试

```bash
./gradlew buildNative compileTestJava
java -Djava.library.path=build/native -cp "build/classes/java/main;build/classes/java/test" project.MonumentNativeSmokeTest
```

Optional args / 可选参数：`seed minX maxX minZ maxZ pairLevel biomeCheck minGroupArea`
(e.g. `... project.MonumentNativeSmokeTest 123 -2 2 -2 2 3 true 6728` for a triple search with threshold 6728).
例如：`... 123 -2 2 -2 2 3 true 6728` 表示三连搜索、最小面积阈值 6728（阈值传 0 用内置默认 6728）。

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

| Field / 字段 | 中文                                   | English                                          |
|------------|--------------------------------------|--------------------------------------------------|
| Seed       | **种子**                               | **Seed**                                         |
| Threads    | **线程数**                              | **Thread Count**                                 |
| Version    | **版本**                               | **Version**                                      |
| Pair Level | **相邻神殿数** (2 / 3 / 4)              | **Pair Level**                                   |
| Min Area   | **最小可用面积** (仅 3/4 连时显示)             | **Min Usable Area**                              |
| Region     | **MinX / MaxX / MinZ / MaxZ (×512)** | same                                             |
| Language   | **语言** (Chinese / English)           | **Language**                                     |
| Option     | **找到符合条件的多连时立即暂停**                   | **Pause immediately when a valid pair is found** |
| Option     | **群系检查**                              | **Biome check**                                  |

- Results format / 结果格式：`/tp x 50 z [type=...] 面积=...`  
  三/四连结果末尾带连级 / Triple/quadruple results end with the level: `(3)` / `(4)`  
  `type=` 为海洋类型（低温/冻洋/其他），仅在勾选群系检查时输出  
- **3/4 连搜索说明 / Triple & quadruple search**: 成员间中心距 ≤ 336、且通过深海群系采样后才计算共享挂机面积；可用面积须大于「最小可用面积」阈值，不达标的组合会被剔除（阈值仅对 3/4 连生效，允许范围 6728~13455，非法输入会弹窗阻止搜索）。主进度条下方有第二条进度条，显示 3/4 连的**已计算组合数 / 候选组合数**（仅 3/4 连且已筛出候选时显示）。

---

### Tab 2: Batch seed search / 批量种子搜索

Import a text file (one seed per line), configure region and thread count, then click **开始搜索 / Start Search**. The tool searches each seed in order and prints seeds with at least one valid pair.

导入文本文件（每行一个种子），设置区域与线程数后点击 **开始搜索 / Start Search**。程序按顺序搜索每个种子，并输出存在有效多连的种子及其结果。

批量 Tab 同样提供**相邻神殿数**与**最小可用面积**设置（含义与 Tab 1 相同，阈值同样仅对 3/4 连生效）。

---

## Project layout / 项目结构

| Path                                             | Description / 说明                                                                                                               |
|--------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/project/`                         | Swing UI (`DoubleMonumentForFixedSeed`), JNI bridge (`MonumentFinderBridge`), runner (`MonumentSearchRunner`) / 界面、JNI 桥接、搜索编排 |
| `src/test/java/project/`                         | JNI smoke test (`MonumentNativeSmokeTest`) / JNI 冒烟测试 |
| `src/main/resources/messages_*.properties`       | UI i18n strings (zh_CN / en_US) / 界面国际化文案                                                                                      |
| `monument_search.cpp` / `monument_search_core.c` | C++ search and cubiomes calls / C++ 搜索与 cubiomes 调用                                                                            |
| `jni/`                                           | JNI glue and CMake target `monumentFinderLibJ`                                                                                 |
| `cubiomes/`                                      | git submodule ([xpple/cubiomes](https://github.com/xpple/cubiomes))                                                            |
