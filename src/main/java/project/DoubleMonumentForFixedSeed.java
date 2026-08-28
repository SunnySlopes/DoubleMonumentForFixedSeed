package project;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Swing GUI for finding ocean monument pairs/groups (pair level 2-4) on a fixed seed.
 */
public class DoubleMonumentForFixedSeed extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(DoubleMonumentForFixedSeed.class.getName());
    // Default search area: 128x128
    private static final int DEFAULT_MIN_X = -128;
    private static final int DEFAULT_MAX_X = 128;
    private static final int DEFAULT_MIN_Z = -128;
    private static final int DEFAULT_MAX_Z = 128;
    // Batch tab default search area: 16x16
    private static final int BATCH_DEFAULT_MIN_X = -16;
    private static final int BATCH_DEFAULT_MAX_X = 16;
    private static final int BATCH_DEFAULT_MIN_Z = -16;
    private static final int BATCH_DEFAULT_MAX_Z = 16;

    private static final int WORLD_BOUND = 58594;
    private static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    /** Min usable area threshold for 3/4 pair groups (default = optimal 2-pair area) */
    private static final int DEFAULT_MIN_GROUP_AREA = 2 * 58 * 58;
    /** Manual threshold bounds: lower = optimal 2-pair area, upper = theoretical 4-pair max */
    private static final int MIN_GROUP_AREA_MIN = 2 * 58 * 58;
    private static final int MIN_GROUP_AREA_MAX = 4 * 58 * 58 - 1;

    private JTabbedPane tabbedPane;
    private JComboBox<String> languageComboBox;
    private JLabel searchSeedLabel;
    private JLabel searchThreadCountLabel;
    private JLabel searchVersionLabel;
    private JLabel searchPairLevelLabel;
    private JComboBox<String> pairLevelComboBox;
    private JLabel searchMinGroupAreaLabel;
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
    private JLabel batchMinGroupAreaLabel;
    private JLabel batchMinXLabel;
    private JLabel batchMaxXLabel;
    private JLabel batchMinZLabel;
    private JLabel batchMaxZLabel;
    private JLabel batchCreditLabel;
    private JPanel batchRightPanel;

    private JTextField searchSeedField;
    private JTextField searchThreadCountField;
    private JTextField searchMinGroupAreaField;
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
    private JProgressBar searchComboProgressBar;
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
    
    // Batch tab components
    private JButton batchImportButton;
    private JLabel batchSeedFileLabel;
    private JTextField batchThreadCountField;
    private JTextField batchMinGroupAreaField;
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
    /** Monitor for the batch pause wait loop; notifyAll on pause state changes instead of sleep polling */
    private final Object batchPauseMonitor = new Object();
    private MonumentSearchRunner currentBatchSearcher = null;
    private List<Long> batchSeedList = new ArrayList<>();
    private String batchSeedFileName = "";
    
    private Font loadedFont = null;

    public DoubleMonumentForFixedSeed() {
        I18n.initFromSystemDefault();
        setTitle(getString("window.title"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        setWindowIcon();

        setChineseFont();

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(getLoadedFont());

        // Tab 1: single-seed search
        JPanel searchPanel = createSearchPanel();
        tabbedPane.addTab(getString("tab.singleSeedSearch"), searchPanel);

        // Tab 2: batch seed search
        JPanel batchPanel = createBatchSearchPanel();
        tabbedPane.addTab(getString("tab.batchSeedSearch"), batchPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // Restore persisted UI options (language, seed, threads, version, pair level, area, checkboxes)
        loadUiPreferences();

        Runtime.getRuntime().addShutdownHook(new Thread(this::saveUiPreferences));

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

    // ==================== UI option persistence ====================

    private java.util.prefs.Preferences uiPrefs() {
        return java.util.prefs.Preferences.userNodeForPackage(DoubleMonumentForFixedSeed.class);
    }

    private static void putText(java.util.prefs.Preferences p, String key, JTextField f) {
        if (f != null) {
            p.put(key, f.getText().trim());
        }
    }

    private static void putCombo(java.util.prefs.Preferences p, String key, JComboBox<String> c) {
        if (c != null && c.getSelectedIndex() >= 0) {
            p.putInt(key, c.getSelectedIndex());
        }
    }

    private static void getText(java.util.prefs.Preferences p, String key, JTextField f) {
        if (f == null) {
            return;
        }
        String v = p.get(key, null);
        if (v != null && !v.isEmpty()) {
            f.setText(v);
        }
    }

    private static void getCombo(java.util.prefs.Preferences p, String key, JComboBox<String> c) {
        if (c == null) {
            return;
        }
        int i = p.getInt(key, -1);
        if (i >= 0 && i < c.getItemCount()) {
            c.setSelectedIndex(i);
        }
    }

    private static void getBool(java.util.prefs.Preferences p, String key, JCheckBox c) {
        if (c != null) {
            c.setSelected(p.getBoolean(key, c.isSelected()));
        }
    }

    private void saveUiPreferences() {
        try {
            java.util.prefs.Preferences p = uiPrefs();
            putCombo(p, "lang", languageComboBox);
            putText(p, "s.seed", searchSeedField);
            putText(p, "s.threads", searchThreadCountField);
            putCombo(p, "s.version", versionComboBox);
            putCombo(p, "s.pair", pairLevelComboBox);
            putText(p, "s.minArea", searchMinGroupAreaField);
            putText(p, "s.minX", minXField);
            putText(p, "s.maxX", maxXField);
            putText(p, "s.minZ", minZField);
            putText(p, "s.maxZ", maxZField);
            if (pauseOnPairFoundCheckBox != null) {
                p.putBoolean("s.pause", pauseOnPairFoundCheckBox.isSelected());
            }
            if (biomeCheckCheckBox != null) {
                p.putBoolean("s.biome", biomeCheckCheckBox.isSelected());
            }
            putText(p, "b.threads", batchThreadCountField);
            putCombo(p, "b.version", batchVersionComboBox);
            putCombo(p, "b.pair", batchPairLevelComboBox);
            putText(p, "b.minArea", batchMinGroupAreaField);
            putText(p, "b.minX", batchMinXField);
            putText(p, "b.maxX", batchMaxXField);
            putText(p, "b.minZ", batchMinZField);
            putText(p, "b.maxZ", batchMaxZField);
            if (batchBiomeCheckCheckBox != null) {
                p.putBoolean("b.biome", batchBiomeCheckCheckBox.isSelected());
            }
        } catch (Throwable ignored) {
        }
    }

    private void loadUiPreferences() {
        try {
            java.util.prefs.Preferences p = uiPrefs();
            // Language first: setSelectedIndex fires changeLanguage to refresh all texts
            getCombo(p, "lang", languageComboBox);
            getText(p, "s.seed", searchSeedField);
            getText(p, "s.threads", searchThreadCountField);
            getCombo(p, "s.version", versionComboBox);
            getCombo(p, "s.pair", pairLevelComboBox);
            getText(p, "s.minArea", searchMinGroupAreaField);
            getText(p, "s.minX", minXField);
            getText(p, "s.maxX", maxXField);
            getText(p, "s.minZ", minZField);
            getText(p, "s.maxZ", maxZField);
            getBool(p, "s.pause", pauseOnPairFoundCheckBox);
            getBool(p, "s.biome", biomeCheckCheckBox);
            getText(p, "b.threads", batchThreadCountField);
            getCombo(p, "b.version", batchVersionComboBox);
            getCombo(p, "b.pair", batchPairLevelComboBox);
            getText(p, "b.minArea", batchMinGroupAreaField);
            getText(p, "b.minX", batchMinXField);
            getText(p, "b.maxX", batchMaxXField);
            getText(p, "b.minZ", batchMinZField);
            getText(p, "b.maxZ", batchMaxZField);
            getBool(p, "b.biome", batchBiomeCheckCheckBox);
        } catch (Throwable ignored) {
        }
        // Sync threshold field / combo bar visibility after pair level is restored
        updateThresholdVisibility();
    }

    private String nativeErrorMessage() {
        return getString("error.nativeNotLoaded", "");
    }

    /** Threshold field is shown only for pair level 3/4 */
    private void updateThresholdVisibility() {
        boolean singleVisible = getSelectedPairLevel(pairLevelComboBox) >= 3;
        if (searchMinGroupAreaLabel != null) searchMinGroupAreaLabel.setVisible(singleVisible);
        if (searchMinGroupAreaField != null) searchMinGroupAreaField.setVisible(singleVisible);
        boolean batchVisible = getSelectedPairLevel(batchPairLevelComboBox) >= 3;
        if (batchMinGroupAreaLabel != null) batchMinGroupAreaLabel.setVisible(batchVisible);
        if (batchMinGroupAreaField != null) batchMinGroupAreaField.setVisible(batchVisible);
    }

    /**
     * Parses the min area threshold; only applies to pair level 3/4 (2-pair returns the default).
     * Valid range [6728, 13455]; invalid or out-of-range returns -1 so the caller can block the search.
     */
    private int readMinGroupArea(JComboBox<String> levelCombo, JTextField field) {
        if (getSelectedPairLevel(levelCombo) < 3) {
            return DEFAULT_MIN_GROUP_AREA;
        }
        try {
            int v = Integer.parseInt(field.getText().trim());
            if (v >= MIN_GROUP_AREA_MIN && v <= MIN_GROUP_AREA_MAX) {
                return v;
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    private JPanel createSearchPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left: input and progress
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

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
        versionComboBox.setSelectedIndex(0); // index 0 = MC 26.2
        inputPanel.add(versionComboBox, gbc);

        // Pair level selector: search target 2/3/4 groups
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
        pairLevelComboBox.addActionListener(e -> updateThresholdVisibility());

        // Min area threshold (visible only for pair level 3/4)
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchMinGroupAreaLabel = new JLabel(getString("label.minGroupArea"));
        searchMinGroupAreaLabel.setFont(getLoadedFont());
        inputPanel.add(searchMinGroupAreaLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchMinGroupAreaField = new JTextField(String.valueOf(DEFAULT_MIN_GROUP_AREA), 20);
        inputPanel.add(searchMinGroupAreaField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
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

        gbc.gridx = 0;
        gbc.gridy = 6;
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

        gbc.gridx = 0;
        gbc.gridy = 7;
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

        gbc.gridx = 0;
        gbc.gridy = 8;
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

        gbc.gridx = 0;
        gbc.gridy = 9;
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

        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 2;
        pauseOnPairFoundCheckBox = new JCheckBox(getString("check.pauseOnPairFound"));
        pauseOnPairFoundCheckBox.setFont(getLoadedFont());
        pauseOnPairFoundCheckBox.setSelected(false);
        inputPanel.add(pauseOnPairFoundCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.gridwidth = 2;
        biomeCheckCheckBox = new JCheckBox(getString("check.biomeCheck"));
        biomeCheckCheckBox.setFont(getLoadedFont());
        biomeCheckCheckBox.setSelected(true);
        inputPanel.add(biomeCheckCheckBox, gbc);
        gbc.gridwidth = 1;

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

        JPanel creditPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        creditPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        searchCreditLabel = new JLabel(getString("credit.text"));
        searchCreditLabel.setFont(getLoadedFont());
        creditPanel.add(searchCreditLabel);

        JPanel creditButtonPanel = new JPanel(new BorderLayout());
        creditButtonPanel.add(creditPanel, BorderLayout.NORTH);
        creditButtonPanel.add(buttonPanel, BorderLayout.SOUTH);

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
        
        JPanel leftBottomPanel = new JPanel(new BorderLayout());
        leftBottomPanel.add(progressPanel, BorderLayout.CENTER);
        
        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.add(leftPanel, BorderLayout.CENTER);
        leftContainer.add(leftBottomPanel, BorderLayout.SOUTH);

        // Right: results display
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

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, searchRightPanel);
        splitPane.setDividerLocation(450);
        splitPane.setResizeWeight(0.5);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        searchStartButton.addActionListener(e -> startSearch());
        searchPauseButton.addActionListener(e -> toggleSearchPause());
        searchStopButton.addActionListener(e -> stopSearch());
        searchResetButton.addActionListener(e -> resetSearchToDefaults());

        addSearchParameterListeners();

        return mainPanel;
    }


    // Parameter change listeners (thread count excluded)
    private void addSearchParameterListeners() {
        searchSeedField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSearchParameterChange(); }
        });
        
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
    
    // Check search params for changes (thread count excluded)
    private void checkSearchParameterChange() {
        if (isSearchRunning && !isSearchPaused) {
            return;
        }

        if (!isSearchPaused) {
            return;
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
            
            // Changed params while paused reset progress (thread count never triggers reset)
            if (seed != lastSearchSeed || minX != lastSearchMinX || 
                maxX != lastSearchMaxX || minZ != lastSearchMinZ || maxZ != lastSearchMaxZ) {
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
                searchMinGroupAreaField.setEnabled(true);
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
            // Ignore; validated again at search start
        }
    }

    
    private void validateIntegerInput(JTextField field, String fieldName) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return; // Empty: checked at search start instead
        }
        try {
            double value = Double.parseDouble(text);
            if (value != Math.floor(value)) {
                JOptionPane.showMessageDialog(this, getString("validation.integerRequired", fieldName), getString("prompt.inputError"), JOptionPane.ERROR_MESSAGE);
                field.requestFocus();
            }
        } catch (NumberFormatException e) {
            // Non-numeric: checked at search start instead
        }
    }

    // Cache file for the most recently used seed
    private static java.io.File seedCacheFile() {
        return new java.io.File(System.getProperty("user.home"), ".multimonumentfinder_seed");
    }

    private static String loadCachedSeed() {
        try {
            java.io.File f = seedCacheFile();
            if (!f.isFile()) {
                return "";
            }
            return java.nio.file.Files.readString(f.toPath()).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void saveSeedCache(long seed) {
        try {
            java.nio.file.Files.writeString(seedCacheFile().toPath(),
                    String.valueOf(seed));
        } catch (Exception ignored) {
        }
    }

    // Sort results by monument center distance to origin (0,0), nearest first
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
            // Result line format: /tp x 50 z [type=...] area=... [(pairLevel)]
            if (line.startsWith("/tp ")) {
                try {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) {
                        int x = Integer.parseInt(parts[1]);
                        int z = Integer.parseInt(parts[3]);
                        
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
        
        results.sort((a, b) -> {
            double d1 = Double.parseDouble(a[0]);
            double d2 = Double.parseDouble(b[0]);
            return Double.compare(d1, d2);
        });
        
        StringBuilder sb = new StringBuilder();
        for (String[] result : results) {
            sb.append(result[1]).append("\n");
        }
        for (String invalid : invalidLines) {
            sb.append(invalid).append("\n");
        }
        
        searchResultArea.setText(sb.toString());
    }

    // Single-seed search actions
    private void startSearch() {
        // Validate min area threshold for pair level 3/4 (block search if invalid)
        int minGroupArea = readMinGroupArea(pairLevelComboBox, searchMinGroupAreaField);
        if (minGroupArea < 0) {
            JOptionPane.showMessageDialog(this, getString("error.minGroupAreaInvalid"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        // While paused: resume instead of restarting
        if (isSearchRunning && isSearchPaused) {
            try {
                String threadText = searchThreadCountField.getText().trim();
                int threadCount = Integer.parseInt(threadText);
                if (threadCount < 1) {
                    JOptionPane.showMessageDialog(this, getString("error.threadCountRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
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
                
                // Thread count changed: apply live (no dialog, no progress reset)
                if (threadCount != lastSearchThreadCount) {
                    String selectedVersion = (String) versionComboBox.getSelectedItem();
                    int mc = MonumentFinderBridge.mcFromVersionString(
                            selectedVersion != null ? selectedVersion : "26.2");

                    if (searcher == null || searcher.getMc() != mc) {
                        searcher = new MonumentSearchRunner(mc);
                    }
                    
                    // searcher.startSearch detects the paused state and applies the new thread count
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
                            isBiomeCheckSelected(), getSelectedPairLevel(pairLevelComboBox),
                            minGroupArea,
                            this::formatGroupResult, nativeErrorMessage());
                    
                    lastSearchThreadCount = threadCount;
                } else {
                    searcher.resume();
                    isSearchPaused = false;
                }
                searchPauseButton.setText(getString("button.pause"));
                searchThreadCountField.setEnabled(false);
                return;
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.threadCountContinueError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        try {
            String seedText = searchSeedField.getText().trim();
            if (seedText.isEmpty()) {
                JOptionPane.showMessageDialog(this, getString("error.seedRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
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
            
            // MC seeds are 64-bit; reject anything beyond 2^63-1
            long seed;
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.seedOutOfRange"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            
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
            
            String minXText = minXField.getText().trim();
            String maxXText = maxXField.getText().trim();
            String minZText = minZField.getText().trim();
            String maxZText = maxZField.getText().trim();
            
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
            
            // World border check: warn if any |bound| exceeds 58594
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

            // Save baseline for the param-change check
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
            searchThreadCountField.setEnabled(false); // Editable while paused, so lock only while running
            versionComboBox.setEnabled(false);
            pairLevelComboBox.setEnabled(false);
            searchMinGroupAreaField.setEnabled(false);
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

            String selectedVersion = (String) versionComboBox.getSelectedItem();
            int mc = MonumentFinderBridge.mcFromVersionString(
                    selectedVersion != null ? selectedVersion : "26.2");

            searcher = new MonumentSearchRunner(mc);
            formatContextSeed = seed;
            formatContextMc = mc;
            boolean pauseOnPairFound = pauseOnPairFoundCheckBox.isSelected();
            searcher.startSearch(seed, threadCount, minX, maxX, minZ, maxZ, this::updateSearchProgress, this::addSearchResult, pauseOnPairFound,
                            isBiomeCheckSelected(), getSelectedPairLevel(pairLevelComboBox),
                    minGroupArea,
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
            // Resume; thread count changes are handled in startSearch
            searcher.resume();
            isSearchPaused = false;
            searchPauseButton.setText(getString("button.pause"));
            searchThreadCountField.setEnabled(false);
        } else {
            searcher.pause();
            isSearchPaused = true;
            searchPauseButton.setText(getString("button.resume"));
            searchThreadCountField.setEnabled(true); // Allow editing thread count while paused
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
        searchMinGroupAreaField.setEnabled(true);
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

            int progress = (int) Math.min(100, info.percentage());
            searchProgressBar.setValue(progress);
            searchProgressBar.setString(getString("progress.format", info.processed(), info.total(), info.percentage()));

            // Keep timers frozen while paused
            if (!isSearchPaused) {
                searchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(info.elapsedMs())));
                if (info.remainingMs() > 0) {
                    searchRemainingTimeLabel.setText(getString("remainingTime", formatTime(info.remainingMs())));
                } else {
                    searchRemainingTimeLabel.setText(getString("remainingTime.calculating"));
                }
            } else {
                searchRemainingTimeLabel.setText(getString("remainingTime.paused"));
            }

            if (info.processed() >= info.total()) {
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
                searchMinGroupAreaField.setEnabled(true);
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
                searchProgressBar.setString(getString("progress.complete", info.processed(), info.total()));
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
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
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

    /** Sets the window icon from /icon.png (or /icon.ico) in resources. */
    private void setWindowIcon() {
        try {
            java.net.URL iconURL = getClass().getResource("/icon.png");
            if (iconURL == null) {
                iconURL = getClass().getResource("/icon.ico");
            }
            if (iconURL != null) {
                ImageIcon icon = new ImageIcon(iconURL);
                setIconImage(icon.getImage());
            } else {
                // Fall back to the system default icon
                System.out.println("提示: 未找到图标文件 (icon.png 或 icon.ico)，使用系统默认图标");
            }
        } catch (Exception e) {
            System.err.println("设置图标时出错: " + e.getMessage());
        }
    }
    
    /** Loads font.ttf from resources and applies it to all UI components. */
    private void setChineseFont() {
        try {
            java.io.InputStream fontStream = getClass().getResourceAsStream("/font.ttf");
            if (fontStream == null) {
                System.err.println("错误: 未找到字体文件 font.ttf，请确保文件位于 src/main/resources/font.ttf");
                return;
            }
            
            Font font = Font.createFont(Font.TRUETYPE_FONT, fontStream);
            fontStream.close();
            
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(font);
            
            loadedFont = font.deriveFont(Font.PLAIN, 12f);
            
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
            LOGGER.log(Level.SEVERE, "加载字体时出错", e);
        }
    }
    
    /** Returns the loaded font, or the default sans-serif if none was loaded. */
    private Font getLoadedFont() {
        if (loadedFont != null) {
            return loadedFont;
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }
    

    private JPanel createBatchSearchPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left: input and progress
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

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

        // Pair level selector: search target 2/3/4 groups
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
        batchPairLevelComboBox.addActionListener(e -> updateThresholdVisibility());

        // Min area threshold (visible only for pair level 3/4)
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchMinGroupAreaLabel = new JLabel(getString("label.minGroupArea"));
        batchMinGroupAreaLabel.setFont(getLoadedFont());
        inputPanel.add(batchMinGroupAreaLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchMinGroupAreaField = new JTextField(String.valueOf(DEFAULT_MIN_GROUP_AREA), 20);
        inputPanel.add(batchMinGroupAreaField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 2;
        batchBiomeCheckCheckBox = new JCheckBox(getString("check.biomeCheck"));
        batchBiomeCheckCheckBox.setFont(getLoadedFont());
        batchBiomeCheckCheckBox.setSelected(true);
        inputPanel.add(batchBiomeCheckCheckBox, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 6;
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

        gbc.gridx = 0;
        gbc.gridy = 7;
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

        gbc.gridx = 0;
        gbc.gridy = 8;
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

        gbc.gridx = 0;
        gbc.gridy = 9;
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

        JPanel creditPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        creditPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        batchCreditLabel = new JLabel(getString("credit.text"));
        batchCreditLabel.setFont(getLoadedFont());
        creditPanel.add(batchCreditLabel);

        JPanel creditButtonPanel = new JPanel(new BorderLayout());
        creditButtonPanel.add(creditPanel, BorderLayout.NORTH);
        creditButtonPanel.add(buttonPanel, BorderLayout.SOUTH);

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
        
        JPanel leftBottomPanel = new JPanel(new BorderLayout());
        leftBottomPanel.add(progressPanel, BorderLayout.CENTER);
        
        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.add(leftPanel, BorderLayout.CENTER);
        leftContainer.add(leftBottomPanel, BorderLayout.SOUTH);

        // Right: results display
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

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, batchRightPanel);
        splitPane.setDividerLocation(450);
        splitPane.setResizeWeight(0.5);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        batchStartButton.addActionListener(e -> startBatchSearch());
        batchPauseButton.addActionListener(e -> toggleBatchSearchPause());
        batchStopButton.addActionListener(e -> stopBatchSearch());
        batchResetButton.addActionListener(e -> resetBatchSearchToDefaults());

        return mainPanel;
    }

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

    private void startBatchSearch() {
        if (batchSeedList.isEmpty()) {
            JOptionPane.showMessageDialog(this, getString("error.seedFileRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
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
            
            String minXText = batchMinXField.getText().trim();
            String maxXText = batchMaxXField.getText().trim();
            String minZText = batchMinZField.getText().trim();
            String maxZText = batchMaxZField.getText().trim();
            
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
            
            String selectedVersion = (String) batchVersionComboBox.getSelectedItem();
            int mc = MonumentFinderBridge.mcFromVersionString(
                    selectedVersion != null ? selectedVersion : "26.2");

            // Final copies for lambda capture
            final int finalThreadCount = threadCount;
            final int finalMinX = minX;
            final int finalMaxX = maxX;
            final int finalMinZ = minZ;
            final int finalMaxZ = maxZ;
            final int finalPairLevel = getSelectedPairLevel(batchPairLevelComboBox);

            // Validate min area threshold for pair level 3/4 (block search if invalid)
            int batchMinGroupArea = readMinGroupArea(batchPairLevelComboBox, batchMinGroupAreaField);
            if (batchMinGroupArea < 0) {
                JOptionPane.showMessageDialog(this, getString("error.minGroupAreaInvalid"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            final int finalMinGroupArea = batchMinGroupArea;

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
            batchMinGroupAreaField.setEnabled(false);
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
            
            new Thread(() -> {
                long startTime = System.currentTimeMillis();
                int processed = 0;
                int total = batchSeedList.size();
                
                for (long seed : batchSeedList) {
                    if (!isBatchSearchRunning) {
                        break;
                    }
                    
                    MonumentSearchRunner batchSearcher = new MonumentSearchRunner(mc);
                    synchronized (this) {
                        currentBatchSearcher = batchSearcher;
                    }
                    final long currentSeed = seed;
                    formatContextSeed = currentSeed;
                    formatContextMc = mc;
                    
                    // Run each seed in its own thread, joined by this loop (avoids the isRunning race skipping seeds)
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
                                    isBiomeCheckSelected(),
                                    finalPairLevel,
                                    finalMinGroupArea,
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

                        synchronized (batchPauseMonitor) {
                            while (isBatchSearchPaused && isBatchSearchRunning && seedSearchThread.isAlive()) {
                                try {
                                    batchPauseMonitor.wait(100);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    batchSearcher.stop();
                                    return;
                                }
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
                    
                    List<String> allResults = batchSearcher.getResults();
                    List<ResultItem> resultItems = new ArrayList<>();
                    
                    for (String resultStr : allResults) {
                        // Result format: /tp x 50 z [type=...] area=... or area=... (pairLevel)
                        String[] parts = resultStr.split("\\s+");
                        if (parts.length >= 4 && parts[0].equals("/tp")) {
                            try {
                                int centerX = Integer.parseInt(parts[1]);
                                int centerZ = Integer.parseInt(parts[3]);
                                int area = getArea(parts);

                                // Pair level: 2-pair has no suffix; 3/4 end with (3)/(4)
                                int count;
                                if (resultStr.contains("(4)")) {
                                    count = 4;
                                } else if (resultStr.contains("(3)")) {
                                    count = 3;
                                } else {
                                    count = 2;
                                }

                                resultItems.add(new ResultItem(centerX, centerZ, count, area));
                            } catch (NumberFormatException e) {
                                // Skip unparseable lines
                            }
                        }
                    }
                    
                    if (!resultItems.isEmpty()) {
                        // Sort by squared distance to origin (x²+z²), nearest first
                        resultItems.sort((a, b) -> {
                            int distA = a.centerX * a.centerX + a.centerZ * a.centerZ;
                            int distB = b.centerX * b.centerX + b.centerZ * b.centerZ;
                            return Integer.compare(distA, distB);
                        });
                        
                        final List<ResultItem> finalResults = resultItems;
                        SwingUtilities.invokeLater(() -> {
                            formatContextSeed = currentSeed;
                            formatContextMc = mc;
                            batchResultArea.append(currentSeed + "\n");
                            for (ResultItem item : finalResults) {
                                String resultLine = formatGroupResult(item.centerX, item.centerZ, item.count, item.area, 0, 0);
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
                    batchMinGroupAreaField.setEnabled(true);
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
            batchMinGroupAreaField.setEnabled(true);
            batchMinXField.setEnabled(true);
            batchMaxXField.setEnabled(true);
            batchMinZField.setEnabled(true);
            batchMaxZField.setEnabled(true);
            if (batchBiomeCheckCheckBox != null) {
                batchBiomeCheckCheckBox.setEnabled(true);
            }
        }
    }

    private static int getArea(String[] parts) {
        int area = 0;

        for (String token : parts) {
            int eq = token.indexOf('=');
            if (eq > 0 && !token.startsWith("type=")) {
                try {
                    area = Integer.parseInt(token.substring(eq + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return area;
    }

    private void toggleBatchSearchPause() {
        if (!isBatchSearchRunning) {
            return;
        }
        
        if (isBatchSearchPaused) {
            isBatchSearchPaused = false;
            synchronized (batchPauseMonitor) {
                batchPauseMonitor.notifyAll();
            }
            batchPauseButton.setText(getString("button.pause"));
            batchThreadCountField.setEnabled(false);
            synchronized (this) {
                if (currentBatchSearcher != null) {
                    currentBatchSearcher.resume();
                }
            }
        } else {
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

    private void stopBatchSearch() {
        isBatchSearchRunning = false;
        isBatchSearchPaused = false;
        synchronized (batchPauseMonitor) {
            batchPauseMonitor.notifyAll();
        }
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

    private void resetBatchSearchToDefaults() {
        batchMinXField.setText(String.valueOf(BATCH_DEFAULT_MIN_X));
        batchMaxXField.setText(String.valueOf(BATCH_DEFAULT_MAX_X));
        batchMinZField.setText(String.valueOf(BATCH_DEFAULT_MIN_Z));
        batchMaxZField.setText(String.valueOf(BATCH_DEFAULT_MAX_Z));
    }

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
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                if (fullFormat) {
                    writer.print(text);
                } else {
                    // Seeds-only export: emit seed lines, skip /tp result lines
                    String[] lines = text.split("\n");
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }
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

    private void sortBatchResults() {
        String text = batchResultArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        
        // Parse text into per-seed groups
        String[] lines = text.split("\n");
        List<SeedResultGroup> groups = new ArrayList<>();
        SeedResultGroup currentGroup = null;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("/tp")) {
                if (currentGroup != null) {
                    currentGroup.results.add(line);
                }
            } else {
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
        
        // Sort each group's results and record its nearest distance
        for (SeedResultGroup group : groups) {
            if (!group.results.isEmpty()) {
                group.results.sort((a, b) -> {
                    int distA = extractDistanceSquared(a);
                    int distB = extractDistanceSquared(b);
                    return Integer.compare(distA, distB);
                });
                // First result after sorting is the nearest to origin
                group.minDistance = extractDistanceSquared(group.results.get(0));
            } else {
                group.minDistance = Integer.MAX_VALUE;
            }
        }
        
        groups.sort(Comparator.comparingInt(a -> a.minDistance));
        
        // Rebuild text, keeping only seeds with results
        StringBuilder sb = new StringBuilder();
        for (SeedResultGroup group : groups) {
            if (!group.results.isEmpty()) {
                sb.append(group.seed).append("\n");
                for (String result : group.results) {
                    sb.append(result).append("\n");
                }
            }
        }
        
        batchResultArea.setText(sb.toString());
    }
    
    private int extractDistanceSquared(String resultLine) {
        try {
            String[] parts = resultLine.split("\\s+");
            if (parts.length >= 4 && parts[0].equals("/tp")) {
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);
                return x * x + z * z;
            }
        } catch (NumberFormatException e) {
            // Unparseable lines sort last
        }
        return Integer.MAX_VALUE;
    }
    
    private static class SeedResultGroup {
        String seed;
        List<String> results;
        int minDistance; // Nearest distance to origin among this seed's results
    }
    
    private static class ResultItem {
        int centerX;
        int centerZ;
        int count;
        int area;

        ResultItem(int centerX, int centerZ, int count, int area) {
            this.centerX = centerX;
            this.centerZ = centerZ;
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
        if (searchMinGroupAreaLabel != null) searchMinGroupAreaLabel.setText(getString("label.minGroupArea"));
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
        if (batchMinGroupAreaLabel != null) batchMinGroupAreaLabel.setText(getString("label.minGroupArea"));
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
        // System properties are set by the Launcher; keep only essential init here

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "设置系统外观失败", e);
            }
            new DoubleMonumentForFixedSeed().setVisible(true);
        });
    }
}

