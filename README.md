# Double Monument Core

**All-In-One Finder 的二联海底神殿搜索内核**（`all-in-one-base` 分支，tag `aio-lib-v1`）。

本仓库原来是带 Swing 界面的独立工具；这个分支只保留纯 C/C++ 算法与
cubiomes 封装，删除了 Java、JNI、字体/图标、Gradle 构建壳和自带的
cubiomes 子模块。算法、配对阈值和结果字段都没有改动，只是给导出符号加了
`Double*` / `dm_*` 前缀，避免与 AIO 中已编译的 dripstonecave、multimonument、
fortress 内核撞名。

- 无 Java / JNI / Swing / UI / Gradle；
- 不定义 `main`，不限制结果条数；
- C++20 + C11，只依赖 cubiomes、标准库和 `pthread`；
- 可直接把 4 个源文件交给 All-In-One Finder 的 CMake 编译。

---

## 文件

| 文件 | 作用 |
| --- | --- |
| `monument_search.h` | `DoubleProgress`、`DoublePairHit`、`DoubleMonumentConfig`、`runDoubleMonumentSearch` |
| `monument_search.cpp` | 配对/扫描逻辑（算法未改） |
| `monument_search_core.h` / `.c` | cubiomes 封装：神殿位置、深海可行性、海温分类（`dm_*`） |
| `Thread.h` | `DoubleResults` / `DoublePool` |
| `test/selftest.cpp` | 可选命令行自测，用于与旧版结果对比（默认不编译） |
| `CMakeLists.txt` | 只把内核编译成 `double_monument_core` 静态库 |

---

## 接口

AIO 的 `native/double_monument_host.cpp` 按下面的方式调用（节选）：

```cpp
#include "monument_search.h"

DoubleMonumentConfig cfg;
cfg.seed = seed;
cfg.mc = MC_26_3;            // AIO 固定 26.3
cfg.minX = regMinX;          // region 坐标（方块 / 512）
cfg.maxX = regMaxX;          // 开区间
cfg.minZ = regMinZ;
cfg.maxZ = regMaxZ;
cfg.pauseOnPairFound = false; // AIO 不使用「找到即暂停」

DoubleProgress progress;
DoubleResults<DoublePairHit> results;
runDoubleMonumentSearch(cfg, &progress, results, threads);

auto hits = results.getAllResults(); // 字段：hangX, hangZ, dx, dz, lowEfficiency
```

要点：

- `runDoubleMonumentSearch` 在调用线程同步执行，返回时结果已写完；
- 暂停/停止沿用 `DoubleProgress::try_pause` / `try_stop` 原子量；
- `DoubleResults` 不设上限，AIO 收集后统一排序并截断前 1000；
- `lowEfficiency = 1` 表示该二联可用但效率会略微损失，AIO 会把它排到最后；
- `hangX/hangZ` 是两殿中点；`dm_monument_classify_ocean_type(_g)` 返回
  `0=others / 1=cold / 2=frozen`。

### 符号约定

| 旧 | 新 |
| --- | --- |
| `Progress` | `DoubleProgress` |
| `PairHit` | `DoublePairHit` |
| `MonumentSearchConfig` | `DoubleMonumentConfig` |
| `runMonumentSearch` | `runDoubleMonumentSearch` |
| `ThreadSafeResults` | `DoubleResults` |
| `ThreadPool` | `DoublePool` |
| `MonumentGenerator` | `DmMonumentGenerator` |
| `monument_*`（9 个 C API） | `dm_monument_*` |

`Thread.h` 的 include guard 是 `AIO_DOUBLE_MONUMENT_THREAD_H`，多联的
`MONUMENT_FINDER_THREAD_H` 不会再冲突。

---

## 独立编译

本仓库不再自带 cubiomes，需要指向 AIO 的 `tools/cubiomes`（或任意同版本
cubiomes 检出）：

```powershell
cmake -S . -B build `
  -DDOUBLE_MONUMENT_CUBIOMES_DIR="..\All-In-One\All-In-One Finder\tools\cubiomes" `
  -DDOUBLE_MONUMENT_BUILD_SELFTEST=ON
cmake --build build -j
```

产物：

- `build/libdouble_monument_core.a`（Windows 下为 `.a`）
- `build/double_monument_selftest.exe`（仅当开启自测）

也可以直接编译（include 路径按实际目录调整）：

```powershell
g++ -std=c++20 -O3 -c monument_search.cpp -I. -I..\tools
gcc -std=c11   -O3 -c monument_search_core.c -I. -I..\tools
```

注意 `-I` 指向 cubiomes 的**父目录**，因为源文件使用 `cubiomes/...` 形式包含头文件。

### 自测

```powershell
build\double_monument_selftest.exe 7 -48 48 -48 48 8
```

参数：`seed minX maxX minZ maxZ [threads] [mc]`，区域坐标单位是 512 方块。
输出按坐标排序，便于和改造前构建逐行 diff。

---

## 与 All-In-One Finder 集成

- AIO 的 `native/CMakeLists.txt` 直接编译 `monument_search.cpp` 和
  `monument_search_core.c`，并使用 AIO 自己的 `tools/cubiomes`；
- `native/double_monument_host.cpp` 只需包含本仓库的 `monument_search.h`；
  该头文件已经包含 `Thread.h` 和 `monument_search_core.h`，因此
  `DoubleResults`、`DmMonumentGenerator` 和 `dm_*` 都有完整声明；
- AIO 侧把 `AIO_DOUBLE_MONUMENT_REFACTORED` 切到 `1` 后，原先的宏兼容层
  可以删除；
- 本分支不再需要单独的 cubiomes 子模块，AIO 父仓的 submodule 指针指到
  本分支的提交即可。
