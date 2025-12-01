# Double Monument For Fixed Seed

A GUI program for searching double ocean monuments (paired ocean monuments) in Minecraft Java Edition fixed seeds. This program works for Minecraft Java Edition 1.18+.

## How to Run

**This program requires Java 17 or higher.** Please make sure you have Java installed.

You can right-click the jar file and select "Java(TM) Platform SE binary" as the opening method.

You can also use cmd to run: Open cmd.exe (you can search for it in the search bar), then use the following command to run:

Type "java -jar", then type **space**, drag the jar file into the window and press Enter.

Type "java -Xms2048m -Xmx4096m -jar", then type **space**, drag the jar file into the window and press Enter. This is for allocating memory.

You can change -Xms2048m -Xmx4096m to the memory size you want. But it is recommended to allocate at least 4GB or more memory for this program.

**You need to wait 10-20 seconds for the program to fully start.** This is because it needs to initialize Seedchecker first, which takes a long time. Please do not perform other operations during this stage.

## How to Use?

Click the **Start Search** button to start searching, click the **Pause** button to pause searching, and click the **Stop** button to stop searching.

The program has two tabs:

### Tab 1: Single Seed Search

**Left side - Parameter Settings:**

**Seed**: The world seed you want to search. Must be an integer.

**Thread Count**: The number of threads to use. Valid range is 1 to your computer's maximum thread count.

**Version**: Select the Minecraft version (1.21.1, 1.20.1, 1.19.2, 1.18.2).

**MinX/MaxX/MinZ/MaxZ (x512)**: The coordinate range to search for ocean monuments. These are region coordinates (each region is 512 blocks). The default range is -100 to 100 for both X and Z.

**Pause on Pair Found**: When enabled, the search will automatically pause when a valid pair is found (excluding low-efficiency cases).

**Right side - Results:**

The results are displayed in the format: `/tp x 50 z (差值: dx=?, dz=?)`

- `x` and `z` are the center coordinates of the two monuments (average of the two monument positions)
- `dx` and `dz` are the absolute differences in X and Z coordinates between the two monuments
- Some results may have the suffix " - 效率会略微损失" (efficiency will be slightly lost), indicating low-efficiency cases

**Buttons:**

- **Export**: Export the search results to a text file
- **Sort**: Sort results by distance from origin (0, 0) from near to far

### Tab 2: Batch Seed Search

**Left side - Parameter Settings:**

**Seed File**: Click "选择文件" (Select File) to import a text file containing seeds (one seed per line).

**Thread Count**: The number of threads to use for each seed search.

**Version**: Select the Minecraft version.

**MinX/MaxX/MinZ/MaxZ (x512)**: The coordinate range to search. Default range is -16 to 16 for both X and Z.

**Right side - Results:**

Results are displayed with the seed number on the first line, followed by all paired monument coordinates for that seed:

```
1234567890
/tp x 50 z (差值: dx=?, dz=?)
/tp x 50 z (差值: dx=?, dz=?)
9876543210
/tp x 50 z (差值: dx=?, dz=?)
...
```

The coordinates are sorted by distance from origin (0, 0) from near to far for each seed.

**Buttons:**

- **Export Full Results**: Export all results in the same format as displayed
- **Export Seed List**: Export only the seed numbers (one per line)
- **Sort**: Sort results within each seed group by x²+z² from small to large

## Search Conditions

