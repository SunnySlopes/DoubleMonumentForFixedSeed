package project;

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

    private static final int WORLD_BOUND = 58594;
    private static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    private JTabbedPane tabbedPane;
    private JComboBox<String> languageComboBox;
    private JLabel searchSeedLabel;
    private JLabel searchThreadCountLabel;
    private JLabel searchVersionLabel;
    private JLabel searchPairLevelLabel;
    private JComboBox<String> pairLevelComboBox;
    private JLabel searchMinXLabel;
    private JLabel searchMaxXLabel;
    private JLabel searchMinZLabel;
    private JLabel searchMaxZLabel;
    private JLabel searchLanguageLabel;
    private JLabel searchCreditLabel;
    private JPanel searchRightPanel;
    private JLabel batchSeedFileTitleLabel;
    private JLabel batchThreadCountLabel;
    private JLabel batchVersionLabel;
    private JLabel batchPairLevelLabel;
    private JComboBox<String> batchPairLevelComboBox;
    private JLabel batchMinXLabel;
    private JLabel batchMaxXLabel;
    private JLabel batchMinZLabel;
    private JLabel batchMaxZLabel;
    private JLabel batchCreditLabel;
    private JPanel batchRightPanel;

    private JTextField searchSeedField;
    private JTextField searchThreadCountField;
    private JComboBox<String> versionComboBox;
    private JCheckBox pauseOnPairFoundCheckBox;
    private JCheckBox biomeCheckCheckBox;
    private JCheckBox batchBiomeCheckCheckBox;
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
    private MonumentSearchRunner searcher;
    private volatile boolean isSearchRunning = false;
    private volatile boolean isSearchPaused = false;
    private long lastSearchSeed = 0;
    private int lastSearchMinX = 0;
    private int lastSearchMaxX = 0;
    private int lastSearchMinZ = 0;
    private int lastSearchMaxZ = 0;
    private int lastSearchThreadCount = 0;
    private long formatContextSeed = 0;
    private int formatContextMc = MonumentFinderBridge.MC_26_2;
    
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
    private MonumentSearchRunner currentBatchSearcher = null;
    private List<Long> batchSeedList = new ArrayList<>();
    private String batchSeedFileName = "";
    
    // 加载的字体
    private Font loadedFont = null;

    public DoubleMonumentForFixedSeed() {
        I18n.initFromSystemDefault();
        setTitle(getString("window.title"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 设置窗口图标
        setWindowIcon();

        // 设置中文字体
        setChineseFont();

        // 创建标签页
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(getLoadedFont());

        // 第一个tab：单种子搜索
        JPanel searchPanel = createSearchPanel();
        tabbedPane.addTab(getString("tab.singleSeedSearch"), searchPanel);

        // 第二个tab：批量种子搜索
        JPanel batchPanel = createBatchSearchPanel();
        tabbedPane.addTab(getString("tab.batchSeedSearch"), batchPanel);

        add(tabbedPane, BorderLayout.CENTER);

        pack();
        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    private String getString(String key) {
        return I18n.get(key);
    }

    private String getString(String key, Object... args) {
        return I18n.get(key, args);
    }

    private boolean isBiomeCheckSelected() {
        if (isBatchSearchRunning) {
            return batchBiomeCheckCheckBox != null && batchBiomeCheckCheckBox.isSelected();
        }
        return biomeCheckCheckBox != null && biomeCheckCheckBox.isSelected();
    }

    private String formatGroupResult(int x, int z, int count, int area, int dx, int dz) {
        String oceanTypeKey = null;
        if (isBiomeCheckSelected()) {
            oceanTypeKey = MonumentFinderBridge.oceanTypeLabel(formatContextSeed, formatContextMc, x, z);
        }
        return I18n.formatResult(x, z, count, area, dx, dz, oceanTypeKey);
    }

    private void fillPairLevelComboItems(JComboBox<String> combo) {
        int prevIndex = combo.getSelectedIndex();
        combo.removeAllItems();
        combo.addItem(getString("pairLevel.2"));
        combo.addItem(getString("pairLevel.3"));
        combo.addItem(getString("pairLevel.4"));
        if (prevIndex >= 0 && prevIndex <= 2) {
            combo.setSelectedIndex(prevIndex);
        } else {
            combo.setSelectedIndex(0);
        }
    }

    private int getSelectedPairLevel(JComboBox<String> combo) {
        int index = combo != null ? combo.getSelectedIndex() : 0;
        return (index >= 0 && index <= 2) ? index + 2 : 2;
    }

    private String nativeErrorMessage() {
        return getString("error.nativeNotLoaded", "");
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
        searchSeedLabel = new JLabel(getString("label.seed"));
        searchSeedLabel.setFont(getLoadedFont());
        inputPanel.add(searchSeedLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchSeedField = new JTextField(loadCachedSeed(), 20);
        searchSeedField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(searchSeedField, getString("label.field.seed"));
            }
        });
        inputPanel.add(searchSeedField, gbc);

        // Thread Count 输入
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchThreadCountLabel = new JLabel(getString("label.threadCount"));
        searchThreadCountLabel.setFont(getLoadedFont());
        inputPanel.add(searchThreadCountLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchThreadCountField = new JTextField(String.valueOf(DEFAULT_THREAD_COUNT), 20);
        searchThreadCountField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(searchThreadCountField, getString("label.field.threadCount"));
            }
        });
        inputPanel.add(searchThreadCountField, gbc);

        // 版本选择下拉框
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchVersionLabel = new JLabel(getString("label.mcVersion"));
        searchVersionLabel.setFont(getLoadedFont());
        inputPanel.add(searchVersionLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        String[] versionOptions = MonumentFinderBridge.SUPPORTED_VERSION_LABELS;
        versionComboBox = new JComboBox<>(versionOptions);
        versionComboBox.setSelectedIndex(0); // 默认选择 26.2
        inputPanel.add(versionComboBox, gbc);

        // 连级选择（筛选目标：2/3/4 连）
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchPairLevelLabel = new JLabel(getString("label.pairLevel"));
        searchPairLevelLabel.setFont(getLoadedFont());
        inputPanel.add(searchPairLevelLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        pairLevelComboBox = new JComboBox<>();
        fillPairLevelComboItems(pairLevelComboBox);
        inputPanel.add(pairLevelComboBox, gbc);

        // MinX 输入
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchMinXLabel = new JLabel(getString("label.minX"));
        searchMinXLabel.setFont(getLoadedFont());
        inputPanel.add(searchMinXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        minXField = new JTextField(String.valueOf(DEFAULT_MIN_X), 20);
        minXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(minXField, getString("label.minX").replace(":", ""));
            }
        });
        inputPanel.add(minXField, gbc);

        // MaxX 输入
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchMaxXLabel = new JLabel(getString("label.maxX"));
        searchMaxXLabel.setFont(getLoadedFont());
        inputPanel.add(searchMaxXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        maxXField = new JTextField(String.valueOf(DEFAULT_MAX_X), 20);
        maxXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(maxXField, getString("label.maxX").replace(":", ""));
            }
        });
        inputPanel.add(maxXField, gbc);

        // MinZ 输入
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchMinZLabel = new JLabel(getString("label.minZ"));
        searchMinZLabel.setFont(getLoadedFont());
        inputPanel.add(searchMinZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        minZField = new JTextField(String.valueOf(DEFAULT_MIN_Z), 20);
        minZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(minZField, getString("label.minZ").replace(":", ""));
            }
        });
        inputPanel.add(minZField, gbc);

        // MaxZ 输入
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchMaxZLabel = new JLabel(getString("label.maxZ"));
        searchMaxZLabel.setFont(getLoadedFont());
        inputPanel.add(searchMaxZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        maxZField = new JTextField(String.valueOf(DEFAULT_MAX_Z), 20);
        maxZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(maxZField, getString("label.maxZ").replace(":", ""));
            }
        });
        inputPanel.add(maxZField, gbc);

        // 语言选择
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        searchLanguageLabel = new JLabel(getString("label.language"));
        searchLanguageLabel.setFont(getLoadedFont());
        inputPanel.add(searchLanguageLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        String[] languageOptions = {"Chinese", "English"};
        languageComboBox = new JComboBox<>(languageOptions);
        if (I18n.getCurrentLocale().getLanguage().equals("zh")) {
            languageComboBox.setSelectedIndex(0);
        } else {
            languageComboBox.setSelectedIndex(1);
        }
        languageComboBox.addActionListener(e -> changeLanguage());
        inputPanel.add(languageComboBox, gbc);

        // 找到二联时立即暂停选项
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 2;
        pauseOnPairFoundCheckBox = new JCheckBox(getString("check.pauseOnPairFound"));
        pauseOnPairFoundCheckBox.setFont(getLoadedFont());
        pauseOnPairFoundCheckBox.setSelected(false);
        inputPanel.add(pauseOnPairFoundCheckBox, gbc);

        // 群系检查选项
        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.gridwidth = 2;
        biomeCheckCheckBox = new JCheckBox(getString("check.biomeCheck"));
        biomeCheckCheckBox.setFont(getLoadedFont());
        biomeCheckCheckBox.setSelected(true);
        inputPanel.add(biomeCheckCheckBox, gbc);
        gbc.gridwidth = 1;

        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout());
        searchStartButton = new JButton(getString("button.startSearch"));
        searchPauseButton = new JButton(getString("button.pause"));
        searchStopButton = new JButton(getString("button.stop"));
        searchResetButton = new JButton(getString("button.reset"));
        searchPauseButton.setEnabled(false);
        searchStopButton.setEnabled(false);
        buttonPanel.add(searchStartButton);
        buttonPanel.add(searchPauseButton);
        buttonPanel.add(searchStopButton);
        buttonPanel.add(searchResetButton);

        // 静态文字展示区域（放在按钮上方）
        JPanel creditPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        creditPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        searchCreditLabel = new JLabel(getString("credit.text"));
        searchCreditLabel.setFont(getLoadedFont());
        creditPanel.add(searchCreditLabel);

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
        searchProgressBar.setString(getString("progress.initial"));
        progressPanel.add(searchProgressBar, pgc);

        pgc.gridwidth = 1;
        pgc.gridy = 1;
        searchElapsedTimeLabel = new JLabel(getString("elapsedTime.initial"));
        progressPanel.add(searchElapsedTimeLabel, pgc);

        pgc.gridy = 3;
        searchRemainingTimeLabel = new JLabel(getString("remainingTime.calculating"));
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
        searchRightPanel = new JPanel(new BorderLayout());
        searchRightPanel.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        searchResultArea = new JTextArea();
        searchResultArea.setEditable(false);
        searchResultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(searchResultArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel exportSortPanel = new JPanel(new FlowLayout());
        searchExportButton = new JButton(getString("button.export"));
        searchExportButton.addActionListener(e -> exportSearchResults());
        searchSortButton = new JButton(getString("button.sort"));
        searchSortButton.addActionListener(e -> sortSearchResults());
        exportSortPanel.add(searchExportButton);
        exportSortPanel.add(searchSortButton);
        searchRightPanel.add(scrollPane, BorderLayout.CENTER);
        searchRightPanel.add(exportSortPanel, BorderLayout.SOUTH);

        // 使用 JSplitPane 分割
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, searchRightPanel);
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
                searchPauseButton.setText(getString("button.pause"));
                searchStopButton.setEnabled(false);
                searchResetButton.setEnabled(true);
                searchSeedField.setEnabled(true);
                searchThreadCountField.setEnabled(true);
                versionComboBox.setEnabled(true);
                pairLevelComboBox.setEnabled(true);
                pauseOnPairFoundCheckBox.setEnabled(true);
        if (biomeCheckCheckBox != null) {
            biomeCheckCheckBox.setEnabled(true);
        }
                minXField.setEnabled(true);
                maxXField.setEnabled(true);
                minZField.setEnabled(true);
                maxZField.setEnabled(true);
                if (languageComboBox != null) {
                    languageComboBox.setEnabled(true);
                }
                searchResultArea.setText("");
                searchProgressBar.setValue(0);
                searchProgressBar.setString(getString("progress.initial"));
                searchRemainingTimeLabel.setText(getString("remainingTime.reset"));
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
                JOptionPane.showMessageDialog(this, getString("validation.integerRequired", fieldName), getString("prompt.inputError"), JOptionPane.ERROR_MESSAGE);
                field.requestFocus();
            }
        } catch (NumberFormatException e) {
            // 不是数字，会在开始运行时验证
        }
    }

    //保存最近一次搜索使用的种子值
    private static java.io.File seedCacheFile() {
        return new java.io.File(System.getProperty("user.home"), ".multimonumentfinder_seed");
    }

    private static String loadCachedSeed() {
        try {
            java.io.File f = seedCacheFile();
            if (!f.isFile()) {
                return "";
            }
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void saveSeedCache(long seed) {
        try {
            java.nio.file.Files.write(seedCacheFile().toPath(),
                    String.valueOf(seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
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
            // 格式：/tp x 50 y (dx=?, dz=?)
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
                    JOptionPane.showMessageDialog(this, getString("error.threadCountRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // 检查线程数是否超过CPU核数
                int cpuThreads = Runtime.getRuntime().availableProcessors();
                if (threadCount > cpuThreads) {
                    int result = JOptionPane.showConfirmDialog(
                        this,
                        getString("error.threadCountExceedsCPU", cpuThreads, cpuThreads),
                        getString("prompt.adjustThreadCount"),
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
                    int mc = MonumentFinderBridge.mcFromVersionString(
                            selectedVersion != null ? selectedVersion : "26.2");

                    if (searcher == null || searcher.getMc() != mc) {
                        searcher = new MonumentSearchRunner(mc);
                    }
                    
                    // 调用startSearch，它会检测到暂停状态并调整线程数
                    String seedText = searchSeedField.getText().trim();
                    long seed = Long.parseLong(seedText);
                    int minX = Integer.parseInt(minXField.getText().trim());
                    int maxX = Integer.parseInt(maxXField.getText().trim());
                    int minZ = Integer.parseInt(minZField.getText().trim());
                    int maxZ = Integer.parseInt(maxZField.getText().trim());
                    
                    formatContextSeed = seed;
                    formatContextMc = mc;
                    boolean pauseOnPairFound = pauseOnPairFoundCheckBox.isSelected();
                    searcher.startSearch(seed, threadCount, minX, maxX, minZ, maxZ,
                            this::updateSearchProgress, this::addSearchResult, pauseOnPairFound,
                            getSelectedPairLevel(pairLevelComboBox),
                            this::formatGroupResult, nativeErrorMessage());
                    
                    lastSearchThreadCount = threadCount;
                    searchPauseButton.setText(getString("button.pause"));
                    searchThreadCountField.setEnabled(false);
                    return;
                } else {
                    // 线程数没变化，直接恢复
                    searcher.resume();
                    isSearchPaused = false;
                    searchPauseButton.setText(getString("button.pause"));
                    searchThreadCountField.setEnabled(false);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.threadCountContinueError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        try {
            // 验证种子
            String seedText = searchSeedField.getText().trim();
            if (seedText.isEmpty()) {
                JOptionPane.showMessageDialog(this, getString("error.seedRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查种子是否为整数
            double seedDouble;
            try {
                seedDouble = Double.parseDouble(seedText);
                if (seedDouble != Math.floor(seedDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.seedMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.seedFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查种子是否超过MC正常种子边界（绝对值超过2^63-1）
            long seed;
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.seedOutOfRange"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 验证线程数
            String threadText = searchThreadCountField.getText().trim();
            double threadDouble;
            try {
                threadDouble = Double.parseDouble(threadText);
                if (threadDouble != Math.floor(threadDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.threadCountMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.threadCountFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            int threadCount = (int) threadDouble;
            if (threadCount < 1) {
                JOptionPane.showMessageDialog(this, getString("error.threadCountRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查线程数是否超过CPU核数
            int cpuThreads = Runtime.getRuntime().availableProcessors();
            if (threadCount > cpuThreads) {
                int result = JOptionPane.showConfirmDialog(
                    this,
                    getString("error.threadCountExceedsCPU", cpuThreads, cpuThreads),
                    getString("prompt.adjustThreadCount"),
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
                    JOptionPane.showMessageDialog(this, getString("error.minXMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    minXField.setText(String.valueOf(DEFAULT_MIN_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.minXFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                minXField.setText(String.valueOf(DEFAULT_MIN_X));
                return;
            }
            
            try {
                maxXDouble = Double.parseDouble(maxXText);
                if (maxXDouble != Math.floor(maxXDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.maxXMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    maxXField.setText(String.valueOf(DEFAULT_MAX_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.maxXFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                maxXField.setText(String.valueOf(DEFAULT_MAX_X));
                return;
            }
            
            try {
                minZDouble = Double.parseDouble(minZText);
                if (minZDouble != Math.floor(minZDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.minZMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    minZField.setText(String.valueOf(DEFAULT_MIN_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.minZFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                minZField.setText(String.valueOf(DEFAULT_MIN_Z));
                return;
            }
            
            try {
                maxZDouble = Double.parseDouble(maxZText);
                if (maxZDouble != Math.floor(maxZDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.maxZMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    maxZField.setText(String.valueOf(DEFAULT_MAX_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.maxZFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                maxZField.setText(String.valueOf(DEFAULT_MAX_Z));
                return;
            }
            
            int minX = (int) minXDouble;
            int maxX = (int) maxXDouble;
            int minZ = (int) minZDouble;
            int maxZ = (int) maxZDouble;
            
            if (minX >= maxX) {
                JOptionPane.showMessageDialog(this, getString("error.minXGreaterThanMaxX"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (minZ >= maxZ) {
                JOptionPane.showMessageDialog(this, getString("error.minZGreaterThanMaxZ"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查世界边界：minX/maxX/minZ/maxZ 中任一个绝对值超过 58594 时提示
            boolean outOfBounds = Math.abs(minX) > WORLD_BOUND || Math.abs(maxX) > WORLD_BOUND
                    || Math.abs(minZ) > WORLD_BOUND || Math.abs(maxZ) > WORLD_BOUND;

            if (outOfBounds) {
                int result = JOptionPane.showConfirmDialog(
                    this,
                    getString("error.outOfBounds"),
                    getString("prompt.warning"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            // 保存当前参数
            lastSearchSeed = seed;
            saveSeedCache(seed);
            lastSearchMinX = minX;
            lastSearchMaxX = maxX;
            lastSearchMinZ = minZ;
            lastSearchMaxZ = maxZ;
            lastSearchThreadCount = threadCount;

            isSearchRunning = true;
            isSearchPaused = false;
            searchStartButton.setEnabled(false);
            searchPauseButton.setEnabled(true);
            searchPauseButton.setText(getString("button.pause"));
            searchStopButton.setEnabled(true);
            searchResetButton.setEnabled(false);
            searchSeedField.setEnabled(false);
            searchThreadCountField.setEnabled(false); // 运行中不能修改，暂停时可以修改
            versionComboBox.setEnabled(false);
            pairLevelComboBox.setEnabled(false);
            pauseOnPairFoundCheckBox.setEnabled(false);
            if (biomeCheckCheckBox != null) {
                biomeCheckCheckBox.setEnabled(false);
            }
            if (languageComboBox != null) {
                languageComboBox.setEnabled(false);
            }
            minXField.setEnabled(false);
            maxXField.setEnabled(false);
            minZField.setEnabled(false);
            maxZField.setEnabled(false);
            searchResultArea.setText("");
            searchProgressBar.setValue(0);
            searchProgressBar.setString(getString("progress.initial"));
            searchElapsedTimeLabel.setText(getString("elapsedTime.initial"));
            searchRemainingTimeLabel.setText(getString("remainingTime.calculating"));

            // 获取选择的版本
            String selectedVersion = (String) versionComboBox.getSelectedItem();
            int mc = MonumentFinderBridge.mcFromVersionString(
                    selectedVersion != null ? selectedVersion : "26.2");

            searcher = new MonumentSearchRunner(mc);
            formatContextSeed = seed;
            formatContextMc = mc;
            boolean pauseOnPairFound = pauseOnPairFoundCheckBox.isSelected();
            searcher.startSearch(seed, threadCount, minX, maxX, minZ, maxZ, this::updateSearchProgress, this::addSearchResult, pauseOnPairFound,
                            getSelectedPairLevel(pairLevelComboBox),
                            this::formatGroupResult, nativeErrorMessage());

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, getString("error.invalidNumber"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
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
            searchPauseButton.setText(getString("button.pause"));
            searchThreadCountField.setEnabled(false); // 恢复后不能修改线程数
        } else {
            // 暂停
            searcher.pause();
            isSearchPaused = true;
            searchPauseButton.setText(getString("button.resume"));
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
        searchPauseButton.setText(getString("button.pause"));
        searchStopButton.setEnabled(false);
        searchResetButton.setEnabled(true);
        searchSeedField.setEnabled(true);
        searchThreadCountField.setEnabled(true);
        versionComboBox.setEnabled(true);
        pairLevelComboBox.setEnabled(true);
        pauseOnPairFoundCheckBox.setEnabled(true);
        if (biomeCheckCheckBox != null) {
            biomeCheckCheckBox.setEnabled(true);
        }
        minXField.setEnabled(true);
        maxXField.setEnabled(true);
        minZField.setEnabled(true);
        maxZField.setEnabled(true);
        if (languageComboBox != null) {
            languageComboBox.setEnabled(true);
        }
        searchRemainingTimeLabel.setText(getString("remainingTime.stopped"));
    }

    private void resetSearchToDefaults() {
        minXField.setText(String.valueOf(DEFAULT_MIN_X));
        maxXField.setText(String.valueOf(DEFAULT_MAX_X));
        minZField.setText(String.valueOf(DEFAULT_MIN_Z));
        maxZField.setText(String.valueOf(DEFAULT_MAX_Z));
    }

    private void updateSearchProgress(MonumentSearchRunner.ProgressInfo info) {
        SwingUtilities.invokeLater(() -> {
            if (!isSearchRunning) return;

            int progress = (int) Math.min(100, info.percentage);
            searchProgressBar.setValue(progress);
            // 将进度信息显示在进度条中
            searchProgressBar.setString(getString("progress.format", info.processed, info.total, info.percentage));
            
            // 暂停时不更新时间
            if (!isSearchPaused) {
                searchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(info.elapsedMs)));
                if (info.remainingMs > 0) {
                    searchRemainingTimeLabel.setText(getString("remainingTime", formatTime(info.remainingMs)));
                } else {
                    searchRemainingTimeLabel.setText(getString("remainingTime.calculating"));
                }
            } else {
                searchRemainingTimeLabel.setText(getString("remainingTime.paused"));
            }

            if (info.processed >= info.total) {
                isSearchRunning = false;
                isSearchPaused = false;
                searchStartButton.setEnabled(true);
                searchPauseButton.setEnabled(false);
                searchPauseButton.setText(getString("button.pause"));
                searchStopButton.setEnabled(false);
                searchResetButton.setEnabled(true);
                searchSeedField.setEnabled(true);
                searchThreadCountField.setEnabled(true);
                versionComboBox.setEnabled(true);
                pairLevelComboBox.setEnabled(true);
                pauseOnPairFoundCheckBox.setEnabled(true);
        if (biomeCheckCheckBox != null) {
            biomeCheckCheckBox.setEnabled(true);
        }
                if (languageComboBox != null) {
                    languageComboBox.setEnabled(true);
                }
                minXField.setEnabled(true);
                maxXField.setEnabled(true);
                minZField.setEnabled(true);
                maxZField.setEnabled(true);
                searchProgressBar.setString(getString("progress.complete", info.processed, info.total));
                searchRemainingTimeLabel.setText(getString("remainingTime.completed"));
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
            JOptionPane.showMessageDialog(this, getString("error.noResultsToExport"), getString("prompt.information"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(getString("dialog.exportResults"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(getString("dialog.textFiles"), "txt"));
        fileChooser.setSelectedFile(new File(getString("file.searchOutput")));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                writer.print(searchResultArea.getText());
                JOptionPane.showMessageDialog(this, getString("success.export"), getString("prompt.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, getString("error.exportFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return getString("time.format", days, hours, minutes, seconds);
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
        batchSeedFileTitleLabel = new JLabel(getString("label.seedFile"));
        batchSeedFileTitleLabel.setFont(getLoadedFont());
        inputPanel.add(batchSeedFileTitleLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchImportButton = new JButton(getString("button.selectFile"));
        batchImportButton.setFont(getLoadedFont());
        batchImportButton.addActionListener(e -> importSeedFile());
        inputPanel.add(batchImportButton, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 2;
        batchSeedFileLabel = new JLabel(getString("label.noFileSelected"));
        batchSeedFileLabel.setFont(getLoadedFont());
        inputPanel.add(batchSeedFileLabel, gbc);
        gbc.gridwidth = 1;

        // Thread Count 输入
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchThreadCountLabel = new JLabel(getString("label.threadCount"));
        batchThreadCountLabel.setFont(getLoadedFont());
        inputPanel.add(batchThreadCountLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchThreadCountField = new JTextField(String.valueOf(DEFAULT_THREAD_COUNT), 20);
        batchThreadCountField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchThreadCountField, getString("label.field.threadCount"));
            }
        });
        inputPanel.add(batchThreadCountField, gbc);

        // 版本选择下拉框
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchVersionLabel = new JLabel(getString("label.mcVersion"));
        batchVersionLabel.setFont(getLoadedFont());
        inputPanel.add(batchVersionLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchVersionComboBox = new JComboBox<>(MonumentFinderBridge.SUPPORTED_VERSION_LABELS);
        batchVersionComboBox.setSelectedIndex(0);
        inputPanel.add(batchVersionComboBox, gbc);

        // 连级选择（筛选目标：2/3/4 连）
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchPairLevelLabel = new JLabel(getString("label.pairLevel"));
        batchPairLevelLabel.setFont(getLoadedFont());
        inputPanel.add(batchPairLevelLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchPairLevelComboBox = new JComboBox<>();
        fillPairLevelComboItems(batchPairLevelComboBox);
        inputPanel.add(batchPairLevelComboBox, gbc);

        // 群系检查选项
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 2;
        batchBiomeCheckCheckBox = new JCheckBox(getString("check.biomeCheck"));
        batchBiomeCheckCheckBox.setFont(getLoadedFont());
        batchBiomeCheckCheckBox.setSelected(true);
        inputPanel.add(batchBiomeCheckCheckBox, gbc);
        gbc.gridwidth = 1;

        // MinX 输入
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchMinXLabel = new JLabel(getString("label.minX"));
        batchMinXLabel.setFont(getLoadedFont());
        inputPanel.add(batchMinXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchMinXField = new JTextField(String.valueOf(BATCH_DEFAULT_MIN_X), 20);
        batchMinXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchMinXField, getString("label.minX").replace(":", ""));
            }
        });
        inputPanel.add(batchMinXField, gbc);

        // MaxX 输入
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchMaxXLabel = new JLabel(getString("label.maxX"));
        batchMaxXLabel.setFont(getLoadedFont());
        inputPanel.add(batchMaxXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchMaxXField = new JTextField(String.valueOf(BATCH_DEFAULT_MAX_X), 20);
        batchMaxXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchMaxXField, getString("label.maxX").replace(":", ""));
            }
        });
        inputPanel.add(batchMaxXField, gbc);

        // MinZ 输入
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchMinZLabel = new JLabel(getString("label.minZ"));
        batchMinZLabel.setFont(getLoadedFont());
        inputPanel.add(batchMinZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchMinZField = new JTextField(String.valueOf(BATCH_DEFAULT_MIN_Z), 20);
        batchMinZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchMinZField, getString("label.minZ").replace(":", ""));
            }
        });
        inputPanel.add(batchMinZField, gbc);

        // MaxZ 输入
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchMaxZLabel = new JLabel(getString("label.maxZ"));
        batchMaxZLabel.setFont(getLoadedFont());
        inputPanel.add(batchMaxZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchMaxZField = new JTextField(String.valueOf(BATCH_DEFAULT_MAX_Z), 20);
        batchMaxZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchMaxZField, getString("label.maxZ").replace(":", ""));
            }
        });
        inputPanel.add(batchMaxZField, gbc);

        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout());
        batchStartButton = new JButton(getString("button.startSearch"));
        batchPauseButton = new JButton(getString("button.pause"));
        batchStopButton = new JButton(getString("button.stop"));
        batchResetButton = new JButton(getString("button.reset"));
        batchPauseButton.setEnabled(false);
        batchStopButton.setEnabled(false);
        buttonPanel.add(batchStartButton);
        buttonPanel.add(batchPauseButton);
        buttonPanel.add(batchStopButton);
        buttonPanel.add(batchResetButton);

        // 静态文字展示区域（放在按钮上方）
        JPanel creditPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        creditPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        batchCreditLabel = new JLabel(getString("credit.text"));
        batchCreditLabel.setFont(getLoadedFont());
        creditPanel.add(batchCreditLabel);

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
        batchProgressBar.setString(getString("progress.initial"));
        progressPanel.add(batchProgressBar, pgc);

        pgc.gridwidth = 1;
        pgc.gridy = 1;
        batchElapsedTimeLabel = new JLabel(getString("elapsedTime.initial"));
        progressPanel.add(batchElapsedTimeLabel, pgc);

        pgc.gridy = 3;
        batchRemainingTimeLabel = new JLabel(getString("remainingTime.calculating"));
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
        batchRightPanel = new JPanel(new BorderLayout());
        batchRightPanel.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        batchResultArea = new JTextArea();
        batchResultArea.setEditable(false);
        batchResultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(batchResultArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel exportSortPanel = new JPanel(new FlowLayout());
        batchExportFullButton = new JButton(getString("button.exportFull"));
        batchExportFullButton.addActionListener(e -> exportBatchResults(true));
        batchExportSeedsButton = new JButton(getString("button.exportSeeds"));
        batchExportSeedsButton.addActionListener(e -> exportBatchResults(false));
        batchSortButton = new JButton(getString("button.sort"));
        batchSortButton.addActionListener(e -> sortBatchResults());
        exportSortPanel.add(batchExportFullButton);
        exportSortPanel.add(batchExportSeedsButton);
        exportSortPanel.add(batchSortButton);
        batchRightPanel.add(scrollPane, BorderLayout.CENTER);
        batchRightPanel.add(exportSortPanel, BorderLayout.SOUTH);

        // 使用 JSplitPane 分割
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, batchRightPanel);
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
        fileChooser.setDialogTitle(getString("dialog.selectSeedFile"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(getString("dialog.textFiles"), "txt"));
        
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
                batchSeedFileLabel.setText(getString("label.seedFileSelected", batchSeedFileName, batchSeedList.size()));
                JOptionPane.showMessageDialog(this, getString("success.importSeeds", batchSeedList.size()), getString("prompt.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, getString("error.seedFileReadFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 开始批量搜索
    private void startBatchSearch() {
        if (batchSeedList.isEmpty()) {
            JOptionPane.showMessageDialog(this, getString("error.seedFileRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            // 验证线程数
            String threadText = batchThreadCountField.getText().trim();
            double threadDouble;
            try {
                threadDouble = Double.parseDouble(threadText);
                if (threadDouble != Math.floor(threadDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.threadCountMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.threadCountFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            int threadCount = (int) threadDouble;
            if (threadCount < 1) {
                JOptionPane.showMessageDialog(this, getString("error.threadCountRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查线程数是否超过CPU核数
            int cpuThreads = Runtime.getRuntime().availableProcessors();
            if (threadCount > cpuThreads) {
                int result = JOptionPane.showConfirmDialog(
                    this,
                    getString("error.threadCountExceedsCPU", cpuThreads, cpuThreads),
                    getString("prompt.adjustThreadCount"),
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
                    JOptionPane.showMessageDialog(this, getString("error.minXMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    batchMinXField.setText(String.valueOf(BATCH_DEFAULT_MIN_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.minXFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                batchMinXField.setText(String.valueOf(BATCH_DEFAULT_MIN_X));
                return;
            }
            
            try {
                maxXDouble = Double.parseDouble(maxXText);
                if (maxXDouble != Math.floor(maxXDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.maxXMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    batchMaxXField.setText(String.valueOf(BATCH_DEFAULT_MAX_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.maxXFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                batchMaxXField.setText(String.valueOf(BATCH_DEFAULT_MAX_X));
                return;
            }
            
            try {
                minZDouble = Double.parseDouble(minZText);
                if (minZDouble != Math.floor(minZDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.minZMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    batchMinZField.setText(String.valueOf(BATCH_DEFAULT_MIN_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.minZFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                batchMinZField.setText(String.valueOf(BATCH_DEFAULT_MIN_Z));
                return;
            }
            
            try {
                maxZDouble = Double.parseDouble(maxZText);
                if (maxZDouble != Math.floor(maxZDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.maxZMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    batchMaxZField.setText(String.valueOf(BATCH_DEFAULT_MAX_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.maxZFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                batchMaxZField.setText(String.valueOf(BATCH_DEFAULT_MAX_Z));
                return;
            }
            
            int minX = (int) minXDouble;
            int maxX = (int) maxXDouble;
            int minZ = (int) minZDouble;
            int maxZ = (int) maxZDouble;
            
            if (minX >= maxX) {
                JOptionPane.showMessageDialog(this, getString("error.minXGreaterThanMaxX"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (minZ >= maxZ) {
                JOptionPane.showMessageDialog(this, getString("error.minZGreaterThanMaxZ"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 获取选择的版本
            String selectedVersion = (String) batchVersionComboBox.getSelectedItem();
            int mc = MonumentFinderBridge.mcFromVersionString(
                    selectedVersion != null ? selectedVersion : "26.2");

            // 创建final副本用于lambda表达式
            final int finalThreadCount = threadCount;
            final int finalMinX = minX;
            final int finalMaxX = maxX;
            final int finalMinZ = minZ;
            final int finalMaxZ = maxZ;
            final int finalPairLevel = getSelectedPairLevel(batchPairLevelComboBox);

            // 开始批量搜索
            isBatchSearchRunning = true;
            isBatchSearchPaused = false;
            batchStartButton.setEnabled(false);
            batchPauseButton.setEnabled(true);
            batchPauseButton.setText(getString("button.pause"));
            batchStopButton.setEnabled(true);
            batchResetButton.setEnabled(false);
            batchImportButton.setEnabled(false);
            batchThreadCountField.setEnabled(false);
            batchVersionComboBox.setEnabled(false);
            batchPairLevelComboBox.setEnabled(false);
            batchMinXField.setEnabled(false);
            batchMaxXField.setEnabled(false);
            batchMinZField.setEnabled(false);
            batchMaxZField.setEnabled(false);
            if (batchBiomeCheckCheckBox != null) {
                batchBiomeCheckCheckBox.setEnabled(false);
            }
            batchResultArea.setText("");
            batchProgressBar.setValue(0);
            batchProgressBar.setString(getString("progress.total", 0, batchSeedList.size(), 0.0));
            batchElapsedTimeLabel.setText(getString("elapsedTime.initial"));
            batchRemainingTimeLabel.setText(getString("remainingTime.calculating"));
            
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
                    MonumentSearchRunner batchSearcher = new MonumentSearchRunner(mc);
                    synchronized (this) {
                        currentBatchSearcher = batchSearcher;
                    }
                    final long currentSeed = seed;
                    formatContextSeed = currentSeed;
                    formatContextMc = mc;
                    
                    // 在独立线程中阻塞执行搜索，主循环 join 等待（避免 isRunning 竞态导致立即跳过）
                    final MonumentSearchRunner searcherForSeed = batchSearcher;
                    Thread seedSearchThread = new Thread(
                            () -> searcherForSeed.runSearchBlocking(
                                    seed,
                                    finalThreadCount,
                                    finalMinX,
                                    finalMaxX,
                                    finalMinZ,
                                    finalMaxZ,
                                    null,
                                    null,
                                    false,
                                    finalPairLevel,
                                    this::formatGroupResult,
                                    nativeErrorMessage()),
                            "batch-monument-seed-" + seed);
                    seedSearchThread.setDaemon(true);
                    seedSearchThread.start();

                    while (seedSearchThread.isAlive()) {
                        if (!isBatchSearchRunning) {
                            batchSearcher.stop();
                        }
                        if (isBatchSearchPaused) {
                            batchSearcher.pause();
                        } else {
                            batchSearcher.resume();
                        }

                        while (isBatchSearchPaused && isBatchSearchRunning && seedSearchThread.isAlive()) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                batchSearcher.stop();
                                return;
                            }
                        }

                        if (!isBatchSearchRunning) {
                            batchSearcher.stop();
                            break;
                        }

                        try {
                            seedSearchThread.join(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            batchSearcher.stop();
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
                        // 格式: /tp x 50 z [type=...] 面积=... (dx=?, dz=?) 或 (连级)
                        String[] parts = resultStr.split("\\s+");
                        if (parts.length >= 4 && parts[0].equals("/tp")) {
                            try {
                                int centerX = Integer.parseInt(parts[1]);
                                int centerZ = Integer.parseInt(parts[3]);
                                // 提取dx和dz（二连）
                                int dx = 0, dz = 0;
                                int area = 0;

                                // 查找dx、dz和面积
                                for (int i = 0; i < parts.length; i++) {
                                    String token = parts[i];
                                    if (token.startsWith("dx=")) {
                                        String dxStr = token.substring(3);
                                        if (dxStr.endsWith(",")) {
                                            dxStr = dxStr.substring(0, dxStr.length() - 1);
                                        }
                                        dx = Integer.parseInt(dxStr);
                                    }
                                    if (token.startsWith("dz=")) {
                                        String dzStr = token.substring(3);
                                        if (dzStr.endsWith(")")) {
                                            dzStr = dzStr.substring(0, dzStr.length() - 1);
                                        }
                                        dz = Integer.parseInt(dzStr);
                                    }
                                    int eq = token.indexOf('=');
                                    if (eq > 0 && !token.startsWith("type=") && !token.startsWith("dx=") && !token.startsWith("dz=")) {
                                        try {
                                            area = Integer.parseInt(token.substring(eq + 1));
                                        } catch (NumberFormatException ignored) {
                                        }
                                    }
                                }

                                // 连级：带dx/dz的括号为二连，否则匹配尾部 (N)
                                int count;
                                if (resultStr.contains("dx=")) {
                                    count = 2;
                                } else if (resultStr.contains("(4)")) {
                                    count = 4;
                                } else if (resultStr.contains("(3)")) {
                                    count = 3;
                                } else {
                                    count = 2;
                                }

                                resultItems.add(new ResultItem(centerX, centerZ, dx, dz, count, area));
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
                            formatContextSeed = currentSeed;
                            formatContextMc = mc;
                            batchResultArea.append(String.valueOf(currentSeed) + "\n");
                            for (ResultItem item : finalResults) {
                                String resultLine = formatGroupResult(item.centerX, item.centerZ, item.count, item.area, item.dx, item.dz);
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
                        batchProgressBar.setString(getString("progress.total", currentProcessed, total, percentage));
                        batchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(elapsed)));
                        if (!isBatchSearchPaused) {
                            if (remaining > 0) {
                                batchRemainingTimeLabel.setText(getString("remainingTime", formatTime(remaining)));
                            } else {
                                batchRemainingTimeLabel.setText(getString("remainingTime.calculating"));
                            }
                        }
                    });
                }
                
                SwingUtilities.invokeLater(() -> {
                    isBatchSearchRunning = false;
                    isBatchSearchPaused = false;
                    batchStartButton.setEnabled(true);
                    batchPauseButton.setEnabled(false);
                    batchPauseButton.setText(getString("button.pause"));
                    batchStopButton.setEnabled(false);
                    batchResetButton.setEnabled(true);
                    batchImportButton.setEnabled(true);
                    batchThreadCountField.setEnabled(true);
                    batchVersionComboBox.setEnabled(true);
                    batchPairLevelComboBox.setEnabled(true);
                    batchMinXField.setEnabled(true);
                    batchMaxXField.setEnabled(true);
                    batchMinZField.setEnabled(true);
                    batchMaxZField.setEnabled(true);
                    if (batchBiomeCheckCheckBox != null) {
                        batchBiomeCheckCheckBox.setEnabled(true);
                    }
                    batchProgressBar.setString(getString("progress.complete", total, total));
                    batchRemainingTimeLabel.setText(getString("remainingTime.completed"));
                });
            }).start();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, getString("error.startSearchFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            isBatchSearchRunning = false;
            isBatchSearchPaused = false;
            batchStartButton.setEnabled(true);
            batchPauseButton.setEnabled(false);
            batchPauseButton.setText(getString("button.pause"));
            batchStopButton.setEnabled(false);
            batchResetButton.setEnabled(true);
            batchImportButton.setEnabled(true);
            batchThreadCountField.setEnabled(true);
            batchVersionComboBox.setEnabled(true);
            batchPairLevelComboBox.setEnabled(true);
            batchMinXField.setEnabled(true);
            batchMaxXField.setEnabled(true);
            batchMinZField.setEnabled(true);
            batchMaxZField.setEnabled(true);
            if (batchBiomeCheckCheckBox != null) {
                batchBiomeCheckCheckBox.setEnabled(true);
            }
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
            batchPauseButton.setText(getString("button.pause"));
            batchThreadCountField.setEnabled(false);
            synchronized (this) {
                if (currentBatchSearcher != null) {
                    currentBatchSearcher.resume();
                }
            }
        } else {
            // 暂停
            isBatchSearchPaused = true;
            batchPauseButton.setText(getString("button.resume"));
            batchThreadCountField.setEnabled(true);
            synchronized (this) {
                if (currentBatchSearcher != null) {
                    currentBatchSearcher.pause();
                }
            }
            batchRemainingTimeLabel.setText(getString("remainingTime.paused"));
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
        batchPauseButton.setText(getString("button.pause"));
        batchStopButton.setEnabled(false);
        batchResetButton.setEnabled(true);
        batchImportButton.setEnabled(true);
        batchThreadCountField.setEnabled(true);
        batchVersionComboBox.setEnabled(true);
        batchMinXField.setEnabled(true);
        batchMaxXField.setEnabled(true);
        batchMinZField.setEnabled(true);
        batchMaxZField.setEnabled(true);
        if (batchBiomeCheckCheckBox != null) {
            batchBiomeCheckCheckBox.setEnabled(true);
        }
        batchRemainingTimeLabel.setText(getString("remainingTime.stopped"));
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
            JOptionPane.showMessageDialog(this, getString("error.noResultsToExport"), getString("prompt.information"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(fullFormat ? getString("dialog.exportFullResults") : getString("dialog.exportSeedList"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(getString("dialog.textFiles"), "txt"));
        fileChooser.setSelectedFile(new File(fullFormat ? getString("file.batchOutput") : getString("file.batchSeeds")));

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
                JOptionPane.showMessageDialog(this, getString("success.export"), getString("prompt.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, getString("error.exportFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
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
        int count;
        int area;

        ResultItem(int centerX, int centerZ, int dx, int dz, int count, int area) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.dx = dx;
            this.dz = dz;
            this.count = count;
            this.area = area;
        }
    }

    private void changeLanguage() {
        if (isSearchRunning || isSearchPaused || isBatchSearchRunning || isBatchSearchPaused) {
            if (languageComboBox != null) {
                if (I18n.getCurrentLocale().getLanguage().equals("zh")) {
                    languageComboBox.setSelectedIndex(0);
                } else {
                    languageComboBox.setSelectedIndex(1);
                }
            }
            return;
        }

        java.util.Locale newLocale;
        if (languageComboBox != null && languageComboBox.getSelectedIndex() == 0) {
            newLocale = new java.util.Locale("zh", "CN");
        } else {
            newLocale = new java.util.Locale("en", "US");
        }

        if (newLocale.equals(I18n.getCurrentLocale())) {
            return;
        }

        I18n.setLocale(newLocale);
        updateUITexts();
    }

    private void updateUITexts() {
        SwingUtilities.invokeLater(() -> {
            setTitle(getString("window.title"));
            if (tabbedPane != null) {
                tabbedPane.setTitleAt(0, getString("tab.singleSeedSearch"));
                tabbedPane.setTitleAt(1, getString("tab.batchSeedSearch"));
            }
            updateSingleSeedSearchTexts();
            updateBatchSearchTexts();
        });
    }

    private void updateSingleSeedSearchTexts() {
        if (searchSeedLabel != null) searchSeedLabel.setText(getString("label.seed"));
        if (searchThreadCountLabel != null) searchThreadCountLabel.setText(getString("label.threadCount"));
        if (searchVersionLabel != null) searchVersionLabel.setText(getString("label.mcVersion"));
        if (searchPairLevelLabel != null) searchPairLevelLabel.setText(getString("label.pairLevel"));
        if (pairLevelComboBox != null) fillPairLevelComboItems(pairLevelComboBox);
        if (searchMinXLabel != null) searchMinXLabel.setText(getString("label.minX"));
        if (searchMaxXLabel != null) searchMaxXLabel.setText(getString("label.maxX"));
        if (searchMinZLabel != null) searchMinZLabel.setText(getString("label.minZ"));
        if (searchMaxZLabel != null) searchMaxZLabel.setText(getString("label.maxZ"));
        if (searchLanguageLabel != null) searchLanguageLabel.setText(getString("label.language"));
        if (searchCreditLabel != null) searchCreditLabel.setText(getString("credit.text"));
        if (searchRightPanel != null) {
            searchRightPanel.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        }
        if (pauseOnPairFoundCheckBox != null) {
            pauseOnPairFoundCheckBox.setText(getString("check.pauseOnPairFound"));
        }
        if (biomeCheckCheckBox != null) {
            biomeCheckCheckBox.setText(getString("check.biomeCheck"));
        }
        if (batchBiomeCheckCheckBox != null) {
            batchBiomeCheckCheckBox.setText(getString("check.biomeCheck"));
        }
        if (searchStartButton != null) searchStartButton.setText(getString("button.startSearch"));
        if (searchPauseButton != null && !isSearchPaused) {
            searchPauseButton.setText(getString("button.pause"));
        }
        if (searchStopButton != null) searchStopButton.setText(getString("button.stop"));
        if (searchResetButton != null) searchResetButton.setText(getString("button.reset"));
        if (searchExportButton != null) searchExportButton.setText(getString("button.export"));
        if (searchSortButton != null) searchSortButton.setText(getString("button.sort"));
        if (searchProgressBar != null && !isSearchRunning) {
            searchProgressBar.setString(getString("progress.initial"));
        }
        if (searchElapsedTimeLabel != null && !isSearchRunning) {
            searchElapsedTimeLabel.setText(getString("elapsedTime.initial"));
        }
        if (searchRemainingTimeLabel != null && !isSearchRunning && !isSearchPaused) {
            searchRemainingTimeLabel.setText(getString("remainingTime.calculating"));
        }
    }

    private void updateBatchSearchTexts() {
        if (batchSeedFileTitleLabel != null) batchSeedFileTitleLabel.setText(getString("label.seedFile"));
        if (batchImportButton != null) batchImportButton.setText(getString("button.selectFile"));
        if (batchSeedFileLabel != null && batchSeedList.isEmpty()) {
            batchSeedFileLabel.setText(getString("label.noFileSelected"));
        } else if (batchSeedFileLabel != null && !batchSeedFileName.isEmpty()) {
            batchSeedFileLabel.setText(getString("label.seedFileSelected", batchSeedFileName, batchSeedList.size()));
        }
        if (batchThreadCountLabel != null) batchThreadCountLabel.setText(getString("label.threadCount"));
        if (batchVersionLabel != null) batchVersionLabel.setText(getString("label.mcVersion"));
        if (batchPairLevelLabel != null) batchPairLevelLabel.setText(getString("label.pairLevel"));
        if (batchPairLevelComboBox != null) fillPairLevelComboItems(batchPairLevelComboBox);
        if (batchMinXLabel != null) batchMinXLabel.setText(getString("label.minX"));
        if (batchMaxXLabel != null) batchMaxXLabel.setText(getString("label.maxX"));
        if (batchMinZLabel != null) batchMinZLabel.setText(getString("label.minZ"));
        if (batchMaxZLabel != null) batchMaxZLabel.setText(getString("label.maxZ"));
        if (batchCreditLabel != null) batchCreditLabel.setText(getString("credit.text"));
        if (batchRightPanel != null) {
            batchRightPanel.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        }
        if (batchStartButton != null) batchStartButton.setText(getString("button.startSearch"));
        if (batchPauseButton != null && !isBatchSearchPaused) {
            batchPauseButton.setText(getString("button.pause"));
        }
        if (batchStopButton != null) batchStopButton.setText(getString("button.stop"));
        if (batchResetButton != null) batchResetButton.setText(getString("button.reset"));
        if (batchExportFullButton != null) batchExportFullButton.setText(getString("button.exportFull"));
        if (batchExportSeedsButton != null) batchExportSeedsButton.setText(getString("button.exportSeeds"));
        if (batchSortButton != null) batchSortButton.setText(getString("button.sort"));
        if (batchProgressBar != null && !isBatchSearchRunning) {
            batchProgressBar.setString(getString("progress.initial"));
        }
        if (batchElapsedTimeLabel != null && !isBatchSearchRunning) {
            batchElapsedTimeLabel.setText(getString("elapsedTime.initial"));
        }
        if (batchRemainingTimeLabel != null && !isBatchSearchRunning && !isBatchSearchPaused) {
            batchRemainingTimeLabel.setText(getString("remainingTime.calculating"));
        }
    }

    public static void main(String[] args) {
        // 注意：系统属性应该在 Launcher 中设置
        // 这里只保留必要的初始化逻辑
        
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

