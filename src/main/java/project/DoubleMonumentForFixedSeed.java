package project;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    // Batch tab manual search area default: 16x16 (square mode uses DEFAULT_SQUARE_SIDE)
    private static final int BATCH_DEFAULT_MIN_X = -16;
    private static final int BATCH_DEFAULT_MAX_X = 16;
    private static final int BATCH_DEFAULT_MIN_Z = -16;
    private static final int BATCH_DEFAULT_MAX_Z = 16;
    /** region 坐标 ×512 = 方块坐标 */
    private static final int REGION_TO_BLOCKS = 512;
    /** 正方形区域默认边长（单位：region），单种子和批量一致 */
    private static final int DEFAULT_SQUARE_SIDE = 256;
    private static final int BATCH_DEFAULT_SQUARE_SIDE = DEFAULT_SQUARE_SIDE;
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
    private JLabel minXBlocksHintLabel;
    private JLabel maxXBlocksHintLabel;
    private JLabel minZBlocksHintLabel;
    private JLabel maxZBlocksHintLabel;
    private JLabel squareSideBlocksHintLabel;
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
    private JLabel batchMinXBlocksHintLabel;
    private JLabel batchMaxXBlocksHintLabel;
    private JLabel batchMinZBlocksHintLabel;
    private JLabel batchMaxZBlocksHintLabel;
    private JLabel batchSquareSideBlocksHintLabel;
    private JPanel batchRightPanel;

    private JTextField searchSeedField;
    private JTextField searchThreadCountField;
    private JTextField searchMinGroupAreaField;
    private JComboBox<String> versionComboBox;
    private JCheckBox pauseOnPairFoundCheckBox;
    private JCheckBox biomeCheckCheckBox;
    // area filter (optional) + search mode (exclusive: single dummy / dual dummy / single gate)
    private JPanel searchAreaPanel;
    private JPanel searchModePanel;
    private JPanel batchAreaPanel;
    private JPanel batchModePanel;
    private JCheckBox searchMinAreaCheckBox;
    private JComboBox<String> searchAreaTypeComboBox;
    private JCheckBox searchSingleModeCheckBox;
    private JCheckBox searchDualModeCheckBox;
    private JCheckBox searchGateModeCheckBox;
    private JCheckBox batchBiomeCheckCheckBox;
    private JCheckBox batchMinAreaCheckBox;
    private JComboBox<String> batchAreaTypeComboBox;
    private JCheckBox batchSingleModeCheckBox;
    private JCheckBox batchDualModeCheckBox;
    private JCheckBox batchGateModeCheckBox;
    private JTextField minXField;
    private JTextField maxXField;
    private JTextField minZField;
    private JTextField maxZField;
    private JCheckBox squareSideCheckBox;
    private JTextField squareSideField;
    private boolean squareSideUpdating = false;
    private JButton searchStartButton;
    private JButton searchPauseButton;
    private JButton searchStopButton;
    private JButton searchResetButton;
    private JButton searchExportButton;
    private JButton searchCsvButton;
    private JButton searchSortButton;
    private JProgressBar searchProgressBar;
    private JProgressBar searchComboProgressBar;
    private JLabel searchElapsedTimeLabel;
    private JLabel searchRemainingTimeLabel;
    private JTable searchResultTable;
    private final ResultTableModel searchModel = new ResultTableModel(false);
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
    private int formatContextMc = MonumentFinderBridge.MC_26_3;
    // Rows shown in the result table are held by searchModel; non-result lines (errors) go here
    private final List<String> searchNotes = new ArrayList<>();
    // Which result columns are shown (and exported) per tab, remembered for the session
    private boolean[] columnsSingle = ResultTable.defaultSelection(false);
    private boolean[] columnsBatch = ResultTable.defaultSelection(true);
    private boolean csvHeaderSingle = true;
    private boolean csvHeaderBatch = true;
    private JButton searchColumnsButton;
    private JButton batchColumnsButton;
    
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
    private JCheckBox batchSquareSideCheckBox;
    private JTextField batchSquareSideField;
    private boolean batchSquareSideUpdating = false;
    private JButton batchStartButton;
    private JButton batchPauseButton;
    private JButton batchStopButton;
    private JButton batchResetButton;
    private JButton batchExportFullButton;
    private JButton batchExportSeedsButton;
    private JButton batchCsvButton;
    private JButton batchSortButton;
    private JProgressBar batchProgressBar;
    private JLabel batchElapsedTimeLabel;
    private JLabel batchRemainingTimeLabel;
    private JTable batchResultTable;
    private final ResultTableModel batchModel = new ResultTableModel(true);
    private volatile boolean isBatchSearchRunning = false;
    private volatile boolean isBatchSearchPaused = false;
    /** Monitor for the batch pause wait loop; notifyAll on pause state changes instead of sleep polling */
    private final Object batchPauseMonitor = new Object();
    private MonumentSearchRunner currentBatchSearcher = null;
    private List<Long> batchSeedList = new ArrayList<>();
    private String batchSeedFileName = "";
    // Rows shown in the batch table are held by batchModel; non-result lines (errors) go here
    private final List<String> batchNotes = new ArrayList<>();
    
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

    /** Enabled 3/4-group search modes as a bitmask (any combination may be active). */
    private int selectedSearchModes() {
        return isBatchSearchRunning
                ? modesOf(batchSingleModeCheckBox, batchDualModeCheckBox, batchGateModeCheckBox)
                : modesOf(searchSingleModeCheckBox, searchDualModeCheckBox, searchGateModeCheckBox);
    }

    /** Which computed area the threshold is compared against (0 single, 1 dual, 2 gate). */
    private int selectedFilterMode() {
        JComboBox<String> combo = isBatchSearchRunning ? batchAreaTypeComboBox : searchAreaTypeComboBox;
        if (combo == null) {
            return 0;
        }
        int idx = combo.getSelectedIndex();
        return idx < 0 ? 0 : Math.min(idx, 2);
    }

    private static int modesOf(JCheckBox single, JCheckBox dual, JCheckBox gate) {
        int modes = 0;
        if (single != null && single.isSelected()) {
            modes |= MonumentFinderBridge.SEARCH_SINGLE;
        }
        if (dual != null && dual.isSelected()) {
            modes |= MonumentFinderBridge.SEARCH_DUAL;
        }
        if (gate != null && gate.isSelected()) {
            modes |= MonumentFinderBridge.SEARCH_GATE;
        }
        return modes;
    }

    /** Area filter switch; off means "report every valid group". */
    private boolean isMinAreaEnabled() {
        return isBatchSearchRunning
                ? batchMinAreaCheckBox != null && batchMinAreaCheckBox.isSelected()
                : searchMinAreaCheckBox != null && searchMinAreaCheckBox.isSelected();
    }

    /**
     * The area-type dropdown only has a meaning with exactly one mode enabled: it then mirrors
     * that mode. With several modes enabled the dropdown is greyed out (the shared threshold is
     * compared against each enabled mode's own area).
     */
    private void syncAreaTypeWithMode(boolean batch, int modes) {
        JComboBox<String> combo = batch ? batchAreaTypeComboBox : searchAreaTypeComboBox;
        if (combo == null) {
            return;
        }
        boolean oneMode = Integer.bitCount(modes) == 1;
        if (oneMode) {
            int idx = modes == MonumentFinderBridge.SEARCH_SINGLE ? 0
                    : modes == MonumentFinderBridge.SEARCH_DUAL ? 1 : 2;
            if (combo.getSelectedIndex() != idx) {
                combo.setSelectedIndex(idx);
            }
        }
        boolean areaOn = batch
                ? batchMinAreaCheckBox != null && batchMinAreaCheckBox.isSelected()
                : searchMinAreaCheckBox != null && searchMinAreaCheckBox.isSelected();
        combo.setEnabled(oneMode && areaOn);
    }

    /** Field + dropdown follow the area-filter checkbox; a running search disables everything. */
    private void updateAreaControlsEnabled(boolean batch) {
        boolean on = batch
                ? batchMinAreaCheckBox != null && batchMinAreaCheckBox.isSelected()
                : searchMinAreaCheckBox != null && searchMinAreaCheckBox.isSelected();
        int modes = modesOf(
                batch ? batchSingleModeCheckBox : searchSingleModeCheckBox,
                batch ? batchDualModeCheckBox : searchDualModeCheckBox,
                batch ? batchGateModeCheckBox : searchGateModeCheckBox);
        boolean oneMode = Integer.bitCount(modes) == 1;
        if (batch) {
            if (batchMinGroupAreaField != null) batchMinGroupAreaField.setEnabled(on);
            if (batchAreaTypeComboBox != null) batchAreaTypeComboBox.setEnabled(on && oneMode);
        } else {
            if (searchMinGroupAreaField != null) searchMinGroupAreaField.setEnabled(on);
            if (searchAreaTypeComboBox != null) searchAreaTypeComboBox.setEnabled(on && oneMode);
        }
    }

    private void setModeControlsEnabled(boolean batch, boolean enabled) {
        if (batch) {
            if (batchMinAreaCheckBox != null) batchMinAreaCheckBox.setEnabled(enabled);
            if (batchSingleModeCheckBox != null) batchSingleModeCheckBox.setEnabled(enabled);
            if (batchDualModeCheckBox != null) batchDualModeCheckBox.setEnabled(enabled);
            if (batchGateModeCheckBox != null) batchGateModeCheckBox.setEnabled(enabled);
        } else {
            if (searchMinAreaCheckBox != null) searchMinAreaCheckBox.setEnabled(enabled);
            if (searchSingleModeCheckBox != null) searchSingleModeCheckBox.setEnabled(enabled);
            if (searchDualModeCheckBox != null) searchDualModeCheckBox.setEnabled(enabled);
            if (searchGateModeCheckBox != null) searchGateModeCheckBox.setEnabled(enabled);
        }
        if (!enabled) {
            if (batch) {
                if (batchMinGroupAreaField != null) batchMinGroupAreaField.setEnabled(false);
                if (batchAreaTypeComboBox != null) batchAreaTypeComboBox.setEnabled(false);
            } else {
                if (searchMinGroupAreaField != null) searchMinGroupAreaField.setEnabled(false);
                if (searchAreaTypeComboBox != null) searchAreaTypeComboBox.setEnabled(false);
            }
        } else {
            updateAreaControlsEnabled(batch);
        }
    }

    private String formatGroupResult(int x, int z, int x2, int z2, int sx, int sz, int count,
                                     int area, int singleArea, int areaMax, int areaMaxX,
                                     int areaMaxZ, int dx, int dz, boolean dual) {
        String oceanTypeKey = null;
        if (isBiomeCheckSelected()) {
            // dual: classify at the midpoint of the two dummy points
            int cx = dual ? (x + x2) / 2 : x;
            int cz = dual ? (z + z2) / 2 : z;
            oceanTypeKey = MonumentFinderBridge.oceanTypeLabel(formatContextSeed, formatContextMc, cx, cz);
        }
        return I18n.formatRow(formatContextSeed, x, z, x2, z2, sx, sz, count, area, singleArea,
                areaMax, areaMaxX, areaMaxZ, dual, oceanTypeKey);
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
            if (searchMinAreaCheckBox != null) {
                p.putBoolean("s.minAreaOn", searchMinAreaCheckBox.isSelected());
                p.putInt("s.mode", modesOf(searchSingleModeCheckBox, searchDualModeCheckBox,
                        searchGateModeCheckBox));
            }
            if (squareSideCheckBox != null) {
                p.putBoolean("s.squareOn", squareSideCheckBox.isSelected());
            }
            putText(p, "s.square", squareSideField);
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
            if (batchMinAreaCheckBox != null) {
                p.putBoolean("b.minAreaOn", batchMinAreaCheckBox.isSelected());
                p.putInt("b.mode", modesOf(batchSingleModeCheckBox, batchDualModeCheckBox,
                        batchGateModeCheckBox));
            }
            if (batchSquareSideCheckBox != null) {
                p.putBoolean("b.squareOn", batchSquareSideCheckBox.isSelected());
            }
            putText(p, "b.square", batchSquareSideField);
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
            getBool(p, "s.minAreaOn", searchMinAreaCheckBox);
            getMode(p, "s.mode", searchSingleModeCheckBox, searchDualModeCheckBox, searchGateModeCheckBox);
            getCombo(p, "s.filter", searchAreaTypeComboBox);
            getBool(p, "s.squareOn", squareSideCheckBox);
            getText(p, "s.square", squareSideField);
            getText(p, "b.threads", batchThreadCountField);
            getCombo(p, "b.version", batchVersionComboBox);
            getCombo(p, "b.pair", batchPairLevelComboBox);
            getText(p, "b.minArea", batchMinGroupAreaField);
            getText(p, "b.minX", batchMinXField);
            getText(p, "b.maxX", batchMaxXField);
            getText(p, "b.minZ", batchMinZField);
            getText(p, "b.maxZ", batchMaxZField);
            getBool(p, "b.biome", batchBiomeCheckCheckBox);
            getBool(p, "b.minAreaOn", batchMinAreaCheckBox);
            getMode(p, "b.mode", batchSingleModeCheckBox, batchDualModeCheckBox, batchGateModeCheckBox);
            getCombo(p, "b.filter", batchAreaTypeComboBox);
            getBool(p, "b.squareOn", batchSquareSideCheckBox);
            getText(p, "b.square", batchSquareSideField);
        } catch (Throwable ignored) {
        }
        // Restore the square-mode field lock state, then sync area-filter visibility
        applyBoundFieldEnableState(true);
        applyBatchBoundFieldEnableState(true);
        // Square mode wins over stored Min/Max so the fields match the visible side length
        applySquareSideToBounds();
        applyBatchSquareSideToBounds();
        // Sync threshold field / combo bar visibility after pair level is restored
        updateThresholdVisibility();
    }

    private String nativeErrorMessage() {
        return getString("error.nativeNotLoaded", "");
    }

    private static void getMode(java.util.prefs.Preferences p, String key, JCheckBox single,
                                JCheckBox dual, JCheckBox gate) {
        if (single == null || dual == null || gate == null) {
            return;
        }
        int mode = p.getInt(key, MonumentFinderBridge.SEARCH_SINGLE);
        single.setSelected(mode == MonumentFinderBridge.SEARCH_SINGLE);
        dual.setSelected(mode == MonumentFinderBridge.SEARCH_DUAL);
        gate.setSelected(mode == MonumentFinderBridge.SEARCH_GATE);
    }

    /** Area filter row and the search-mode switches are shown only for pair level 3/4. */
    private void updateThresholdVisibility() {
        boolean singleVisible = getSelectedPairLevel(pairLevelComboBox) >= 3;
        setVisible(singleVisible, searchMinAreaCheckBox, searchAreaPanel, searchModePanel);
        updateAreaControlsEnabled(false);
        boolean batchVisible = getSelectedPairLevel(batchPairLevelComboBox) >= 3;
        setVisible(batchVisible, batchMinAreaCheckBox, batchAreaPanel, batchModePanel);
        updateAreaControlsEnabled(true);
    }

    private static void setVisible(boolean visible, JComponent... components) {
        for (JComponent c : components) {
            if (c != null) {
                c.setVisible(visible);
            }
        }
    }

    /**
     * Area threshold for 3/4 groups: 0 when the filter is off (or for pair level 2), otherwise the
     * field value in [6728, 13455]; -1 (invalid) makes the caller block the search.
     */
    private int readMinGroupArea(JComboBox<String> levelCombo, JTextField field, boolean enabled) {
        if (getSelectedPairLevel(levelCombo) < 3 || !enabled) {
            return 0;
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
        versionComboBox.setSelectedIndex(0); // 只有一个版本：1.18~26.3+
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

        // Area filter (optional) + search mode, visible only for pair level 3/4
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchMinAreaCheckBox = new JCheckBox(getString("check.minArea"));
        searchMinAreaCheckBox.setFont(getLoadedFont());
        searchMinAreaCheckBox.setSelected(true);
        searchMinAreaCheckBox.addActionListener(e -> updateAreaControlsEnabled(false));
        inputPanel.add(searchMinAreaCheckBox, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchAreaPanel = new JPanel(new BorderLayout(4, 0));
        searchAreaTypeComboBox = new JComboBox<>(new String[]{
                getString("area.single"), getString("area.dual"), getString("area.gate")});
        searchAreaTypeComboBox.addActionListener(e -> {
            if (searchSingleModeCheckBox == null) {
                return;
            }
            // picking an area type also enables that mode (the others stay as they are)
            int idx = searchAreaTypeComboBox.getSelectedIndex();
            if (idx == 0) {
                searchSingleModeCheckBox.setSelected(true);
            } else if (idx == 1) {
                searchDualModeCheckBox.setSelected(true);
            } else if (idx == 2) {
                searchGateModeCheckBox.setSelected(true);
            }
        });
        searchAreaPanel.add(searchAreaTypeComboBox, BorderLayout.WEST);
        searchMinGroupAreaField = new JTextField(String.valueOf(DEFAULT_MIN_GROUP_AREA), 8);
        searchAreaPanel.add(searchMinGroupAreaField, BorderLayout.CENTER);
        inputPanel.add(searchAreaPanel, gbc);

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
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        minXBlocksHintLabel = createBlocksHintLabel(minXField);
        inputPanel.add(minXBlocksHintLabel, gbc);

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
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        maxXBlocksHintLabel = createBlocksHintLabel(maxXField);
        inputPanel.add(maxXBlocksHintLabel, gbc);

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
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        minZBlocksHintLabel = createBlocksHintLabel(minZField);
        inputPanel.add(minZBlocksHintLabel, gbc);

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
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        maxZBlocksHintLabel = createBlocksHintLabel(maxZField);
        inputPanel.add(maxZBlocksHintLabel, gbc);

        // 正方形区域边长（勾选后按中心 0,0 填入 min/max）
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        squareSideCheckBox = new JCheckBox(getString("label.squareSide"));
        squareSideCheckBox.setFont(getLoadedFont());
        squareSideCheckBox.setSelected(true);
        squareSideCheckBox.addActionListener(e -> onSquareSideModeToggled());
        inputPanel.add(squareSideCheckBox, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        squareSideField = new JTextField(String.valueOf(DEFAULT_SQUARE_SIDE), 20);
        squareSideField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applySquareSideToBounds();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applySquareSideToBounds();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applySquareSideToBounds();
            }
        });
        squareSideField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(squareSideField, getString("label.squareSide"));
            }
        });
        inputPanel.add(squareSideField, gbc);
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        squareSideBlocksHintLabel = createBlocksHintLabel(squareSideField);
        inputPanel.add(squareSideBlocksHintLabel, gbc);
        applyBoundFieldEnableState(true);
        applySquareSideToBounds();

        gbc.gridx = 0;
        gbc.gridy = 10;
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
        gbc.gridy = 11;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 2;
        pauseOnPairFoundCheckBox = new JCheckBox(getString("check.pauseOnPairFound"));
        pauseOnPairFoundCheckBox.setFont(getLoadedFont());
        pauseOnPairFoundCheckBox.setSelected(false);
        inputPanel.add(pauseOnPairFoundCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 12;
        gbc.gridwidth = 2;
        biomeCheckCheckBox = new JCheckBox(getString("check.biomeCheck"));
        biomeCheckCheckBox.setFont(getLoadedFont());
        biomeCheckCheckBox.setSelected(true);
        inputPanel.add(biomeCheckCheckBox, gbc);

        // Search mode for 3/4 groups: single dummy / dual dummy / single gate (exclusive)
        gbc.gridx = 0;
        gbc.gridy = 13;
        gbc.gridwidth = 2;
        searchSingleModeCheckBox = new JCheckBox(getString("check.modeSingle"));
        searchDualModeCheckBox = new JCheckBox(getString("check.modeDual"));
        searchGateModeCheckBox = new JCheckBox(getString("check.modeGate"));
        for (JCheckBox box : new JCheckBox[]{searchSingleModeCheckBox, searchDualModeCheckBox,
                searchGateModeCheckBox}) {
            box.setFont(getLoadedFont());
            box.addActionListener(e -> syncAreaTypeWithMode(false,
                    modesOf(searchSingleModeCheckBox, searchDualModeCheckBox, searchGateModeCheckBox)));
        }
        searchSingleModeCheckBox.setSelected(true);
        searchModePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchModePanel.add(searchSingleModeCheckBox);
        searchModePanel.add(searchDualModeCheckBox);
        searchModePanel.add(searchGateModeCheckBox);
        searchModePanel.setVisible(false);
        inputPanel.add(searchModePanel, gbc);
        gbc.gridwidth = 1;

        JPanel buttonPanel = new JPanel(new GridLayout(1, 0, 4, 2)); // all buttons stay visible when narrow
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
        leftBottomPanel.add(buttonPanel, BorderLayout.NORTH); // pinned: never clipped by the form above
        leftBottomPanel.add(progressPanel, BorderLayout.CENTER);

        JScrollPane leftScroll = new JScrollPane(leftPanel); // short window scrolls the form instead of hiding the buttons
        leftScroll.setBorder(null);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.add(leftScroll, BorderLayout.CENTER);
        leftContainer.add(leftBottomPanel, BorderLayout.SOUTH);

        // Right: results display
        searchRightPanel = new JPanel(new BorderLayout());
        searchRightPanel.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        searchResultTable = createResultTable(searchModel, false);
        JScrollPane scrollPane = new JScrollPane(searchResultTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel exportSortPanel = new JPanel(new GridLayout(1, 0, 4, 2)); // all buttons stay visible when narrow
        searchExportButton = new JButton(getString("button.export"));
        searchExportButton.addActionListener(e -> exportSearchResults());
        searchCsvButton = new JButton(getString("button.exportCsv"));
        searchCsvButton.addActionListener(e -> exportSearchResultsCsv());
        searchColumnsButton = new JButton(getString("button.columns"));
        searchColumnsButton.addActionListener(e -> showColumnPicker(searchResultTable, false));
        searchSortButton = new JButton(getString("button.sort"));
        searchSortButton.addActionListener(e -> sortSearchResults());
        exportSortPanel.add(searchExportButton);
        exportSortPanel.add(searchCsvButton);
        exportSortPanel.add(searchColumnsButton);
        exportSortPanel.add(searchSortButton);
        searchRightPanel.add(scrollPane, BorderLayout.CENTER);
        searchRightPanel.add(exportSortPanel, BorderLayout.SOUTH);
        // keep the split pane from squeezing the export row out of the panel
        searchRightPanel.setMinimumSize(new Dimension(140, 140));
        scrollPane.setMinimumSize(new Dimension(0, 0));

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
        setModeControlsEnabled(false, true);
                applyBoundFieldEnableState(true);
                if (languageComboBox != null) {
                    languageComboBox.setEnabled(true);
                }
                resetSearchResults();
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

    /** 将 region 输入换算为“ = 65,536方块”提示文案 */
    private String formatBlocksHint(String regionText) {
        try {
            long region = Long.parseLong(regionText.trim());
            long blocks = region * REGION_TO_BLOCKS;
            return getString("hint.blocks", String.format(java.util.Locale.US, "%,d", blocks));
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private void updateBlocksHint(JTextField field, JLabel hintLabel) {
        if (field == null || hintLabel == null) {
            return;
        }
        hintLabel.setText(formatBlocksHint(field.getText()));
    }

    /** 创建 region → 方块坐标提示标签，并跟随输入实时更新 */
    private JLabel createBlocksHintLabel(JTextField field) {
        JLabel hintLabel = new JLabel();
        hintLabel.setFont(getLoadedFont());
        updateBlocksHint(field, hintLabel);
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateBlocksHint(field, hintLabel);
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateBlocksHint(field, hintLabel);
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateBlocksHint(field, hintLabel);
            }
        });
        return hintLabel;
    }

    private void onSquareSideModeToggled() {
        applyBoundFieldEnableState(!isSearchRunning);
        if (squareSideCheckBox != null && squareSideCheckBox.isSelected()) {
            applySquareSideToBounds();
        }
    }

    /** 正方形模式下禁用 Min/Max 输入框，手动模式下禁用边长输入框 */
    private void applyBoundFieldEnableState(boolean allowEdit) {
        boolean squareMode = squareSideCheckBox != null && squareSideCheckBox.isSelected();
        if (squareSideCheckBox != null) {
            squareSideCheckBox.setEnabled(allowEdit);
        }
        if (squareSideField != null) {
            squareSideField.setEnabled(allowEdit && squareMode);
        }
        boolean boundsEditable = allowEdit && !squareMode;
        if (minXField != null) {
            minXField.setEnabled(boundsEditable);
        }
        if (maxXField != null) {
            maxXField.setEnabled(boundsEditable);
        }
        if (minZField != null) {
            minZField.setEnabled(boundsEditable);
        }
        if (maxZField != null) {
            maxZField.setEnabled(boundsEditable);
        }
    }

    /**
     * 按正方形边长（单位 region）以 (0,0) 为中心填充 Min/Max。
     * 本项目的 Max 是排他上界，因此 Max = Min + Side。
     */
    private void applySquareSideToBounds() {
        if (squareSideUpdating || squareSideCheckBox == null || !squareSideCheckBox.isSelected()) {
            return;
        }
        if (squareSideField == null || minXField == null) {
            return;
        }
        String text = squareSideField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            long side = Long.parseLong(text);
            if (side < 1) {
                return;
            }
            long min = -side / 2;
            long max = min + side;
            if (min < Integer.MIN_VALUE || max > Integer.MAX_VALUE) {
                return;
            }
            squareSideUpdating = true;
            minXField.setText(String.valueOf((int) min));
            maxXField.setText(String.valueOf((int) max));
            minZField.setText(String.valueOf((int) min));
            maxZField.setText(String.valueOf((int) max));
            squareSideUpdating = false;
            checkSearchParameterChange();
        } catch (NumberFormatException ignored) {
            // 等待输入完整整数
        }
    }

    private void onBatchSquareSideModeToggled() {
        applyBatchBoundFieldEnableState(!isBatchSearchRunning);
        if (batchSquareSideCheckBox != null && batchSquareSideCheckBox.isSelected()) {
            applyBatchSquareSideToBounds();
        }
    }

    private void applyBatchBoundFieldEnableState(boolean allowEdit) {
        boolean squareMode = batchSquareSideCheckBox != null && batchSquareSideCheckBox.isSelected();
        if (batchSquareSideCheckBox != null) {
            batchSquareSideCheckBox.setEnabled(allowEdit);
        }
        if (batchSquareSideField != null) {
            batchSquareSideField.setEnabled(allowEdit && squareMode);
        }
        boolean boundsEditable = allowEdit && !squareMode;
        if (batchMinXField != null) {
            batchMinXField.setEnabled(boundsEditable);
        }
        if (batchMaxXField != null) {
            batchMaxXField.setEnabled(boundsEditable);
        }
        if (batchMinZField != null) {
            batchMinZField.setEnabled(boundsEditable);
        }
        if (batchMaxZField != null) {
            batchMaxZField.setEnabled(boundsEditable);
        }
    }

    private void applyBatchSquareSideToBounds() {
        if (batchSquareSideUpdating || batchSquareSideCheckBox == null
                || !batchSquareSideCheckBox.isSelected()) {
            return;
        }
        if (batchSquareSideField == null || batchMinXField == null) {
            return;
        }
        String text = batchSquareSideField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            long side = Long.parseLong(text);
            if (side < 1) {
                return;
            }
            long min = -side / 2;
            long max = min + side;
            if (min < Integer.MIN_VALUE || max > Integer.MAX_VALUE) {
                return;
            }
            batchSquareSideUpdating = true;
            batchMinXField.setText(String.valueOf((int) min));
            batchMaxXField.setText(String.valueOf((int) max));
            batchMinZField.setText(String.valueOf((int) min));
            batchMaxZField.setText(String.valueOf((int) max));
            batchSquareSideUpdating = false;
        } catch (NumberFormatException ignored) {
            // 等待输入完整整数
        }
    }

    /**
     * 检查搜索区域的 region 面积是否在允许范围内。
     * 面积 = (MaxX - MinX) * (MaxZ - MinZ)，超过 1024×1024 时弹出提示并拒绝搜索。
     *
     * @return true 表示面积合法，可以继续搜索
     */
    private boolean validateSearchArea(int minX, int maxX, int minZ, int maxZ) {
        long width = (long) maxX - minX;
        long height = (long) maxZ - minZ;
        long limit = MonumentSearchRunner.MAX_SEARCH_REGION_AREA;
        // Check each dimension first so the multiplication below cannot overflow.
        boolean tooLarge = width > limit || height > limit || width * height > limit;
        if (tooLarge) {
            JOptionPane.showMessageDialog(
                    this,
                    getString("error.searchAreaTooLarge"),
                    getString("prompt.error"),
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
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

    /** Builds a read-only results table: real column header (stays visible while scrolling). */
    private JTable createResultTable(ResultTableModel model, boolean batch) {
        return ResultTable.createTable(model, batch, getLoadedFont());
    }

    // Sort results by the recorded AFK point distance to origin (0,0), nearest first
    private void sortSearchResults() {
        List<String[]> rows = searchModel.rows();
        if (rows.isEmpty()) {
            return;
        }
        rows.sort(Comparator.comparingLong(ResultTable::distanceSq));
        searchModel.refresh();
    }

    /** Clears the single-seed results (the header is the table's own column header). */
    private void resetSearchResults() {
        searchModel.clear();
        searchNotes.clear();
    }

    /** Fixed-width text of the current single-seed table, for the plain-text export. */
    private String searchResultsText() {
        int[] cols = ResultTable.visibleCellColumns(searchResultTable, false);
        StringBuilder sb = new StringBuilder(ResultTable.header(cols)).append('\n');
        for (String[] cells : searchModel.rows()) {
            sb.append(ResultTable.line(cells, cols)).append('\n');
        }
        for (String note : searchNotes) {
            sb.append(note).append('\n');
        }
        return sb.toString();
    }

    // Single-seed search actions
    private void startSearch() {
        // Validate min area threshold for pair level 3/4 (block search if invalid)
        int minGroupArea = readMinGroupArea(pairLevelComboBox, searchMinGroupAreaField, searchMinAreaCheckBox == null || searchMinAreaCheckBox.isSelected());
        if (getSelectedPairLevel(pairLevelComboBox) >= 3 && selectedSearchModes() == 0) {
            JOptionPane.showMessageDialog(this, getString("error.selectSearchMode"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
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
                            selectedVersion != null ? selectedVersion : MonumentFinderBridge.VERSION_LABEL);

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
                            minGroupArea, selectedSearchModes(), selectedFilterMode(),
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
            
            // 搜索区域保护：region 面积过大时拒绝搜索
            if (!validateSearchArea(minX, maxX, minZ, maxZ)) {
                return;
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
            setModeControlsEnabled(false, false);
            if (languageComboBox != null) {
                languageComboBox.setEnabled(false);
            }
            applyBoundFieldEnableState(false);
            resetSearchResults();
            searchProgressBar.setValue(0);
            searchProgressBar.setString(getString("progress.initial"));
            searchElapsedTimeLabel.setText(getString("elapsedTime.initial"));
            searchRemainingTimeLabel.setText(getString("remainingTime.calculating"));

            String selectedVersion = (String) versionComboBox.getSelectedItem();
            int mc = MonumentFinderBridge.mcFromVersionString(
                    selectedVersion != null ? selectedVersion : MonumentFinderBridge.VERSION_LABEL);

            searcher = new MonumentSearchRunner(mc);
            formatContextSeed = seed;
            formatContextMc = mc;
            boolean pauseOnPairFound = pauseOnPairFoundCheckBox.isSelected();
            searcher.startSearch(seed, threadCount, minX, maxX, minZ, maxZ, this::updateSearchProgress, this::addSearchResult, pauseOnPairFound,
                            isBiomeCheckSelected(), getSelectedPairLevel(pairLevelComboBox),
                    minGroupArea, selectedSearchModes(), selectedFilterMode(),
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
        setModeControlsEnabled(false, true);
        applyBoundFieldEnableState(true);
        if (languageComboBox != null) {
            languageComboBox.setEnabled(true);
        }
        searchRemainingTimeLabel.setText(getString("remainingTime.stopped"));
    }

    private void resetSearchToDefaults() {
        if (squareSideCheckBox != null) {
            squareSideCheckBox.setSelected(true);
        }
        if (squareSideField != null) {
            squareSideUpdating = true;
            squareSideField.setText(String.valueOf(DEFAULT_SQUARE_SIDE));
            squareSideUpdating = false;
        }
        minXField.setText(String.valueOf(DEFAULT_MIN_X));
        maxXField.setText(String.valueOf(DEFAULT_MAX_X));
        minZField.setText(String.valueOf(DEFAULT_MIN_Z));
        maxZField.setText(String.valueOf(DEFAULT_MAX_Z));
        applyBoundFieldEnableState(!isSearchRunning);
        applySquareSideToBounds();
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
        setModeControlsEnabled(false, true);
                if (languageComboBox != null) {
                    languageComboBox.setEnabled(true);
                }
                applyBoundFieldEnableState(true);
                searchProgressBar.setString(getString("progress.complete", info.processed(), info.total()));
                searchRemainingTimeLabel.setText(getString("remainingTime.completed"));
            }
        });
    }

    private void addSearchResult(String result) {
        SwingUtilities.invokeLater(() -> {
            if (ResultTable.isRecord(result)) {
                searchModel.addRow(ResultTable.parse(result));
                // the native search pauses itself on the first hit; mirror that in the UI so the
                // button reads "继续" and the elapsed timer stops
                if (pauseOnPairFoundCheckBox != null && pauseOnPairFoundCheckBox.isSelected()
                        && isSearchRunning && !isSearchPaused) {
                    isSearchPaused = true;
                    if (searchPauseButton != null) {
                        searchPauseButton.setText(getString("button.resume"));
                    }
                    if (searcher != null) {
                        searcher.pause();
                    }
                }
            } else {
                // not a table row: a message (e.g. native library failure)
                boolean first = searchNotes.isEmpty();
                searchNotes.add(result);
                searchProgressBar.setString(result);
                if (first) {
                    JOptionPane.showMessageDialog(this, result, getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void exportSearchResults() {
        if (searchModel.getRowCount() == 0 && searchNotes.isEmpty()) {
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
                writer.print(searchResultsText());
                JOptionPane.showMessageDialog(this, getString("success.export"), getString("prompt.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, getString("error.exportFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportSearchResultsCsv() {
        if (searchModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, getString("error.noResultsToExport"), getString("prompt.information"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(getString("dialog.exportCsv"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV", "csv"));
        fileChooser.setSelectedFile(new File(getString("file.searchOutputCsv")));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            writeCsv(searchResultTable, csvHeaderSingle, ensureCsvExtension(fileChooser.getSelectedFile()));
        }
    }

    /**
     * Column picker: the checked columns become the table's columns (and therefore what the text
     * and CSV exports contain). The header-row option only affects the CSV export.
     */
    private void showColumnPicker(JTable table, boolean batch) {
        int[] all = ResultTable.allColumns();
        boolean[] current = batch ? columnsBatch : columnsSingle;
        JPanel panel = new JPanel(new GridLayout(0, 1, 2, 1));
        JCheckBox[] boxes = new JCheckBox[all.length];
        for (int i = 0; i < all.length; i++) {
            boxes[i] = new JCheckBox(I18n.get("col." + ResultTable.KEYS[all[i]]), current[all[i]]);
            panel.add(boxes[i]);
        }
        panel.add(new JSeparator());
        JCheckBox headerBox = new JCheckBox(getString("csv.includeHeader"),
                batch ? csvHeaderBatch : csvHeaderSingle);
        panel.add(headerBox);

        int answer = JOptionPane.showConfirmDialog(this, panel, getString("dialog.columns"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }

        boolean[] chosen = new boolean[all.length];
        int count = 0;
        for (int i = 0; i < all.length; i++) {
            chosen[all[i]] = boxes[i].isSelected();
            if (boxes[i].isSelected()) {
                count++;
            }
        }
        if (count == 0) {
            JOptionPane.showMessageDialog(this, getString("error.selectAtLeastOneColumn"),
                    getString("prompt.information"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (batch) {
            columnsBatch = chosen;
            csvHeaderBatch = headerBox.isSelected();
        } else {
            columnsSingle = chosen;
            csvHeaderSingle = headerBox.isSelected();
        }
        ResultTable.setVisibleColumns(table, batch, chosen);
    }

    /** Writes the columns currently shown in {@code table}, in display order. */
    private void writeCsv(JTable table, boolean includeHeader, File file) {
        StringBuilder sb = new StringBuilder();
        if (includeHeader) {
            sb.append(ResultTable.csvHeader(table)).append('\n');
        }
        for (int row = 0; row < table.getRowCount(); row++) {
            sb.append(ResultTable.csvRow(table, row)).append('\n');
        }
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.print(sb);
            JOptionPane.showMessageDialog(this, getString("success.export"), getString("prompt.success"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, getString("error.exportFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Appends ".csv" when the user typed a name without an extension. */
    private static File ensureCsvExtension(File file) {
        String name = file.getName();
        if (name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            return file;
        }
        return new File(file.getParentFile(), name + ".csv");
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

        // Area filter (optional) + search mode, visible only for pair level 3/4
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchMinAreaCheckBox = new JCheckBox(getString("check.minArea"));
        batchMinAreaCheckBox.setFont(getLoadedFont());
        batchMinAreaCheckBox.setSelected(true);
        batchMinAreaCheckBox.addActionListener(e -> updateAreaControlsEnabled(true));
        inputPanel.add(batchMinAreaCheckBox, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchAreaPanel = new JPanel(new BorderLayout(4, 0));
        batchAreaTypeComboBox = new JComboBox<>(new String[]{
                getString("area.single"), getString("area.dual"), getString("area.gate")});
        batchAreaTypeComboBox.addActionListener(e -> {
            if (batchSingleModeCheckBox == null) {
                return;
            }
            // picking an area type also enables that mode (the others stay as they are)
            int idx = batchAreaTypeComboBox.getSelectedIndex();
            if (idx == 0) {
                batchSingleModeCheckBox.setSelected(true);
            } else if (idx == 1) {
                batchDualModeCheckBox.setSelected(true);
            } else if (idx == 2) {
                batchGateModeCheckBox.setSelected(true);
            }
        });
        batchAreaPanel.add(batchAreaTypeComboBox, BorderLayout.WEST);
        batchMinGroupAreaField = new JTextField(String.valueOf(DEFAULT_MIN_GROUP_AREA), 8);
        batchAreaPanel.add(batchMinGroupAreaField, BorderLayout.CENTER);
        inputPanel.add(batchAreaPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 2;
        batchBiomeCheckCheckBox = new JCheckBox(getString("check.biomeCheck"));
        batchBiomeCheckCheckBox.setFont(getLoadedFont());
        batchBiomeCheckCheckBox.setSelected(true);
        inputPanel.add(batchBiomeCheckCheckBox, gbc);

        // Search mode for batch searches (visible only for pair level 3/4)
        gbc.gridx = 0;
        gbc.gridy = 12;
        batchSingleModeCheckBox = new JCheckBox(getString("check.modeSingle"));
        batchDualModeCheckBox = new JCheckBox(getString("check.modeDual"));
        batchGateModeCheckBox = new JCheckBox(getString("check.modeGate"));
        for (JCheckBox box : new JCheckBox[]{batchSingleModeCheckBox, batchDualModeCheckBox,
                batchGateModeCheckBox}) {
            box.setFont(getLoadedFont());
            box.addActionListener(e -> syncAreaTypeWithMode(true,
                    modesOf(batchSingleModeCheckBox, batchDualModeCheckBox, batchGateModeCheckBox)));
        }
        batchSingleModeCheckBox.setSelected(true);
        batchModePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        batchModePanel.add(batchSingleModeCheckBox);
        batchModePanel.add(batchDualModeCheckBox);
        batchModePanel.add(batchGateModeCheckBox);
        batchModePanel.setVisible(false);
        inputPanel.add(batchModePanel, gbc);
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
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchMinXBlocksHintLabel = createBlocksHintLabel(batchMinXField);
        inputPanel.add(batchMinXBlocksHintLabel, gbc);

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
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchMaxXBlocksHintLabel = createBlocksHintLabel(batchMaxXField);
        inputPanel.add(batchMaxXBlocksHintLabel, gbc);

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
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchMinZBlocksHintLabel = createBlocksHintLabel(batchMinZField);
        inputPanel.add(batchMinZBlocksHintLabel, gbc);

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
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchMaxZBlocksHintLabel = createBlocksHintLabel(batchMaxZField);
        inputPanel.add(batchMaxZBlocksHintLabel, gbc);

        // 正方形区域边长（勾选后按中心 0,0 填入 min/max）
        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        batchSquareSideCheckBox = new JCheckBox(getString("label.squareSide"));
        batchSquareSideCheckBox.setFont(getLoadedFont());
        batchSquareSideCheckBox.setSelected(true);
        batchSquareSideCheckBox.addActionListener(e -> onBatchSquareSideModeToggled());
        inputPanel.add(batchSquareSideCheckBox, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        batchSquareSideField = new JTextField(String.valueOf(BATCH_DEFAULT_SQUARE_SIDE), 20);
        batchSquareSideField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyBatchSquareSideToBounds();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyBatchSquareSideToBounds();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyBatchSquareSideToBounds();
            }
        });
        batchSquareSideField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(batchSquareSideField, getString("label.squareSide"));
            }
        });
        inputPanel.add(batchSquareSideField, gbc);
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        batchSquareSideBlocksHintLabel = createBlocksHintLabel(batchSquareSideField);
        inputPanel.add(batchSquareSideBlocksHintLabel, gbc);
        applyBatchBoundFieldEnableState(true);
        applyBatchSquareSideToBounds();

        JPanel buttonPanel = new JPanel(new GridLayout(1, 0, 4, 2)); // all buttons stay visible when narrow
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
        leftBottomPanel.add(buttonPanel, BorderLayout.NORTH); // pinned: never clipped by the form above
        leftBottomPanel.add(progressPanel, BorderLayout.CENTER);

        JScrollPane leftScroll = new JScrollPane(leftPanel); // short window scrolls the form instead of hiding the buttons
        leftScroll.setBorder(null);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.add(leftScroll, BorderLayout.CENTER);
        leftContainer.add(leftBottomPanel, BorderLayout.SOUTH);

        // Right: results display
        batchRightPanel = new JPanel(new BorderLayout());
        batchRightPanel.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        batchResultTable = createResultTable(batchModel, true);
        JScrollPane scrollPane = new JScrollPane(batchResultTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel exportSortPanel = new JPanel(new GridLayout(1, 0, 4, 2)); // all buttons stay visible when narrow
        batchExportFullButton = new JButton(getString("button.exportFull"));
        batchExportFullButton.addActionListener(e -> exportBatchResults(true));
        batchExportSeedsButton = new JButton(getString("button.exportSeeds"));
        batchExportSeedsButton.addActionListener(e -> exportBatchResults(false));
        batchCsvButton = new JButton(getString("button.exportCsv"));
        batchCsvButton.addActionListener(e -> exportBatchResultsCsv());
        batchColumnsButton = new JButton(getString("button.columns"));
        batchColumnsButton.addActionListener(e -> showColumnPicker(batchResultTable, true));
        batchSortButton = new JButton(getString("button.sort"));
        batchSortButton.addActionListener(e -> sortBatchResults());
        exportSortPanel.add(batchExportFullButton);
        exportSortPanel.add(batchExportSeedsButton);
        exportSortPanel.add(batchCsvButton);
        exportSortPanel.add(batchColumnsButton);
        exportSortPanel.add(batchSortButton);
        batchRightPanel.add(scrollPane, BorderLayout.CENTER);
        batchRightPanel.add(exportSortPanel, BorderLayout.SOUTH);
        // keep the split pane from squeezing the export row out of the panel
        batchRightPanel.setMinimumSize(new Dimension(140, 140));
        scrollPane.setMinimumSize(new Dimension(0, 0));

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
            // 搜索区域保护：region 面积过大时拒绝搜索
            if (!validateSearchArea(minX, maxX, minZ, maxZ)) {
                return;
            }

            // 获取选择的版本
            String selectedVersion = (String) batchVersionComboBox.getSelectedItem();
            int mc = MonumentFinderBridge.mcFromVersionString(
                    selectedVersion != null ? selectedVersion : MonumentFinderBridge.VERSION_LABEL);

            // Final copies for lambda capture
            final int finalThreadCount = threadCount;
            final int finalMinX = minX;
            final int finalMaxX = maxX;
            final int finalMinZ = minZ;
            final int finalMaxZ = maxZ;
            final int finalPairLevel = getSelectedPairLevel(batchPairLevelComboBox);

            // Validate min area threshold for pair level 3/4 (block search if invalid)
            int batchMinGroupArea = readMinGroupArea(batchPairLevelComboBox, batchMinGroupAreaField, batchMinAreaCheckBox == null || batchMinAreaCheckBox.isSelected());
            if (getSelectedPairLevel(batchPairLevelComboBox) >= 3 && selectedSearchModes() == 0) {
                JOptionPane.showMessageDialog(this, getString("error.selectSearchMode"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
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
            applyBatchBoundFieldEnableState(false);
            if (batchBiomeCheckCheckBox != null) {
                batchBiomeCheckCheckBox.setEnabled(false);
            }
            setModeControlsEnabled(true, false);
            resetBatchResults();
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
                                    selectedSearchModes(),
                                    selectedFilterMode(),
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
                    List<String[]> seedRows = new ArrayList<>();
                    List<String> seedNotes = new ArrayList<>();

                    for (String resultStr : allResults) {
                        // the formatter yields one ResultTable record per hit; anything else is a
                        // message (e.g. native library error) shown as-is
                        if (ResultTable.isRecord(resultStr)) {
                            seedRows.add(ResultTable.parse(resultStr));
                        } else {
                            seedNotes.add(resultStr);
                        }
                    }

                    if (!seedRows.isEmpty() || !seedNotes.isEmpty()) {
                        // nearest first (squared distance of the recorded AFK point)
                        seedRows.sort(Comparator.comparingLong(ResultTable::distanceSq));

                        SwingUtilities.invokeLater(() -> {
                            for (String[] cells : seedRows) {
                                batchModel.addRow(cells);
                            }
                            for (String note : seedNotes) {
                                boolean first = batchNotes.isEmpty();
                                batchNotes.add(note);
                                batchProgressBar.setString(note);
                                if (first) {
                                    JOptionPane.showMessageDialog(this, note,
                                            getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                                }
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
                    applyBatchBoundFieldEnableState(true);
                    if (batchBiomeCheckCheckBox != null) {
                        batchBiomeCheckCheckBox.setEnabled(true);
                    }
                    setModeControlsEnabled(true, true);
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
            applyBatchBoundFieldEnableState(true);
            if (batchBiomeCheckCheckBox != null) {
                batchBiomeCheckCheckBox.setEnabled(true);
            }
            setModeControlsEnabled(true, true);
        }
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
        applyBatchBoundFieldEnableState(true);
        if (batchBiomeCheckCheckBox != null) {
            batchBiomeCheckCheckBox.setEnabled(true);
        }
        batchRemainingTimeLabel.setText(getString("remainingTime.stopped"));
    }

    private void resetBatchSearchToDefaults() {
        if (batchSquareSideCheckBox != null) {
            batchSquareSideCheckBox.setSelected(true);
        }
        if (batchSquareSideField != null) {
            batchSquareSideUpdating = true;
            batchSquareSideField.setText(String.valueOf(BATCH_DEFAULT_SQUARE_SIDE));
            batchSquareSideUpdating = false;
        }
        batchMinXField.setText(String.valueOf(BATCH_DEFAULT_MIN_X));
        batchMaxXField.setText(String.valueOf(BATCH_DEFAULT_MAX_X));
        batchMinZField.setText(String.valueOf(BATCH_DEFAULT_MIN_Z));
        batchMaxZField.setText(String.valueOf(BATCH_DEFAULT_MAX_Z));
        applyBatchBoundFieldEnableState(!isBatchSearchRunning);
        applyBatchSquareSideToBounds();
    }

    private void exportBatchResults(boolean fullFormat) {
        if (batchModel.getRowCount() == 0 && batchNotes.isEmpty()) {
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
                    writer.print(batchResultsText());
                } else {
                    // Seeds-only export: one seed per seed that produced at least one hit
                    Set<String> seeds = new LinkedHashSet<>();
                    for (String[] cells : batchModel.rows()) {
                        seeds.add(ResultTable.cell(cells, ResultTable.SEED));
                    }
                    for (String seed : seeds) {
                        writer.println(seed);
                    }
                }
                JOptionPane.showMessageDialog(this, getString("success.export"), getString("prompt.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, getString("error.exportFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Exports the batch results as CSV using the columns currently shown. */
    private void exportBatchResultsCsv() {
        if (batchModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, getString("error.noResultsToExport"), getString("prompt.information"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(getString("dialog.exportCsv"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV", "csv"));
        fileChooser.setSelectedFile(new File(getString("file.batchOutputCsv")));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            writeCsv(batchResultTable, csvHeaderBatch, ensureCsvExtension(fileChooser.getSelectedFile()));
        }
    }

    /** Clears the batch results (the header is the table's own column header). */
    private void resetBatchResults() {
        batchModel.clear();
        batchNotes.clear();
    }

    /** Fixed-width text of the current batch table, for the plain-text export. */
    private String batchResultsText() {
        int[] cols = ResultTable.visibleCellColumns(batchResultTable, true);
        StringBuilder sb = new StringBuilder(ResultTable.header(cols)).append('\n');
        for (String[] cells : batchModel.rows()) {
            sb.append(ResultTable.line(cells, cols)).append('\n');
        }
        for (String note : batchNotes) {
            sb.append(note).append('\n');
        }
        return sb.toString();
    }

    /** Sorts each seed's rows and then the seeds, keeping only seeds that produced results. */
    private void sortBatchResults() {
        List<String[]> rows = batchModel.rows();
        if (rows.isEmpty()) {
            return;
        }
        Map<String, List<String[]>> bySeed = new LinkedHashMap<>();
        for (String[] cells : rows) {
            bySeed.computeIfAbsent(ResultTable.cell(cells, ResultTable.SEED), k -> new ArrayList<>()).add(cells);
        }
        for (List<String[]> group : bySeed.values()) {
            group.sort(Comparator.comparingLong(ResultTable::distanceSq));
        }
        List<Map.Entry<String, List<String[]>>> groups = new ArrayList<>(bySeed.entrySet());
        groups.sort(Comparator.comparingLong(e -> ResultTable.distanceSq(e.getValue().get(0))));

        rows.clear();
        for (Map.Entry<String, List<String[]>> group : groups) {
            rows.addAll(group.getValue());
        }
        batchModel.refresh();
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
            // header names are localized: rebuild the table headers and re-size the columns
            if (searchResultTable != null) {
                searchModel.structureChanged();
                ResultTable.setVisibleColumns(searchResultTable, false, columnsSingle);
            }
            if (batchResultTable != null) {
                batchModel.structureChanged();
                ResultTable.setVisibleColumns(batchResultTable, true, columnsBatch);
            }
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
        if (squareSideCheckBox != null) squareSideCheckBox.setText(getString("label.squareSide"));
        updateBlocksHint(minXField, minXBlocksHintLabel);
        updateBlocksHint(maxXField, maxXBlocksHintLabel);
        updateBlocksHint(minZField, minZBlocksHintLabel);
        updateBlocksHint(maxZField, maxZBlocksHintLabel);
        updateBlocksHint(squareSideField, squareSideBlocksHintLabel);
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
        if (searchMinAreaCheckBox != null) {
            searchMinAreaCheckBox.setText(getString("check.minArea"));
        }
        if (searchSingleModeCheckBox != null) {
            searchSingleModeCheckBox.setText(getString("check.modeSingle"));
            searchDualModeCheckBox.setText(getString("check.modeDual"));
            searchGateModeCheckBox.setText(getString("check.modeGate"));
        }
        if (batchBiomeCheckCheckBox != null) {
            batchBiomeCheckCheckBox.setText(getString("check.biomeCheck"));
        }
        if (batchMinAreaCheckBox != null) {
            batchMinAreaCheckBox.setText(getString("check.minArea"));
        }
        if (batchSingleModeCheckBox != null) {
            batchSingleModeCheckBox.setText(getString("check.modeSingle"));
            batchDualModeCheckBox.setText(getString("check.modeDual"));
            batchGateModeCheckBox.setText(getString("check.modeGate"));
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
        if (batchSquareSideCheckBox != null) batchSquareSideCheckBox.setText(getString("label.squareSide"));
        updateBlocksHint(batchMinXField, batchMinXBlocksHintLabel);
        updateBlocksHint(batchMaxXField, batchMaxXBlocksHintLabel);
        updateBlocksHint(batchMinZField, batchMinZBlocksHintLabel);
        updateBlocksHint(batchMaxZField, batchMaxZBlocksHintLabel);
        updateBlocksHint(batchSquareSideField, batchSquareSideBlocksHintLabel);
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

