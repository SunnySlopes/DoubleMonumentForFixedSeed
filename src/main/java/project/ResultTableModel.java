package project;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

/**
 * Table model over {@link ResultTable} records: one column per panel column, read-only.
 *
 * <p>Rows are the same {@code String[]} records the CSV export writes, so the table, the text
 * export and the CSV export can never drift apart. The model is the single owner of the rows
 * (callers use {@link #rows()} for sorting/exporting).</p>
 */
final class ResultTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private final List<String[]> rows = new ArrayList<>();
    private final boolean batch;

    ResultTableModel(boolean batch) {
        this.batch = batch;
    }

    /** Live backing list; sort it in place and then call {@link #refresh()}. */
    List<String[]> rows() {
        return rows;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return ResultTable.panelColumns(batch).length;
    }

    @Override
    public String getColumnName(int column) {
        int[] cols = ResultTable.panelColumns(batch);
        return I18n.get("col." + ResultTable.KEYS[cols[column]]);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        int[] cols = ResultTable.panelColumns(batch);
        return ResultTable.cell(rows.get(rowIndex), cols[columnIndex]);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    void addRow(String[] cells) {
        rows.add(cells);
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    void clear() {
        int n = rows.size();
        rows.clear();
        if (n > 0) {
            fireTableRowsDeleted(0, n - 1);
        }
    }

    /** Rows were reordered or re-populated in place. */
    void refresh() {
        fireTableDataChanged();
    }

    /** Column names changed (language switch): rebuild the header and re-size the columns. */
    void structureChanged() {
        fireTableStructureChanged();
    }
}
