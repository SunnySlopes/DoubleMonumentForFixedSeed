package project;

import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * Table layout shared by the result panels and the CSV export.
 *
 * <p>The runner hands the UI one tab-separated <em>record</em> per hit, with the cells in
 * {@link #KEYS} order. The panels show those cells in a {@link JTable} (real column header, one
 * column per cell, no manual padding), and the CSV export writes any selected subset of the cells
 * with the same header names. {@link #header(int[])}/{@link #line(String[], int[])} render the
 * same data as fixed-width text for the plain-text export.</p>
 */
final class ResultTable {

    static final int SEED = 0;
    static final int SINGLE_POINT = 1;
    static final int SINGLE_AREA = 2;
    static final int OCEAN = 3;
    static final int LEVEL = 4;
    static final int D1 = 5;
    static final int D2 = 6;
    static final int DUAL_AREA = 7;
    static final int AREA_MAX = 8;
    static final int GATE = 9;
    static final int CELLS = 10;

    /** Cell keys, also the i18n suffix of the header names ({@code col.<key>}). */
    static final String[] KEYS = {
            "seed", "singlePoint", "singleArea", "ocean", "level", "d1", "d2", "dualArea",
            "areaMax", "gate"
    };

    /** Display widths in monospace cells (a CJK glyph counts as two), used by the text export. */
    private static final int[] WIDTHS = {21, 21, 16, 12, 10, 21, 21, 16, 16, 21};

    /** Widest cell each column can ever hold; drives the table column widths. */
    private static final String[] SAMPLES = {
            "-9223372036854775808", "-30000000 -30000000", "13456", "frozen",
            "4", "-30000000 -30000000", "-30000000 -30000000", "13456", "13456",
            "-3750000 -3750000"
    };

    /** Panel columns: the requested columns; the batch tab prefixes the seed. */
    static final int[] PANEL_SINGLE = {
            SINGLE_POINT, SINGLE_AREA, OCEAN, LEVEL, D1, D2, DUAL_AREA, AREA_MAX, GATE
    };
    static final int[] PANEL_BATCH = {
            SEED, SINGLE_POINT, SINGLE_AREA, OCEAN, LEVEL, D1, D2, DUAL_AREA, AREA_MAX, GATE
    };

    private ResultTable() {}

    static int[] panelColumns(boolean batch) {
        return batch ? PANEL_BATCH : PANEL_SINGLE;
    }

    /** All cells in key order (the CSV selector offers these). */
    static int[] allColumns() {
        int[] all = new int[CELLS];
        for (int i = 0; i < CELLS; i++) {
            all[i] = i;
        }
        return all;
    }

    /** Default CSV selection: the panel columns; the seed only in batch mode. */
    static boolean[] defaultSelection(boolean batch) {
        boolean[] sel = new boolean[CELLS];
        for (int c : PANEL_SINGLE) {
            sel[c] = true;
        }
        sel[SEED] = batch;
        return sel;
    }

    static String header(int[] cols) {
        StringBuilder sb = new StringBuilder();
        for (int c : cols) {
            sb.append(pad(I18n.get("col." + KEYS[c]), WIDTHS[c]));
        }
        return sb.toString();
    }

    /**
     * Builds the read-only results table: real column header (kept visible by the enclosing
     * {@link javax.swing.JScrollPane} while the rows scroll), one column per cell, widths from
     * {@link #applyColumnWidths}.
     */
    static JTable createTable(ResultTableModel model, boolean batch, Font font) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // keep preferred widths, scroll instead
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setShowGrid(true);
        table.setFont(font);
        table.getTableHeader().setFont(font);
        // header drag reorders the columns (copy and CSV export follow the visible order)
        table.getTableHeader().setReorderingAllowed(true);
        table.setRowHeight(Math.max(20, table.getFontMetrics(font).getHeight() + 4));
        // left-align header and cells (the look-and-feel default centres headers and some L&Fs
        // right-align numbers, which would not line up with the values below them)
        forceLeftAlignment(table);
        installCopyAction(table);
        applyColumnWidths(table, batch);
        return table;
    }

    /**
     * Hides/shows columns without touching the model: the current display order is kept, columns
     * that become visible for the first time are appended in their natural order. Widths are
     * re-applied by model column, so a dragged order survives.
     */
    static void setVisibleColumns(JTable table, boolean batch, boolean[] visibleByCell) {
        int[] cellOfModel = panelColumns(batch);
        if (table.getModel().getColumnCount() != cellOfModel.length) {
            return;
        }
        TableColumnModel cm = table.getColumnModel();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            order.add(cm.getColumn(i).getModelIndex());
        }
        for (int m = 0; m < cellOfModel.length; m++) {
            if (!order.contains(m)) {
                order.add(m);
            }
        }
        while (cm.getColumnCount() > 0) {
            cm.removeColumn(cm.getColumn(0));
        }
        for (int modelIndex : order) {
            if (!visibleByCell[cellOfModel[modelIndex]]) {
                continue;
            }
            TableColumn column = new TableColumn(modelIndex);
            column.setHeaderValue(table.getModel().getColumnName(modelIndex));
            cm.addColumn(column);
        }
        applyColumnWidths(table, batch);
    }

    /** Cell indexes currently shown, in display order. */
    static int[] visibleCellColumns(JTable table, boolean batch) {
        int[] cellOfModel = panelColumns(batch);
        int[] cells = new int[table.getColumnCount()];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = cellOfModel[table.getColumnModel().getColumn(i).getModelIndex()];
        }
        return cells;
    }

    /**
     * Ctrl+C copies the selected rows (or the selected cells) as tab-separated text; when more
     * than one cell is copied the column header row is prepended, so the paste is self-describing.
     * A right-click menu offers the same for the whole table.
     */
    private static void installCopyAction(JTable table) {
        KeyStroke copy = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK);
        table.getInputMap(JComponent.WHEN_FOCUSED).put(copy, "copy");
        table.getActionMap().put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyToClipboard(table, table.getSelectedRows(), table.getSelectedColumns());
            }
        });

        JPopupMenu menu = new JPopupMenu();
        JMenuItem copySelected = new JMenuItem(I18n.get("table.copy"));
        copySelected.addActionListener(e ->
                copyToClipboard(table, table.getSelectedRows(), table.getSelectedColumns()));
        JMenuItem copyAll = new JMenuItem(I18n.get("table.copyAll"));
        copyAll.addActionListener(e -> copyToClipboard(table, allRows(table), allColumns(table)));
        JMenuItem selectAll = new JMenuItem(I18n.get("table.selectAll"));
        selectAll.addActionListener(e -> table.selectAll());
        menu.add(copySelected);
        menu.add(copyAll);
        menu.addSeparator();
        menu.add(selectAll);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShow(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShow(e);
            }

            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && !table.isRowSelected(row)) {
                    table.setRowSelectionInterval(row, row);
                }
                menu.show(table, e.getX(), e.getY());
            }
        });
    }

    private static int[] allRows(JTable table) {
        int[] rows = new int[table.getRowCount()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }
        return rows;
    }

    private static int[] allColumns(JTable table) {
        int[] cols = new int[table.getColumnCount()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }
        return cols;
    }

    /** Copies the given cells as TSV (header prepended unless it is a single cell). */
    private static void copyToClipboard(JTable table, int[] rows, int[] cols) {
        if (rows.length == 0 || cols.length == 0) {
            return;
        }
        int[] useCols = cols.length == 0 ? allColumns(table) : cols;
        StringBuilder sb = new StringBuilder();
        if ((long) rows.length * useCols.length > 1) {
            for (int i = 0; i < useCols.length; i++) {
                if (i > 0) {
                    sb.append('\t');
                }
                sb.append(table.getColumnName(useCols[i]));
            }
            sb.append('\n');
        }
        for (int r : rows) {
            for (int i = 0; i < useCols.length; i++) {
                if (i > 0) {
                    sb.append('\t');
                }
                Object value = table.getValueAt(r, useCols[i]);
                sb.append(value == null ? "" : value);
            }
            sb.append('\n');
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(sb.toString()), null);
        } catch (RuntimeException ignored) {
            // clipboard unavailable (e.g. headless): nothing else to do
        }
    }

    /** Wraps the look-and-feel renderers so every header/cell is leading-aligned. */
    private static void forceLeftAlignment(JTable table) {
        TableCellRenderer cellRenderer = table.getDefaultRenderer(Object.class);
        table.setDefaultRenderer(Object.class, (tbl, value, selected, focused, row, col) -> {
            Component c = cellRenderer.getTableCellRendererComponent(tbl, value, selected, focused, row, col);
            if (c instanceof JLabel label) {
                label.setHorizontalAlignment(SwingConstants.LEFT);
            }
            return c;
        });
        TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer((tbl, value, selected, focused, row, col) -> {
            Component c = headerRenderer.getTableCellRendererComponent(tbl, value, selected, focused, row, col);
            if (c instanceof JLabel label) {
                label.setHorizontalAlignment(SwingConstants.LEFT);
            }
            return c;
        });
    }

    /**
     * Sizes the table columns from the header text and the widest possible cell (plus padding).
     * Alignment is then handled by the table itself; nothing here depends on the row contents, so
     * it only has to run once per language change.
     */
    static void applyColumnWidths(JTable table, boolean batch) {
        int[] cols = panelColumns(batch);
        FontMetrics headerFm = table.getTableHeader().getFontMetrics(table.getTableHeader().getFont());
        FontMetrics cellFm = table.getFontMetrics(table.getFont());
        if (table.getModel().getColumnCount() != cols.length) {
            return; // model not attached yet
        }
        TableColumnModel cm = table.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            TableColumn column = cm.getColumn(i);
            int cell = cols[column.getModelIndex()];
            int width = Math.max(headerFm.stringWidth(String.valueOf(column.getHeaderValue())),
                    cellFm.stringWidth(sampleFor(cell, cellFm)));
            width += 14; // cell insets + a little slack
            column.setPreferredWidth(width);
            column.setMinWidth(48);
        }
    }

    /** Widest cell for a column, taking the current locale's ocean labels into account. */
    private static String sampleFor(int col, FontMetrics fm) {
        if (col != OCEAN) {
            return SAMPLES[col];
        }
        String widest = SAMPLES[OCEAN];
        for (String key : new String[] {"cold", "frozen", "others"}) {
            String label = I18n.get("result.oceanType." + key);
            if (fm.stringWidth(label) > fm.stringWidth(widest)) {
                widest = label;
            }
        }
        return widest;
    }

    static String line(String[] cells, int[] cols) {
        StringBuilder sb = new StringBuilder();
        for (int c : cols) {
            sb.append(pad(cell(cells, c), WIDTHS[c]));
        }
        return sb.toString();
    }

    /** CSV header line for the columns currently shown (display order, localized names). */
    static String csvHeader(JTable table) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < table.getColumnCount(); c++) {
            if (c > 0) {
                sb.append(',');
            }
            sb.append(csvCell(table.getColumnName(c)));
        }
        return sb.toString();
    }

    /** CSV line for one table row, using the columns currently shown. */
    static String csvRow(JTable table, int row) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < table.getColumnCount(); c++) {
            if (c > 0) {
                sb.append(',');
            }
            Object value = table.getValueAt(row, c);
            sb.append(csvCell(value == null ? "" : String.valueOf(value)));
        }
        return sb.toString();
    }

    static String csvHeader(int[] cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvCell(I18n.get("col." + KEYS[cols[i]])));
        }
        return sb.toString();
    }

    static String csvLine(String[] cells, int[] cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvCell(cell(cells, cols[i])));
        }
        return sb.toString();
    }

    /** A runner result line is a record when it carries exactly one cell per column. */
    static boolean isRecord(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        return line.split("\t", -1).length == CELLS;
    }

    static String[] parse(String line) {
        String[] cells = line.split("\t", -1);
        if (cells.length != CELLS) {
            throw new IllegalArgumentException("not a result record: " + line);
        }
        return cells;
    }

    /** Squared distance to the origin of the recorded AFK point (dummy 1, else the single point). */
    static long distanceSq(String[] cells) {
        String point = cell(cells, D1);
        if ("-".equals(point)) {
            point = cell(cells, SINGLE_POINT);
        }
        String[] xz = point.trim().split("\\s+");
        if (xz.length != 2) {
            return Long.MAX_VALUE;
        }
        try {
            long x = Long.parseLong(xz[0]);
            long z = Long.parseLong(xz[1]);
            return x * x + z * z;
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    static String cell(String[] cells, int col) {
        return col >= 0 && col < cells.length && cells[col] != null ? cells[col] : "-";
    }

    private static String csvCell(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String pad(String value, int width) {
        int w = displayWidth(value);
        StringBuilder sb = new StringBuilder(value);
        // at least one separator column, so cells never touch
        int fill = Math.max(1, width - w);
        for (int i = 0; i < fill; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** Approximate terminal width: CJK / full-width glyphs occupy two cells. */
    private static int displayWidth(String value) {
        int w = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            w += (cp >= 0x1100 && (cp <= 0x115F || (cp >= 0x2E80 && cp <= 0xA4CF)
                    || (cp >= 0xAC00 && cp <= 0xD7A3) || (cp >= 0xF900 && cp <= 0xFAFF)
                    || (cp >= 0xFE30 && cp <= 0xFE6F) || (cp >= 0xFF00 && cp <= 0xFF60)
                    || (cp >= 0xFFE0 && cp <= 0xFFE6))) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return w;
    }
}
