package com.mlprograms.searchmax.model;

import com.mlprograms.searchmax.view.GuiConstants;

import javax.swing.table.AbstractTableModel;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Einfaches TableModel für Zeitspannen (Start/Ende) mit einem Modus (Datum+Uhrzeit / Datum / Uhrzeit).
 */
public class TimeRangeTableModel extends AbstractTableModel {

    public enum Mode {DATETIME, DATE, TIME}

    public static class Entry {
        public boolean enabled;
        public Date start;
        public Date end;
        public Mode mode;

        public Entry(boolean enabled, Date start, Date end, Mode mode) {
            this.enabled = enabled;
            this.start = start;
            this.end = end;
            this.mode = mode;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final String[] cols = {GuiConstants.COLUMN_ACTIVE, GuiConstants.COLUMN_START, GuiConstants.COLUMN_END, GuiConstants.COLUMN_TIME_MODE, GuiConstants.COLUMN_REMOVE};

    public List<Entry> getEntries() { return entries; }

    public void addEntry(Date start, Date end, Mode mode, boolean enabled) {
        // einfache Duplikat-Prüfung
        for (Entry e : entries) {
            if (e.start.equals(start) && e.end.equals(end) && e.mode == mode) return;
        }
        entries.add(new Entry(enabled, start, end, mode));
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
    @Override public Class<?> getColumnClass(int columnIndex) { return columnIndex == 0 ? Boolean.class : String.class; }
    @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return columnIndex == 0 || columnIndex == 4; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Entry e = entries.get(rowIndex);
        DateFormat dtf;
        switch (e.mode) {
            case DATE:
                dtf = DateFormat.getDateInstance(DateFormat.SHORT);
                break;
            case TIME:
                dtf = DateFormat.getTimeInstance(DateFormat.SHORT);
                break;
            default:
                dtf = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        }

        switch (columnIndex) {
            case 0: return e.enabled;
            case 1: return e.start == null ? "" : dtf.format(e.start);
            case 2: return e.end == null ? "" : dtf.format(e.end);
            case 3: return e.mode.name();
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
        }
    }

    public int getRemoveColumnIndex() { return 4; }
}

