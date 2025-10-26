package com.mlprograms.searchmax.view;

import javax.swing.JOptionPane;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class FiltersTableModel extends AbstractTableModel {
    public static class Entry {
        public boolean enabled;
        public String pattern;
        public boolean caseSensitive;
        public boolean exclude;

        public Entry(boolean enabled, String pattern, boolean exclude) {
            this.enabled = enabled;
            this.pattern = pattern;
            this.exclude = exclude;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final String[] cols = {"Aktiv","Muster","Groß-/Kleinschreibung beachten","Ausschließen",""};

    public List<Entry> getEntries() {
        return entries;
    }

    public void addEntry(String p, boolean enabled) {
        addEntry(p, enabled, false);
    }

    public void addEntry(String p, boolean enabled, boolean exclude) {
        for (Entry e : entries) if (e.pattern.equals(p)) return;
        Entry ne = new Entry(enabled, p, exclude);
        ne.caseSensitive = false;
        entries.add(ne);
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

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return cols.length;
    }

    @Override
    public String getColumnName(int column) {
        return cols[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 0 || columnIndex == 2 || columnIndex == 3) return Boolean.class;
        return columnIndex == 1 ? String.class : Object.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0 || columnIndex == 1 || columnIndex == 2 || columnIndex == 3 || columnIndex == 4;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Entry e = entries.get(rowIndex);
        switch (columnIndex) {
            case 0: return e.enabled;
            case 1: return e.pattern;
            case 2: return e.caseSensitive;
            case 3: return e.exclude;
            default: return "Entfernen";
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
                    JOptionPane.showMessageDialog(null, "Muster existiert bereits.", "Fehler", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            e.pattern = v;
            fireTableCellUpdated(rowIndex, columnIndex);
        } else if (columnIndex == 2 && aValue instanceof Boolean) {
            e.caseSensitive = (Boolean) aValue;
            fireTableCellUpdated(rowIndex, columnIndex);
        } else if (columnIndex == 3 && aValue instanceof Boolean) {
            e.exclude = (Boolean) aValue;
            fireTableCellUpdated(rowIndex, columnIndex);
        } else if (columnIndex == 4) {
            removeAt(rowIndex);
        }
    }

    public int getRemoveColumnIndex() { return 4; }
}
