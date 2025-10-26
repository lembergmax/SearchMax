package com.mlprograms.searchmax.model;

import com.mlprograms.searchmax.view.GuiConstants;

import javax.swing.JOptionPane;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class TextFiltersTableModel extends AbstractTableModel {
    public static class Entry {
        public boolean enabled;
        public String pattern;
        public boolean caseSensitive;

        public Entry(boolean enabled, String pattern) {
            this.enabled = enabled;
            this.pattern = pattern;
            this.caseSensitive = false;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final String[] cols = {GuiConstants.COLUMN_ACTIVE, GuiConstants.COLUMN_PATTERN, GuiConstants.COLUMN_CASE_SENSITIVE, GuiConstants.COLUMN_REMOVE};

    public List<Entry> getEntries() { return entries; }

    public void addEntry(String p, boolean enabled) {
        for (Entry e : entries) if (e.pattern.equals(p)) return;
        entries.add(new Entry(enabled, p));
        fireTableDataChanged();
    }

    public void removeAt(int idx) {
        if (idx >= 0 && idx < entries.size()) {
            entries.remove(idx);
            fireTableDataChanged();
        }
    }

    public void setAllEnabled(boolean enabled) {
        for (Entry e : entries) e.enabled = enabled;
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return entries.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int column) { return cols[column]; }
    @Override public Class<?> getColumnClass(int columnIndex) { return columnIndex == 0 || columnIndex == 2 ? Boolean.class : (columnIndex == 1 ? String.class : Object.class); }
    @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return columnIndex == 0 || columnIndex == 1 || columnIndex == 2 || columnIndex == 3; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Entry e = entries.get(rowIndex);
        switch (columnIndex) {
            case 0: return e.enabled;
            case 1: return e.pattern;
            case 2: return e.caseSensitive;
            default: return GuiConstants.COLUMN_REMOVE;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= entries.size()) return;
        Entry e = entries.get(rowIndex);
        if (columnIndex == 0 && aValue instanceof Boolean) {
            e.enabled = (Boolean) aValue;
            fireTableCellUpdated(rowIndex, columnIndex);
        } else if (columnIndex == 1 && aValue instanceof String) {
            String v = ((String) aValue).trim();
            if (v.isEmpty()) return;
            for (int i = 0; i < entries.size(); i++) {
                if (i == rowIndex) continue;
                if (entries.get(i).pattern.equals(v)) {
                    JOptionPane.showMessageDialog(null, "Pattern already exists.", "Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            e.pattern = v;
            fireTableCellUpdated(rowIndex, columnIndex);
        } else if (columnIndex == 2 && aValue instanceof Boolean) {
            e.caseSensitive = (Boolean) aValue;
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }

    public int getRemoveColumnIndex() { return 3; }
}