The program searches for pairs of ocean monuments where the absolute differences in X and Z coordinates between their center positions meet the following conditions (assuming |x| >= |z|, ensuring all spawnable areas of both monuments are fully contained within a single player's 128-block radius):

- dx = 112, dz: all values allowed
- dx = 128, dz ≤ 112
- dx = 144, dz ≤ 96
- dx = 160, dz ≤ 64
- dx = 176, dz ≤ 32

Additionally, the following cases will also be listed (slightly exceeding the radius but still usable):
- dx = 192, dz = 0
- dx = 176, dz = 48
- dx = 160, dz = 80

**Summary**: When the distance between two monument centers is less than 180 blocks, all cases except (160, 80) meet the perfect pair conditions. Cubiomes Viewer can use this radius to filter for double monuments.

**Note**: The program also checks for monuments outside the search range that can form valid pairs with monuments inside the range.

## Libraries Mainly Used in This Program

- [SeedFinding](https://github.com/SeedFinding) - Minecraft seed finding library
- [noise-sampler](https://github.com/KalleStruik/noise-sampler) - Noise sampling library
- [seed-checker](https://github.com/jellejurre/seed-checker) - Seed checking library

## Credits

- [SunnySlopes](https://github.com/SunnySlopes) - Original author
- Font: 江城黑体 (Jiangcheng Heiti)

---

## 中文翻译：

# 定种二联海底神殿搜索工具

一个用于在Minecraft Java版定种中搜索二联海底神殿（成对的海底神殿）的GUI程序。此程序适用于Minecraft Java版 1.18+。

## 如何运行

**此程序需要 Java 17 或更高版本**。请确保您已下载 Java。

您可以右键单击 jar 文件，并选择 "Java(TM) Platform SE binary" 作为打开方式。

您也可以使用 cmd 来运行：打开 cmd.exe（您可以在搜索栏中搜索它），然后使用以下命令运行：

输入 "java -jar"，然后输入**空格**，将 jar 文件拖到窗口中并按回车。

输入 "java -Xms2048m -Xmx4096m -jar"，然后输入**空格**，将 jar 文件拖到窗口中并按回车。这是用于分配内存的。

您可以将 -Xms2048m -Xmx4096m 更改为您想要的内存大小。但建议为此程序分配最大 4GB 或更多的内存。

**您需要等待 10-20 秒才能完全启动这个程序**。这是因为它需要先初始化 Seedchecker，这会花费较长时间。在此阶段请不要进行其他操作。

## 如何使用？

点击**开始搜索**按钮启动搜索，点击**暂停**按钮暂停搜索，点击**停止**按钮停止搜索。

程序有两个标签页：

### 标签页1：单种子搜索

**左侧 - 参数设置：**

**种子**：您要搜索的世界种子。必须为整数。

**线程数**：使用的线程数量。有效范围为 1 至您计算机的最大线程数。

**版本**：选择Minecraft版本（1.21.1, 1.20.1, 1.19.2, 1.18.2）。

**MinX/MaxX/MinZ/MaxZ (x512)**：搜索海底神殿的坐标范围。这些是区域坐标（每个区域为512方块）。默认范围为 X 和 Z 都是 -100 到 100。

**找到符合条件的二联时立即暂停**：启用后，当找到符合条件的二联时（不包括低效情况）会自动暂停搜索。

**右侧 - 结果：**

结果以以下格式显示：`/tp x 50 z (差值: dx=?, dz=?)`

- `x` 和 `z` 是两个神殿的中心坐标（两个神殿位置的平均值）
- `dx` 和 `dz` 是两个神殿在 X 和 Z 坐标上的绝对差值
- 某些结果可能带有后缀 " - 效率会略微损失"，表示低效情况

**按钮：**

- **导出**：将搜索结果导出到文本文件
- **排序**：按距离原点 (0, 0) 从近到远排序结果

### 标签页2：批量种子搜索

**左侧 - 参数设置：**

**种子文件**：点击"选择文件"按钮导入包含种子的文本文件（每行一个种子）。

**线程数**：每个种子搜索使用的线程数量。

**版本**：选择Minecraft版本。

**MinX/MaxX/MinZ/MaxZ (x512)**：搜索坐标范围。默认范围为 X 和 Z 都是 -16 到 16。

**右侧 - 结果：**

结果以种子号在第一行，下方是该种子的所有二联神殿坐标的格式显示：

```
1234567890
/tp x 50 z (差值: dx=?, dz=?)
/tp x 50 z (差值: dx=?, dz=?)
9876543210
/tp x 50 z (差值: dx=?, dz=?)
...
```

坐标按距离原点 (0, 0) 由近及远的顺序排序。

**按钮：**

- **导出完整结果**：按照输出区域的格式导出所有结果
- **导出种子列表**：只导出种子号（每行一个）
- **排序**：对每个种子组内的结果按 x²+z² 由小到大排序

## 搜索条件

程序搜索两个海底神殿中心位置坐标差值的绝对值满足以下条件的二联（假设 |x| >= |z|，保证两个海底神殿所有刷怪面积完整地包含在单人128半径内）：

- dx = 112，dz：全部允许
- dx = 128，dz ≤ 112
- dx = 144，dz ≤ 96
- dx = 160，dz ≤ 64
- dx = 176，dz ≤ 32

此外，以下情况也会列出（稍微超了一点点半径但是也算可用）：
- dx = 192，dz = 0
- dx = 176，dz = 48
- dx = 160，dz = 80

**总结**：两个神殿中心距离小于180时，除了 (160, 80) 的情况之外都符合完美二联的条件。cubiomes viewer 可以用这个半径来卡二联。

**注意**：程序还会检查搜索范围外可以与范围内神殿形成有效二联的神殿。

## 此程序主要使用的库

- [SeedFinding](https://github.com/SeedFinding) - Minecraft种子查找库
- [noise-sampler](https://github.com/KalleStruik/noise-sampler) - 噪声采样库
- [seed-checker](https://github.com/jellejurre/seed-checker) - 种子检查库

## 致谢

- [SunnySlopes](https://github.com/SunnySlopes) - 原作者
- 字体：江城黑体
