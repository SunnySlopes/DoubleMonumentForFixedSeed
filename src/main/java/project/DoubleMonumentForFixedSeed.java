package project;

import com.seedfinding.mccore.version.MCVersion;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DoubleMonumentForFixedSeed extends JFrame {
    // 默认值（128x128的范围）
    private static final int DEFAULT_MIN_X = -128;
    private static final int DEFAULT_MAX_X = 128;
    private static final int DEFAULT_MIN_Z = -128;
    private static final int DEFAULT_MAX_Z = 128;
    // 第二个tab的默认值（16x16的范围）
    private static final int BATCH_DEFAULT_MIN_X = -16;
    private static final int BATCH_DEFAULT_MAX_X = 16;
    private static final int BATCH_DEFAULT_MIN_Z = -16;
    private static final int BATCH_DEFAULT_MAX_Z = 16;
    private static final int DEFAULT_THREAD_COUNT = 8;
    
    private JTextField searchSeedField;
    private JTextField searchThreadCountField;
    private JComboBox<String> versionComboBox;
    private JCheckBox pauseOnPairFoundCheckBox;
    private JTextField minXField;
    private JTextField maxXField;
    private JTextField minZField;
    private JTextField maxZField;
    private JButton searchStartButton;
    private JButton searchPauseButton;
    private JButton searchStopButton;
    private JButton searchResetButton;
    private JButton searchExportButton;
    private JButton searchSortButton;
    private JProgressBar searchProgressBar;
    private JLabel searchElapsedTimeLabel;
    private JLabel searchRemainingTimeLabel;
    private JTextArea searchResultArea;
    private SearchMonument searcher;
    private volatile boolean isSearchRunning = false;
    private volatile boolean isSearchPaused = false;
    private long lastSearchSeed = 0;
    private int lastSearchMinX = 0;
    private int lastSearchMaxX = 0;
    private int lastSearchMinZ = 0;
    private int lastSearchMaxZ = 0;
    private int lastSearchThreadCount = 0;
    
    // 第二个tab的组件
    private JButton batchImportButton;
    private JLabel batchSeedFileLabel;
    private JTextField batchThreadCountField;
    private JComboBox<String> batchVersionComboBox;
    private JTextField batchMinXField;
    private JTextField batchMaxXField;
    private JTextField batchMinZField;
    private JTextField batchMaxZField;
    private JButton batchStartButton;
    private JButton batchPauseButton;
    private JButton batchStopButton;
    private JButton batchResetButton;
    private JButton batchExportFullButton;
    private JButton batchExportSeedsButton;
    private JButton batchSortButton;
    private JProgressBar batchProgressBar;
    private JLabel batchElapsedTimeLabel;
    private JLabel batchRemainingTimeLabel;
    private JTextArea batchResultArea;
    private volatile boolean isBatchSearchRunning = false;
    private volatile boolean isBatchSearchPaused = false;
    private SearchMonument currentBatchSearcher = null;
    private List<Long> batchSeedList = new ArrayList<>();
    private String batchSeedFileName = "";
    
    // 加载的字体
    private Font loadedFont = null;

    public DoubleMonumentForFixedSeed() {
        setTitle("Minecraft Java版二联海底神殿搜索工具");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // 设置窗口图标
        setWindowIcon();
        
        // 设置中文字体
        setChineseFont();

        // 创建标签页
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(getLoadedFont());
        
        // 第一个tab：单种子搜索
        JPanel searchPanel = createSearchPanel();
        tabbedPane.addTab("单种子搜索", searchPanel);
        
        // 第二个tab：批量种子搜索
        JPanel batchPanel = createBatchSearchPanel();
        tabbedPane.addTab("批量种子搜索", batchPanel);
        
        add(tabbedPane, BorderLayout.CENTER);
        
        pack();
        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    // 创建搜索面板（第一个功能）
    private JPanel createSearchPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 左侧：输入和进度
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        // 输入区域
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Seed 输入
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel seedLabel = new JLabel("种子:");
        seedLabel.setFont(getLoadedFont());
        inputPanel.add(seedLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchSeedField = new JTextField("", 20);
        // 添加输入验证，非整数时提示
        searchSeedField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(searchSeedField, "种子");
            }
        });
        inputPanel.add(searchSeedField, gbc);

        // Thread Count 输入
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel threadLabel = new JLabel("线程数:");
        threadLabel.setFont(getLoadedFont());
        inputPanel.add(threadLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchThreadCountField = new JTextField(String.valueOf(DEFAULT_THREAD_COUNT), 20);
        // 添加输入验证，非整数时提示
        searchThreadCountField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(searchThreadCountField, "线程数");
            }
        });
        inputPanel.add(searchThreadCountField, gbc);

        // 版本选择下拉框
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel versionLabel = new JLabel("版本:");
        versionLabel.setFont(getLoadedFont());
        inputPanel.add(versionLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        String[] versionOptions = {"1.21.1", "1.20.1", "1.19.2", "1.18.2"};
        versionComboBox = new JComboBox<>(versionOptions);
        versionComboBox.setSelectedIndex(0); // 默认选择 1.21.1
        inputPanel.add(versionComboBox, gbc);

        // MinX 输入
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel minXLabel = new JLabel("MinX(x512):");
        minXLabel.setFont(getLoadedFont());
        inputPanel.add(minXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        minXField = new JTextField(String.valueOf(DEFAULT_MIN_X), 20);
        // 添加输入验证，非整数时提示
        minXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(minXField, "MinX");
            }
        });
        inputPanel.add(minXField, gbc);

        // MaxX 输入
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel maxXLabel = new JLabel("MaxX(x512):");
        maxXLabel.setFont(getLoadedFont());
        inputPanel.add(maxXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        maxXField = new JTextField(String.valueOf(DEFAULT_MAX_X), 20);
        maxXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(maxXField, "MaxX");
            }
        });
        inputPanel.add(maxXField, gbc);

        // MinZ 输入
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel minZLabel = new JLabel("MinZ(x512):");
        minZLabel.setFont(getLoadedFont());
        inputPanel.add(minZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        minZField = new JTextField(String.valueOf(DEFAULT_MIN_Z), 20);
        minZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(minZField, "MinZ");
            }
        });
        inputPanel.add(minZField, gbc);

        // MaxZ 输入
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel maxZLabel = new JLabel("MaxZ(x512):");
        maxZLabel.setFont(getLoadedFont());
        inputPanel.add(maxZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        maxZField = new JTextField(String.valueOf(DEFAULT_MAX_Z), 20);
        maxZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(maxZField, "MaxZ");
            }
        });
        inputPanel.add(maxZField, gbc);

        // 找到二联时立即暂停选项
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 2;
        pauseOnPairFoundCheckBox = new JCheckBox("找到符合条件的二联时立即暂停");
        pauseOnPairFoundCheckBox.setFont(getLoadedFont());
        pauseOnPairFoundCheckBox.setSelected(false); // 默认不选中
        inputPanel.add(pauseOnPairFoundCheckBox, gbc);
        gbc.gridwidth = 1;

        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout());
        searchStartButton = new JButton("开始搜索");
        searchPauseButton = new JButton("暂停");
        searchStopButton = new JButton("停止");
        searchResetButton = new JButton("重置搜索区域");
        searchPauseButton.setEnabled(false);
        searchStopButton.setEnabled(false);
        buttonPanel.add(searchStartButton);
        buttonPanel.add(searchPauseButton);
        buttonPanel.add(searchStopButton);
        buttonPanel.add(searchResetButton);

        // 静态文字展示区域（放在按钮上方）
        JPanel creditPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        creditPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel creditLabel = new JLabel("<html><div style='text-align: center;'><br><br><br><br><br><br><br>作者：b站@SunnySlopes<br>字体：江城黑体</div></html>");
        creditLabel.setFont(getLoadedFont()); // 使用加载的字体
        creditPanel.add(creditLabel);

        // 将 credit 和按钮放在一个容器中，credit 在上，按钮在下
        JPanel creditButtonPanel = new JPanel(new BorderLayout());
        creditButtonPanel.add(creditPanel, BorderLayout.NORTH);
        creditButtonPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 进度区域
        JPanel progressPanel = new JPanel(new GridBagLayout());
        GridBagConstraints pgc = new GridBagConstraints();
        pgc.insets = new Insets(5, 5, 5, 5);
        pgc.anchor = GridBagConstraints.WEST;
        pgc.fill = GridBagConstraints.HORIZONTAL;
        pgc.weightx = 1.0;

        pgc.gridx = 0;
        pgc.gridy = 0;
        pgc.gridwidth = 2;
        searchProgressBar = new JProgressBar(0, 100);
        searchProgressBar.setStringPainted(true);
        searchProgressBar.setString("进度: 0/0 (0.00%)");
        progressPanel.add(searchProgressBar, pgc);

        pgc.gridwidth = 1;
        pgc.gridy = 1;
        searchElapsedTimeLabel = new JLabel("已过时间: 0天 0时 0分 0秒");
        progressPanel.add(searchElapsedTimeLabel, pgc);

        pgc.gridy = 3;
        searchRemainingTimeLabel = new JLabel("剩余时间: 计算中...");
        progressPanel.add(searchRemainingTimeLabel, pgc);

        leftPanel.add(inputPanel, BorderLayout.NORTH);
        leftPanel.add(creditButtonPanel, BorderLayout.CENTER);
        
        // 将进度区域放在另一个容器中
        JPanel leftBottomPanel = new JPanel(new BorderLayout());
        leftBottomPanel.add(progressPanel, BorderLayout.CENTER);
        
        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.add(leftPanel, BorderLayout.CENTER);
        leftContainer.add(leftBottomPanel, BorderLayout.SOUTH);

        // 右侧：结果显示
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("检查结果(输出格式: /tp x 50 z (差值: dx=?, dz=?))"));
        searchResultArea = new JTextArea();
        searchResultArea.setEditable(false);
        searchResultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(searchResultArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel exportSortPanel = new JPanel(new FlowLayout());
        searchExportButton = new JButton("导出");
        searchExportButton.addActionListener(e -> exportSearchResults());
        searchSortButton = new JButton("排序");
        searchSortButton.addActionListener(e -> sortSearchResults());
        exportSortPanel.add(searchExportButton);
        exportSortPanel.add(searchSortButton);
        rightPanel.add(scrollPane, BorderLayout.CENTER);
        rightPanel.add(exportSortPanel, BorderLayout.SOUTH);

        // 使用 JSplitPane 分割
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, rightPanel);
        splitPane.setDividerLocation(450);
        splitPane.setResizeWeight(0.5);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 添加事件监听
        searchStartButton.addActionListener(e -> startSearch());
        searchPauseButton.addActionListener(e -> toggleSearchPause());
        searchStopButton.addActionListener(e -> stopSearch());
        searchResetButton.addActionListener(e -> resetSearchToDefaults());

        // 添加输入字段监听，检测参数变化
        addSearchParameterListeners();

        return mainPanel;
    }


    // 添加搜索参数监听器，检测参数变化（不包括线程数）
    private void addSearchParameterListeners() {
        // 种子变化监听（在已有监听器基础上添加检查）
        searchSeedField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
        });
        
        // 坐标变化监听
        minXField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
        });
        maxXField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
        });
        minZField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
        });
        maxZField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
        });
    }
    
    // 检查搜索参数是否变化（除了线程数）
    private void checkSearchParameterChange() {
        if (isSearchRunning && !isSearchPaused) {
            return; // 运行中且未暂停，不检查
        }
        
        if (!isSearchPaused) {
            return; // 未暂停，不检查
        }
        
        try {
            String seedText = searchSeedField.getText().trim();
            if (seedText.isEmpty()) {
                return;
            }
            long seed = Long.parseLong(seedText);
            int minX = Integer.parseInt(minXField.getText().trim());
            int maxX = Integer.parseInt(maxXField.getText().trim());
            int minZ = Integer.parseInt(minZField.getText().trim());
            int maxZ = Integer.parseInt(maxZField.getText().trim());
            
            // 如果参数变化且处于暂停状态，重置进度（线程数变化不触发重置）
            if (seed != lastSearchSeed || minX != lastSearchMinX || 
                maxX != lastSearchMaxX || minZ != lastSearchMinZ || maxZ != lastSearchMaxZ) {
                // 停止当前搜索
                if (searcher != null) {
                    searcher.stop();
                }
                isSearchRunning = false;
                isSearchPaused = false;
                searchStartButton.setEnabled(true);
                searchPauseButton.setEnabled(false);
                searchPauseButton.setText("暂停");
                searchStopButton.setEnabled(false);
                searchResetButton.setEnabled(true);
                searchSeedField.setEnabled(true);
                searchThreadCountField.setEnabled(true);
                versionComboBox.setEnabled(true);
                pauseOnPairFoundCheckBox.setEnabled(true);
                minXField.setEnabled(true);
                maxXField.setEnabled(true);
                minZField.setEnabled(true);
                maxZField.setEnabled(true);
                searchResultArea.setText("");
                searchProgressBar.setValue(0);
                searchProgressBar.setString("进度: 0/0 (0.00%)");
                searchRemainingTimeLabel.setText("剩余时间: 已重置（参数已更改）");
            }
        } catch (NumberFormatException e) {
            // 忽略无效输入
        }
    }

    
    // 验证整数输入
    private void validateIntegerInput(JTextField field, String fieldName) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return; // 空值不验证，会在开始运行时验证
        }
        try {
            // 尝试解析为double，检查是否为整数
            double value = Double.parseDouble(text);
            if (value != Math.floor(value)) {
                JOptionPane.showMessageDialog(this, fieldName + "必须为整数", "输入错误", JOptionPane.ERROR_MESSAGE);
                field.requestFocus();
            }
        } catch (NumberFormatException e) {
            // 不是数字，会在开始运行时验证
        }
    }
    
    
    // 排序搜索结果（按神殿中心点到坐标原点(0,0)的直线距离从近到远）
    private void sortSearchResults() {
        String text = searchResultArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        
        String[] lines = text.split("\n");
        List<String[]> results = new ArrayList<>();
        List<String> invalidLines = new ArrayList<>();
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            // 格式：/tp x 50 y (差值: dx=?, dz=?)
            if (line.startsWith("/tp ")) {
                try {
                    // 提取坐标：/tp x 50 y
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) {
                        int x = Integer.parseInt(parts[1]);
                        int z = Integer.parseInt(parts[3]);
                        
                        // 计算到原点(0,0)的距离
                        double distance = Math.sqrt(x * x + z * z);
                        results.add(new String[]{String.valueOf(distance), line});
                    } else {
                        invalidLines.add(line);
                    }
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    invalidLines.add(line);
                }
            } else {
                invalidLines.add(line);
            }
        }
        
        // 排序：按到原点的距离从近到远
        results.sort((a, b) -> {
            double d1 = Double.parseDouble(a[0]);
            double d2 = Double.parseDouble(b[0]);
            return Double.compare(d1, d2);
        });
        
        // 重新组合文本
        StringBuilder sb = new StringBuilder();
        for (String[] result : results) {
            sb.append(result[1]).append("\n");
        }
        for (String invalid : invalidLines) {
            sb.append(invalid).append("\n");
        }
        
        searchResultArea.setText(sb.toString());
    }

    // 搜索相关方法
    private void startSearch() {
        // 如果当前处于暂停状态，直接恢复（不重新开始）
        if (isSearchRunning && isSearchPaused) {
            // 检查线程数是否变化
            try {
                String threadText = searchThreadCountField.getText().trim();
                int threadCount = Integer.parseInt(threadText);
                if (threadCount < 1) {
                    JOptionPane.showMessageDialog(this, "线程数必须大于0", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // 检查线程数是否超过CPU核数
                int cpuThreads = Runtime.getRuntime().availableProcessors();
                if (threadCount > cpuThreads) {
                    int result = JOptionPane.showConfirmDialog(
                        this,
                        "线程数超过CPU核数（" + cpuThreads + "），是否自动调整为" + cpuThreads + "？",
                        "提示",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                    );
                    if (result == JOptionPane.YES_OPTION) {
                        threadCount = cpuThreads;
                        searchThreadCountField.setText(String.valueOf(cpuThreads));
                    } else {
                        return;
                    }
                }
                
                // 如果线程数变化，调整线程数（不弹框，不清除进度）
                if (threadCount != lastSearchThreadCount) {
                    // 获取其他参数
                    String selectedVersion = (String) versionComboBox.getSelectedItem();
                    MCVersion mcVersion = getMCVersion(selectedVersion != null ? selectedVersion : "1.21.1");
                    
                    // 如果版本变化，需要重新创建searcher
                    if (searcher == null || !searcher.getMCVersion().equals(mcVersion)) {
                        searcher = new SearchMonument(mcVersion);
                    }
                    
                    // 调用startSearch，它会检测到暂停状态并调整线程数
                    String seedText = searchSeedField.getText().trim();
                    long seed = Long.parseLong(seedText);
                    int minX = Integer.parseInt(minXField.getText().trim());
                    int maxX = Integer.parseInt(maxXField.getText().trim());
                    int minZ = Integer.parseInt(minZField.getText().trim());
                    int maxZ = Integer.parseInt(maxZField.getText().trim());
                    
                    boolean pauseOnPairFound = pauseOnPairFoundCheckBox.isSelected();
                    searcher.startSearch(seed, threadCount, minX, maxX, minZ, maxZ, 
                            this::updateSearchProgress, this::addSearchResult, pauseOnPairFound);
                    
                    lastSearchThreadCount = threadCount;
                    searchPauseButton.setText("暂停");
                    searchThreadCountField.setEnabled(false);
                    return;
                } else {
                    // 线程数没变化，直接恢复
                    searcher.resume();
                    isSearchPaused = false;
                    searchPauseButton.setText("暂停");
                    searchThreadCountField.setEnabled(false);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, 
                    "线程数格式错误，无法继续", 
                    "错误", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        try {
            // 验证种子
            String seedText = searchSeedField.getText().trim();
            if (seedText.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入种子值", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查种子是否为整数
            double seedDouble;
            try {
                seedDouble = Double.parseDouble(seedText);
                if (seedDouble != Math.floor(seedDouble)) {
                    JOptionPane.showMessageDialog(this, "种子必须为整数", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "种子格式错误，请输入整数", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查种子是否超过MC正常种子边界（绝对值超过2^63-1）
            long seed;
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "种子值超出范围（绝对值不能超过2^63-1）", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 验证线程数
            String threadText = searchThreadCountField.getText().trim();
            double threadDouble;
            try {
                threadDouble = Double.parseDouble(threadText);
                if (threadDouble != Math.floor(threadDouble)) {
                    JOptionPane.showMessageDialog(this, "线程数必须为整数", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "线程数格式错误，请输入整数", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            int threadCount = (int) threadDouble;
            if (threadCount < 1) {
                JOptionPane.showMessageDialog(this, "线程数必须大于0", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查线程数是否超过CPU核数
            int cpuThreads = Runtime.getRuntime().availableProcessors();
            if (threadCount > cpuThreads) {
                int result = JOptionPane.showConfirmDialog(
                    this,
                    "线程数超过CPU核数（" + cpuThreads + "），是否自动调整为" + cpuThreads + "？",
                    "提示",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                );
                if (result == JOptionPane.YES_OPTION) {
                    threadCount = cpuThreads;
                    searchThreadCountField.setText(String.valueOf(cpuThreads));
                } else {
                    return;
                }
            }
            
            // 验证XZ坐标
            String minXText = minXField.getText().trim();
            String maxXText = maxXField.getText().trim();
            String minZText = minZField.getText().trim();
            String maxZText = maxZField.getText().trim();
            
            // 检查是否为整数
            double minXDouble, maxXDouble, minZDouble, maxZDouble;
            try {
                minXDouble = Double.parseDouble(minXText);
                if (minXDouble != Math.floor(minXDouble)) {
                    JOptionPane.showMessageDialog(this, "MinX必须为整数，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                    minXField.setText(String.valueOf(DEFAULT_MIN_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "MinX格式错误，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                minXField.setText(String.valueOf(DEFAULT_MIN_X));
                return;
            }
            
            try {
                maxXDouble = Double.parseDouble(maxXText);
                if (maxXDouble != Math.floor(maxXDouble)) {
                    JOptionPane.showMessageDialog(this, "MaxX必须为整数，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                    maxXField.setText(String.valueOf(DEFAULT_MAX_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "MaxX格式错误，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                maxXField.setText(String.valueOf(DEFAULT_MAX_X));
                return;
            }
            
            try {
                minZDouble = Double.parseDouble(minZText);
                if (minZDouble != Math.floor(minZDouble)) {
                    JOptionPane.showMessageDialog(this, "MinZ必须为整数，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                    minZField.setText(String.valueOf(DEFAULT_MIN_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "MinZ格式错误，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                minZField.setText(String.valueOf(DEFAULT_MIN_Z));
                return;
            }
            
            try {
                maxZDouble = Double.parseDouble(maxZText);
                if (maxZDouble != Math.floor(maxZDouble)) {
                    JOptionPane.showMessageDialog(this, "MaxZ必须为整数，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                    maxZField.setText(String.valueOf(DEFAULT_MAX_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "MaxZ格式错误，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                maxZField.setText(String.valueOf(DEFAULT_MAX_Z));
                return;
            }
            
            int minX = (int) minXDouble;
            int maxX = (int) maxXDouble;
            int minZ = (int) minZDouble;
            int maxZ = (int) maxZDouble;
            
            if (minX >= maxX) {
                JOptionPane.showMessageDialog(this, "MinX 必须小于 MaxX", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (minZ >= maxZ) {
                JOptionPane.showMessageDialog(this, "MinZ 必须小于 MaxZ", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查世界边界：minX < -58594, maxX > 58593, minZ < -58594, maxZ > 58593
            boolean outOfBounds = minX < DEFAULT_MIN_X || maxX > DEFAULT_MAX_X || minZ < DEFAULT_MIN_Z || maxZ > DEFAULT_MAX_Z;

            if (outOfBounds) {
                int result = JOptionPane.showConfirmDialog(
                    this,
                    "输入的值超出世界边界，搜索出的神殿可能无法抵达，是否继续？",
                    "警告",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            // 保存当前参数
            lastSearchSeed = seed;
            lastSearchMinX = minX;
            lastSearchMaxX = maxX;
            lastSearchMinZ = minZ;
            lastSearchMaxZ = maxZ;
            lastSearchThreadCount = threadCount;

            isSearchRunning = true;
            isSearchPaused = false;
            searchStartButton.setEnabled(false);
            searchPauseButton.setEnabled(true);
            searchPauseButton.setText("暂停");
            searchStopButton.setEnabled(true);
            searchResetButton.setEnabled(false);
            searchSeedField.setEnabled(false);
            searchThreadCountField.setEnabled(false); // 运行中不能修改，暂停时可以修改
            versionComboBox.setEnabled(false);
            pauseOnPairFoundCheckBox.setEnabled(false);
            minXField.setEnabled(false);
            maxXField.setEnabled(false);
            minZField.setEnabled(false);
            maxZField.setEnabled(false);
            searchResultArea.setText("");
            searchProgressBar.setValue(0);
            searchProgressBar.setString("进度: 0/0 (0.00%)");
            searchElapsedTimeLabel.setText("已过时间: 0天 0时 0分 0秒");
            searchRemainingTimeLabel.setText("剩余时间: 计算中...");

            // 获取选择的版本
            String selectedVersion = (String) versionComboBox.getSelectedItem();
            MCVersion mcVersion = getMCVersion(selectedVersion != null ? selectedVersion : "1.21.1");

            searcher = new SearchMonument(mcVersion);
            boolean pauseOnPairFound = pauseOnPairFoundCheckBox.isSelected();
            searcher.startSearch(seed, threadCount, minX, maxX, minZ, maxZ, this::updateSearchProgress, this::addSearchResult, pauseOnPairFound);

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleSearchPause() {
        if (searcher == null || !isSearchRunning) {
            return;
        }
        
        if (isSearchPaused) {
            // 恢复（线程数变化会在startSearch中处理）
            searcher.resume();
            isSearchPaused = false;
            searchPauseButton.setText("暂停");
            searchThreadCountField.setEnabled(false); // 恢复后不能修改线程数
        } else {
            // 暂停
            searcher.pause();
            isSearchPaused = true;
            searchPauseButton.setText("继续");
            searchThreadCountField.setEnabled(true); // 暂停时可以修改线程数
        }
    }

    private void stopSearch() {
        if (searcher != null) {
            searcher.stop();
        }
        isSearchRunning = false;
        isSearchPaused = false;
        searchStartButton.setEnabled(true);
        searchPauseButton.setEnabled(false);
        searchPauseButton.setText("暂停");
        searchStopButton.setEnabled(false);
        searchResetButton.setEnabled(true);
        searchSeedField.setEnabled(true);
        searchThreadCountField.setEnabled(true);
        versionComboBox.setEnabled(true);
        pauseOnPairFoundCheckBox.setEnabled(true);
        minXField.setEnabled(true);
        maxXField.setEnabled(true);
        minZField.setEnabled(true);
        maxZField.setEnabled(true);
        searchRemainingTimeLabel.setText("剩余时间: 已停止");
    }

    private void resetSearchToDefaults() {
        minXField.setText(String.valueOf(DEFAULT_MIN_X));
        maxXField.setText(String.valueOf(DEFAULT_MAX_X));
        minZField.setText(String.valueOf(DEFAULT_MIN_Z));
        maxZField.setText(String.valueOf(DEFAULT_MAX_Z));
    }

    private void updateSearchProgress(SearchMonument.ProgressInfo info) {
        SwingUtilities.invokeLater(() -> {
            if (!isSearchRunning) return;

            int progress = (int) Math.min(100, info.percentage);
            searchProgressBar.setValue(progress);
            // 将进度信息显示在进度条中
            searchProgressBar.setString(String.format("进度: %d/%d (%.2f%%)", info.processed, info.total, info.percentage));
            
            // 暂停时不更新时间
            if (!isSearchPaused) {
                searchElapsedTimeLabel.setText("已过时间: " + formatTime(info.elapsedMs));
                if (info.remainingMs > 0) {
                    searchRemainingTimeLabel.setText("剩余时间: " + formatTime(info.remainingMs));
                } else {
                    searchRemainingTimeLabel.setText("剩余时间: 计算中...");
                }
            } else {
                searchRemainingTimeLabel.setText("剩余时间: 已暂停");
            }

            if (info.processed >= info.total) {
                isSearchRunning = false;
                isSearchPaused = false;
                searchStartButton.setEnabled(true);
                searchPauseButton.setEnabled(false);
                searchPauseButton.setText("暂停");
                searchStopButton.setEnabled(false);
                searchResetButton.setEnabled(true);
                searchSeedField.setEnabled(true);
                searchThreadCountField.setEnabled(true);
                versionComboBox.setEnabled(true);
                pauseOnPairFoundCheckBox.setEnabled(true);
                minXField.setEnabled(true);
                maxXField.setEnabled(true);
                minZField.setEnabled(true);
                maxZField.setEnabled(true);
                // 不再弹框，只在进度条中显示完成
                searchProgressBar.setString(String.format("进度: %d/%d (100.00%%) - 完成", info.processed, info.total));
                searchRemainingTimeLabel.setText("剩余时间: 已完成");
            }
        });
    }

    private void addSearchResult(String result) {
        SwingUtilities.invokeLater(() -> {
            searchResultArea.append(result + "\n");
            searchResultArea.setCaretPosition(searchResultArea.getDocument().getLength());
        });
    }

    private void exportSearchResults() {
        if (searchResultArea.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有结果可导出", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导出搜索结果");
        fileChooser.setFileFilter(new FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        fileChooser.setSelectedFile(new File("search_output.txt"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                writer.print(searchResultArea.getText());
                JOptionPane.showMessageDialog(this, "导出成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "导出失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d天 %d时 %d分 %d秒", days, hours, minutes, seconds);
    }

    /**
     * 设置窗口图标
     * 图标文件应放在 src/main/resources/icon.png 或 icon.ico
     */
    private void setWindowIcon() {
        try {
            // 尝试从资源文件加载图标
            java.net.URL iconURL = getClass().getResource("/icon.png");
            if (iconURL == null) {
                iconURL = getClass().getResource("/icon.ico");
            }
            if (iconURL != null) {
                ImageIcon icon = new ImageIcon(iconURL);
                setIconImage(icon.getImage());
            } else {
                // 如果没有找到图标文件，可以创建一个简单的默认图标
                // 或者使用系统默认图标（不设置）
                System.out.println("提示: 未找到图标文件 (icon.png 或 icon.ico)，使用系统默认图标");
            }
        } catch (Exception e) {
            System.err.println("设置图标时出错: " + e.getMessage());
        }
    }
    
    /**
     * 设置字体
     * 从资源文件加载 font.ttf 字体
     */
    private void setChineseFont() {
        try {
            // 从资源文件加载 font.ttf
            java.io.InputStream fontStream = getClass().getResourceAsStream("/font.ttf");
            if (fontStream == null) {
                System.err.println("错误: 未找到字体文件 font.ttf，请确保文件位于 src/main/resources/font.ttf");
                return;
            }
            
            // 创建字体
            Font font = Font.createFont(Font.TRUETYPE_FONT, fontStream);
            fontStream.close();
            
            // 注册字体到系统
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(font);
            
            // 创建指定大小的字体并保存
            loadedFont = font.deriveFont(Font.PLAIN, 12f);
            
            // 设置全局字体
            UIManager.put("Label.font", loadedFont);
            UIManager.put("Button.font", loadedFont);
            UIManager.put("TextField.font", loadedFont);
            UIManager.put("TextArea.font", loadedFont);
            UIManager.put("ComboBox.font", loadedFont);
            UIManager.put("TabbedPane.font", loadedFont);
            UIManager.put("ProgressBar.font", loadedFont);
            UIManager.put("ToolTip.font", loadedFont);
            UIManager.put("Menu.font", loadedFont);
            UIManager.put("MenuItem.font", loadedFont);
            UIManager.put("CheckBox.font", loadedFont);
            UIManager.put("RadioButton.font", loadedFont);
            UIManager.put("List.font", loadedFont);
            UIManager.put("Table.font", loadedFont);
            UIManager.put("Tree.font", loadedFont);
            
            System.out.println("成功加载字体: " + font.getFontName() + " (大小: " + loadedFont.getSize() + ")");
        } catch (FontFormatException e) {
            System.err.println("字体文件格式错误: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("读取字体文件时出错: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("加载字体时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取加载的字体，如果未加载则返回默认字体
     */
    private Font getLoadedFont() {
        if (loadedFont != null) {
            return loadedFont;
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }
    
    /**
     * 将版本字符串转换为 MCVersion
     */
    private MCVersion getMCVersion(String versionString) {
        switch (versionString) {
            case "1.18.2":
                return MCVersion.v1_18_2;
            case "1.19.2":
                return MCVersion.v1_19_2;
            case "1.20.1":
                return MCVersion.v1_20_1;
            case "1.21.1":
            default:
                return MCVersion.v1_21;
        }
    }

    // 创建批量搜索面板（第二个tab）
    private JPanel createBatchSearchPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 左侧：输入和进度
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        // 输入区域
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // 种子文件导入
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel seedFileLabel = new JLabel("种子文件:");
        seedFileLabel.setFont(getLoadedFont());
        inputPanel.add(seedFileLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchImportButton = new JButton("选择文件");
        batchImportButton.setFont(getLoadedFont());
        batchImportButton.addActionListener(e -> importSeedFile());
        inputPanel.add(batchImportButton, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 2;
        batchSeedFileLabel = new JLabel("未选择文件");
        batchSeedFileLabel.setFont(getLoadedFont());
        inputPanel.add(batchSeedFileLabel, gbc);
        gbc.gridwidth = 1;

        // Thread Count 输入
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel threadLabel = new JLabel("线程数:");
        threadLabel.setFont(getLoadedFont());
        inputPanel.add(threadLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchThreadCountField = new JTextField(String.valueOf(DEFAULT_THREAD_COUNT), 20);
        batchThreadCountField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchThreadCountField, "线程数");
            }
        });
        inputPanel.add(batchThreadCountField, gbc);

        // 版本选择下拉框
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel versionLabel = new JLabel("版本:");
        versionLabel.setFont(getLoadedFont());
        inputPanel.add(versionLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        String[] versionOptions = {"1.21.1", "1.20.1", "1.19.2", "1.18.2"};
        batchVersionComboBox = new JComboBox<>(versionOptions);
        batchVersionComboBox.setSelectedIndex(0);
        inputPanel.add(batchVersionComboBox, gbc);

        // MinX 输入
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel minXLabel = new JLabel("MinX(x512):");
        minXLabel.setFont(getLoadedFont());
        inputPanel.add(minXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchMinXField = new JTextField(String.valueOf(BATCH_DEFAULT_MIN_X), 20);
        batchMinXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchMinXField, "MinX");
            }
        });
        inputPanel.add(batchMinXField, gbc);

        // MaxX 输入
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel maxXLabel = new JLabel("MaxX(x512):");
        maxXLabel.setFont(getLoadedFont());
        inputPanel.add(maxXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchMaxXField = new JTextField(String.valueOf(BATCH_DEFAULT_MAX_X), 20);
        batchMaxXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchMaxXField, "MaxX");
            }
        });
        inputPanel.add(batchMaxXField, gbc);

        // MinZ 输入
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel minZLabel = new JLabel("MinZ(x512):");
        minZLabel.setFont(getLoadedFont());
        inputPanel.add(minZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchMinZField = new JTextField(String.valueOf(BATCH_DEFAULT_MIN_Z), 20);
        batchMinZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchMinZField, "MinZ");
            }
        });
        inputPanel.add(batchMinZField, gbc);

        // MaxZ 输入
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel maxZLabel = new JLabel("MaxZ(x512):");
        maxZLabel.setFont(getLoadedFont());
        inputPanel.add(maxZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchMaxZField = new JTextField(String.valueOf(BATCH_DEFAULT_MAX_Z), 20);
        batchMaxZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchMaxZField, "MaxZ");
            }
        });
        inputPanel.add(batchMaxZField, gbc);

        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout());
        batchStartButton = new JButton("开始搜索");
        batchPauseButton = new JButton("暂停");
        batchStopButton = new JButton("停止");
        batchResetButton = new JButton("重置搜索区域");
        batchPauseButton.setEnabled(false);
        batchStopButton.setEnabled(false);
        buttonPanel.add(batchStartButton);
        buttonPanel.add(batchPauseButton);
        buttonPanel.add(batchStopButton);
        buttonPanel.add(batchResetButton);

        // 静态文字展示区域（放在按钮上方）
        JPanel creditPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        creditPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel creditLabel = new JLabel("<html><div style='text-align: center;'><br><br><br><br><br><br><br>作者：b站@SunnySlopes<br>字体：江城黑体</div></html>");
        creditLabel.setFont(getLoadedFont());
        creditPanel.add(creditLabel);

        // 将 credit 和按钮放在一个容器中，credit 在上，按钮在下
        JPanel creditButtonPanel = new JPanel(new BorderLayout());
        creditButtonPanel.add(creditPanel, BorderLayout.NORTH);
        creditButtonPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 进度区域
        JPanel progressPanel = new JPanel(new GridBagLayout());
        GridBagConstraints pgc = new GridBagConstraints();
        pgc.insets = new Insets(5, 5, 5, 5);
        pgc.anchor = GridBagConstraints.WEST;
        pgc.fill = GridBagConstraints.HORIZONTAL;
        pgc.weightx = 1.0;

        pgc.gridx = 0;
        pgc.gridy = 0;
        pgc.gridwidth = 2;
        batchProgressBar = new JProgressBar(0, 100);
        batchProgressBar.setStringPainted(true);
        batchProgressBar.setString("进度: 0/0 (0.00%)");
        progressPanel.add(batchProgressBar, pgc);

        pgc.gridwidth = 1;
        pgc.gridy = 1;
        batchElapsedTimeLabel = new JLabel("已过时间: 0天 0时 0分 0秒");
        progressPanel.add(batchElapsedTimeLabel, pgc);

        pgc.gridy = 3;
        batchRemainingTimeLabel = new JLabel("剩余时间: 计算中...");
        progressPanel.add(batchRemainingTimeLabel, pgc);

        leftPanel.add(inputPanel, BorderLayout.NORTH);
        leftPanel.add(creditButtonPanel, BorderLayout.CENTER);
        
        // 将进度区域放在另一个容器中
        JPanel leftBottomPanel = new JPanel(new BorderLayout());
        leftBottomPanel.add(progressPanel, BorderLayout.CENTER);
        
        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.add(leftPanel, BorderLayout.CENTER);
        leftContainer.add(leftBottomPanel, BorderLayout.SOUTH);

        // 右侧：结果显示
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("检查结果(输出格式: /tp x 50 z (差值: dx=?, dz=?))"));
        batchResultArea = new JTextArea();
        batchResultArea.setEditable(false);
        batchResultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(batchResultArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel exportSortPanel = new JPanel(new FlowLayout());
        batchExportFullButton = new JButton("导出完整结果");
        batchExportFullButton.addActionListener(e -> exportBatchResults(true));
        batchExportSeedsButton = new JButton("导出种子列表");
        batchExportSeedsButton.addActionListener(e -> exportBatchResults(false));
        batchSortButton = new JButton("排序");
        batchSortButton.addActionListener(e -> sortBatchResults());
        exportSortPanel.add(batchExportFullButton);
        exportSortPanel.add(batchExportSeedsButton);
        exportSortPanel.add(batchSortButton);
        rightPanel.add(scrollPane, BorderLayout.CENTER);
        rightPanel.add(exportSortPanel, BorderLayout.SOUTH);

        // 使用 JSplitPane 分割
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, rightPanel);
        splitPane.setDividerLocation(450);
        splitPane.setResizeWeight(0.5);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 添加事件监听
        batchStartButton.addActionListener(e -> startBatchSearch());
        batchPauseButton.addActionListener(e -> toggleBatchSearchPause());
        batchStopButton.addActionListener(e -> stopBatchSearch());
        batchResetButton.addActionListener(e -> resetBatchSearchToDefaults());

        return mainPanel;
    }

    // 导入种子文件
    private void importSeedFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择种子文件");
        fileChooser.setFileFilter(new FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                batchSeedList.clear();
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    try {
                        long seed = Long.parseLong(line);
                        batchSeedList.add(seed);
                    } catch (NumberFormatException e) {
                        System.err.println("第" + lineNum + "行不是有效的种子: " + line);
                    }
                }
                reader.close();
                batchSeedFileName = file.getName();
                batchSeedFileLabel.setText("已选择: " + batchSeedFileName + " (" + batchSeedList.size() + " 个种子)");
                JOptionPane.showMessageDialog(this, "成功导入 " + batchSeedList.size() + " 个种子", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "读取文件失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 开始批量搜索
    private void startBatchSearch() {
        if (batchSeedList.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先导入种子文件", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            // 验证线程数
            String threadText = batchThreadCountField.getText().trim();
            double threadDouble;
            try {
                threadDouble = Double.parseDouble(threadText);
                if (threadDouble != Math.floor(threadDouble)) {
                    JOptionPane.showMessageDialog(this, "线程数必须为整数", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "线程数格式错误，请输入整数", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            int threadCount = (int) threadDouble;
            if (threadCount < 1) {
                JOptionPane.showMessageDialog(this, "线程数必须大于0", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查线程数是否超过CPU核数
            int cpuThreads = Runtime.getRuntime().availableProcessors();
            if (threadCount > cpuThreads) {
                int result = JOptionPane.showConfirmDialog(
                    this,
                    "线程数超过CPU核数（" + cpuThreads + "），是否自动调整为" + cpuThreads + "？",
                    "提示",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                );
                if (result == JOptionPane.YES_OPTION) {
                    threadCount = cpuThreads;
                    batchThreadCountField.setText(String.valueOf(cpuThreads));
                } else {
                    return;
                }
            }
            
            // 验证XZ坐标
            String minXText = batchMinXField.getText().trim();
            String maxXText = batchMaxXField.getText().trim();
            String minZText = batchMinZField.getText().trim();
            String maxZText = batchMaxZField.getText().trim();
            
            // 检查是否为整数
            double minXDouble, maxXDouble, minZDouble, maxZDouble;
            try {
                minXDouble = Double.parseDouble(minXText);
                if (minXDouble != Math.floor(minXDouble)) {
                    JOptionPane.showMessageDialog(this, "MinX必须为整数，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                    batchMinXField.setText(String.valueOf(BATCH_DEFAULT_MIN_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "MinX格式错误，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                batchMinXField.setText(String.valueOf(BATCH_DEFAULT_MIN_X));
                return;
            }
            
            try {
                maxXDouble = Double.parseDouble(maxXText);
                if (maxXDouble != Math.floor(maxXDouble)) {
                    JOptionPane.showMessageDialog(this, "MaxX必须为整数，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                    batchMaxXField.setText(String.valueOf(BATCH_DEFAULT_MAX_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "MaxX格式错误，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                batchMaxXField.setText(String.valueOf(BATCH_DEFAULT_MAX_X));
                return;
            }
            
            try {
                minZDouble = Double.parseDouble(minZText);
                if (minZDouble != Math.floor(minZDouble)) {
                    JOptionPane.showMessageDialog(this, "MinZ必须为整数，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                    batchMinZField.setText(String.valueOf(BATCH_DEFAULT_MIN_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "MinZ格式错误，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                batchMinZField.setText(String.valueOf(BATCH_DEFAULT_MIN_Z));
                return;
            }
            
            try {
                maxZDouble = Double.parseDouble(maxZText);
                if (maxZDouble != Math.floor(maxZDouble)) {
                    JOptionPane.showMessageDialog(this, "MaxZ必须为整数，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                    batchMaxZField.setText(String.valueOf(BATCH_DEFAULT_MAX_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "MaxZ格式错误，已重置为默认值", "错误", JOptionPane.ERROR_MESSAGE);
                batchMaxZField.setText(String.valueOf(BATCH_DEFAULT_MAX_Z));
                return;
            }
            
            int minX = (int) minXDouble;
            int maxX = (int) maxXDouble;
            int minZ = (int) minZDouble;
            int maxZ = (int) maxZDouble;
            
            if (minX >= maxX) {
                JOptionPane.showMessageDialog(this, "MinX 必须小于 MaxX", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (minZ >= maxZ) {
                JOptionPane.showMessageDialog(this, "MinZ 必须小于 MaxZ", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 获取选择的版本
            String selectedVersion = (String) batchVersionComboBox.getSelectedItem();
            MCVersion mcVersion = getMCVersion(selectedVersion != null ? selectedVersion : "1.21.1");
            
            // 创建final副本用于lambda表达式
            final int finalThreadCount = threadCount;
            final int finalMinX = minX;
            final int finalMaxX = maxX;
            final int finalMinZ = minZ;
            final int finalMaxZ = maxZ;
            
            // 开始批量搜索
            isBatchSearchRunning = true;
            isBatchSearchPaused = false;
            batchStartButton.setEnabled(false);
            batchPauseButton.setEnabled(true);
            batchPauseButton.setText("暂停");
            batchStopButton.setEnabled(true);
            batchResetButton.setEnabled(false);
            batchImportButton.setEnabled(false);
            batchThreadCountField.setEnabled(false);
            batchVersionComboBox.setEnabled(false);
            batchMinXField.setEnabled(false);
            batchMaxXField.setEnabled(false);
            batchMinZField.setEnabled(false);
            batchMaxZField.setEnabled(false);
            batchResultArea.setText("");
            batchProgressBar.setValue(0);
            batchProgressBar.setString("进度: 0/" + batchSeedList.size() + " (0.00%)");
            batchElapsedTimeLabel.setText("已过时间: 0天 0时 0分 0秒");
            batchRemainingTimeLabel.setText("剩余时间: 计算中...");
            
            // 在新线程中执行批量搜索
            new Thread(() -> {
                long startTime = System.currentTimeMillis();
                int processed = 0;
                int total = batchSeedList.size();
                
                for (long seed : batchSeedList) {
                    if (!isBatchSearchRunning) {
                        break;
                    }
                    
                    // 为每个种子创建搜索器
                    SearchMonument batchSearcher = new SearchMonument(mcVersion);
                    synchronized (this) {
                        currentBatchSearcher = batchSearcher;
                    }
                    final long currentSeed = seed;
                    
                    // 执行搜索
                    final List<String> seedResultStrings = new ArrayList<>();
                    batchSearcher.startSearch(seed, finalThreadCount, finalMinX, finalMaxX, finalMinZ, finalMaxZ,
                        null, // 不显示单个种子的进度
                        result -> {
                            synchronized (seedResultStrings) {
                                seedResultStrings.add(result);
                            }
                        },
                        false // 不暂停
                    );
                    
                    // 等待搜索完成
                    while (batchSearcher.isRunning()) {
                        // 检查暂停状态
                        if (isBatchSearchPaused) {
                            batchSearcher.pause();
                        } else {
                            batchSearcher.resume();
                        }
                        
                        // 暂停时等待
                        while (isBatchSearchPaused && isBatchSearchRunning) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        
                        if (!isBatchSearchRunning) {
                            batchSearcher.stop();
                            break;
                        }
                        
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    
                    synchronized (this) {
                        if (currentBatchSearcher == batchSearcher) {
                            currentBatchSearcher = null;
                        }
                    }
                    
                    // 获取所有结果并解析
                    List<String> allResults = batchSearcher.getResults();
                    List<ResultItem> resultItems = new ArrayList<>();
                    
                    for (String resultStr : allResults) {
                        // 解析结果字符串
                        // 格式: /tp x 50 z (差值: dx=?, dz=?) 或带后缀
                        String[] parts = resultStr.split("\\s+");
                        if (parts.length >= 4 && parts[0].equals("/tp")) {
                            try {
                                int centerX = Integer.parseInt(parts[1]);
                                int centerZ = Integer.parseInt(parts[3]);
                                // 从差值中提取dx和dz
                                int dx = 0, dz = 0;
                                boolean isLowEfficiency = resultStr.contains("效率会略微损失");
                                
                                // 查找dx和dz
                                for (int i = 0; i < parts.length; i++) {
                                    if (parts[i].startsWith("dx=")) {
                                        String dxStr = parts[i].substring(3);
                                        if (dxStr.endsWith(",")) {
                                            dxStr = dxStr.substring(0, dxStr.length() - 1);
                                        }
                                        dx = Integer.parseInt(dxStr);
                                    }
                                    if (parts[i].startsWith("dz=")) {
                                        String dzStr = parts[i].substring(3);
                                        if (dzStr.endsWith(")")) {
                                            dzStr = dzStr.substring(0, dzStr.length() - 1);
                                        }
                                        dz = Integer.parseInt(dzStr);
                                    }
                                }
                                
                                resultItems.add(new ResultItem(centerX, centerZ, dx, dz, isLowEfficiency));
                            } catch (NumberFormatException e) {
                                // 忽略解析错误
                            }
                        }
                    }
                    
                    // 只有当有结果时才输出
                    if (!resultItems.isEmpty()) {
                        // 按距离原点由近及远排序（x²+z²）
                        resultItems.sort((a, b) -> {
                            int distA = a.centerX * a.centerX + a.centerZ * a.centerZ;
                            int distB = b.centerX * b.centerX + b.centerZ * b.centerZ;
                            return Integer.compare(distA, distB);
                        });
                        
                        // 输出结果
                        final List<ResultItem> finalResults = resultItems;
                        SwingUtilities.invokeLater(() -> {
                            batchResultArea.append(String.valueOf(currentSeed) + "\n");
                            for (ResultItem item : finalResults) {
                                String resultLine = String.format("/tp %d 50 %d (差值: dx=%d, dz=%d)",
                                    item.centerX, item.centerZ, item.dx, item.dz);
                                if (item.isLowEfficiency) {
                                    resultLine += " - 效率会略微损失";
                                }
                                batchResultArea.append(resultLine + "\n");
                            }
                        });
                    }
                    
                    processed++;
                    final int currentProcessed = processed;
                    final long elapsed = System.currentTimeMillis() - startTime;
                    final long remaining = processed > 0 ? (elapsed * (total - processed) / processed) : 0;
                    
                    SwingUtilities.invokeLater(() -> {
                        double percentage = (double) currentProcessed / total * 100.0;
                        batchProgressBar.setValue((int) Math.min(100, percentage));
                        batchProgressBar.setString(String.format("进度: %d/%d (%.2f%%)", currentProcessed, total, percentage));
                        batchElapsedTimeLabel.setText("已过时间: " + formatTime(elapsed));
                        if (!isBatchSearchPaused) {
                            if (remaining > 0) {
                                batchRemainingTimeLabel.setText("剩余时间: " + formatTime(remaining));
                            } else {
                                batchRemainingTimeLabel.setText("剩余时间: 计算中...");
                            }
                        }
                    });
                }
                
                SwingUtilities.invokeLater(() -> {
                    isBatchSearchRunning = false;
                    isBatchSearchPaused = false;
                    batchStartButton.setEnabled(true);
                    batchPauseButton.setEnabled(false);
                    batchPauseButton.setText("暂停");
                    batchStopButton.setEnabled(false);
                    batchResetButton.setEnabled(true);
                    batchImportButton.setEnabled(true);
                    batchThreadCountField.setEnabled(true);
                    batchVersionComboBox.setEnabled(true);
                    batchMinXField.setEnabled(true);
                    batchMaxXField.setEnabled(true);
                    batchMinZField.setEnabled(true);
                    batchMaxZField.setEnabled(true);
                    batchProgressBar.setString(String.format("进度: %d/%d (100.00%%) - 完成", total, total));
                    batchRemainingTimeLabel.setText("剩余时间: 已完成");
                });
            }).start();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "启动搜索失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            isBatchSearchRunning = false;
            isBatchSearchPaused = false;
            batchStartButton.setEnabled(true);
            batchPauseButton.setEnabled(false);
            batchPauseButton.setText("暂停");
            batchStopButton.setEnabled(false);
            batchResetButton.setEnabled(true);
            batchImportButton.setEnabled(true);
            batchThreadCountField.setEnabled(true);
            batchVersionComboBox.setEnabled(true);
            batchMinXField.setEnabled(true);
            batchMaxXField.setEnabled(true);
            batchMinZField.setEnabled(true);
            batchMaxZField.setEnabled(true);
        }
    }

    // 切换批量搜索暂停状态
    private void toggleBatchSearchPause() {
        if (!isBatchSearchRunning) {
            return;
        }
        
        if (isBatchSearchPaused) {
            // 恢复
            isBatchSearchPaused = false;
            batchPauseButton.setText("暂停");
            batchThreadCountField.setEnabled(false);
            synchronized (this) {
                if (currentBatchSearcher != null) {
                    currentBatchSearcher.resume();
                }
            }
        } else {
            // 暂停
            isBatchSearchPaused = true;
            batchPauseButton.setText("继续");
            batchThreadCountField.setEnabled(true);
            synchronized (this) {
                if (currentBatchSearcher != null) {
                    currentBatchSearcher.pause();
                }
            }
            batchRemainingTimeLabel.setText("剩余时间: 已暂停");
        }
    }

    // 停止批量搜索
    private void stopBatchSearch() {
        isBatchSearchRunning = false;
        isBatchSearchPaused = false;
        synchronized (this) {
            if (currentBatchSearcher != null) {
                currentBatchSearcher.stop();
                currentBatchSearcher = null;
            }
        }
        batchStartButton.setEnabled(true);
        batchPauseButton.setEnabled(false);
        batchPauseButton.setText("暂停");
        batchStopButton.setEnabled(false);
        batchResetButton.setEnabled(true);
        batchImportButton.setEnabled(true);
        batchThreadCountField.setEnabled(true);
        batchVersionComboBox.setEnabled(true);
        batchMinXField.setEnabled(true);
        batchMaxXField.setEnabled(true);
        batchMinZField.setEnabled(true);
        batchMaxZField.setEnabled(true);
        batchRemainingTimeLabel.setText("剩余时间: 已停止");
    }

    // 重置批量搜索区域
    private void resetBatchSearchToDefaults() {
        batchMinXField.setText(String.valueOf(BATCH_DEFAULT_MIN_X));
        batchMaxXField.setText(String.valueOf(BATCH_DEFAULT_MAX_X));
        batchMinZField.setText(String.valueOf(BATCH_DEFAULT_MIN_Z));
        batchMaxZField.setText(String.valueOf(BATCH_DEFAULT_MAX_Z));
    }

    // 导出批量搜索结果
    private void exportBatchResults(boolean fullFormat) {
        String text = batchResultArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有结果可导出", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(fullFormat ? "导出完整结果" : "导出种子列表");
        fileChooser.setFileFilter(new FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        fileChooser.setSelectedFile(new File(fullFormat ? "batch_output.txt" : "batch_seeds.txt"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                if (fullFormat) {
                    writer.print(text);
                } else {
                    // 只导出种子列表
                    String[] lines = text.split("\n");
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }
                        // 如果是种子行（不包含/tp），则输出
                        if (!line.startsWith("/tp")) {
                            writer.println(line);
                        }
                    }
                }
                JOptionPane.showMessageDialog(this, "导出成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "导出失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 排序批量搜索结果
    private void sortBatchResults() {
        String text = batchResultArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        
        // 解析文本，按种子分组
        String[] lines = text.split("\n");
        List<SeedResultGroup> groups = new ArrayList<>();
        SeedResultGroup currentGroup = null;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("/tp")) {
                // 这是结果行
                if (currentGroup != null) {
                    currentGroup.results.add(line);
                }
            } else {
                // 这是种子行
                if (currentGroup != null) {
                    groups.add(currentGroup);
                }
                currentGroup = new SeedResultGroup();
                currentGroup.seed = line;
                currentGroup.results = new ArrayList<>();
            }
        }
        if (currentGroup != null) {
            groups.add(currentGroup);
        }
        
        // 对每个种子的结果进行排序，并计算最近距离
        for (SeedResultGroup group : groups) {
            if (!group.results.isEmpty()) {
                group.results.sort((a, b) -> {
                    // 提取坐标
                    int distA = extractDistanceSquared(a);
                    int distB = extractDistanceSquared(b);
                    return Integer.compare(distA, distB);
                });
                // 计算该种子中离原点最近的距离（第一个结果就是最近的）
                group.minDistance = extractDistanceSquared(group.results.get(0));
            } else {
                group.minDistance = Integer.MAX_VALUE;
            }
        }
        
        // 按照每个种子中离原点最近的距离对所有种子进行排序
        groups.sort((a, b) -> {
            return Integer.compare(a.minDistance, b.minDistance);
        });
        
        // 重新组合文本（只输出有结果的种子）
        StringBuilder sb = new StringBuilder();
        for (SeedResultGroup group : groups) {
            // 只输出有结果的种子组
            if (!group.results.isEmpty()) {
                sb.append(group.seed).append("\n");
                for (String result : group.results) {
                    sb.append(result).append("\n");
                }
            }
        }
        
        batchResultArea.setText(sb.toString());
    }
    
    // 从结果行中提取距离的平方
    private int extractDistanceSquared(String resultLine) {
        try {
            String[] parts = resultLine.split("\\s+");
            if (parts.length >= 4 && parts[0].equals("/tp")) {
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);
                return x * x + z * z;
            }
        } catch (NumberFormatException e) {
            // 忽略
        }
        return Integer.MAX_VALUE;
    }
    
    // 种子结果组
    private static class SeedResultGroup {
        String seed;
        List<String> results;
        int minDistance; // 该种子中离原点最近的距离
    }
    
    // 结果项
    private static class ResultItem {
        int centerX;
        int centerZ;
        int dx;
        int dz;
        boolean isLowEfficiency;
        
        ResultItem(int centerX, int centerZ, int dx, int dz, boolean isLowEfficiency) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.dx = dx;
            this.dz = dz;
            this.isLowEfficiency = isLowEfficiency;
        }
    }

    public static void main(String[] args) {
        // 注意：系统属性应该在 Launcher 中设置
        // 这里只保留必要的初始化逻辑
        
        // 在主线程中预先初始化 SeedCheckerSettings，避免在多线程环境中初始化
        // 使用 try-catch 来捕获可能的初始化错误，但继续执行程序
        try {
            SeedCheckerInitializer.initialize();
        } catch (ExceptionInInitializerError e) {
            // 如果初始化失败，打印警告但继续执行
            System.err.println("Warning: SeedChecker initialization failed, but continuing...");
            System.err.println("You may need to run the JAR with: java -Dlog4j2.callerClass=project.Launcher -Dlog4j2.enable.threadlocals=false -jar ...");
        }
        
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new DoubleMonumentForFixedSeed().setVisible(true);
        });
    }
}

