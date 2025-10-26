package com.mlprograms.searchmax.view;

import javax.swing.JOptionPane;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public abstract class ExtensionsTableModelBase extends AbstractTableModel {
    public static class Entry {
        public boolean enabled;
        public String ext;
        public Entry(boolean enabled, String ext) { this.enabled = enabled; this.ext = ext; }
    }

    protected final List<Entry> entries = new ArrayList<>();
    protected final String[] cols = {"Aktiv","Endung",""};

    public List<Entry> getEntries() { return entries; }

    public void add(String ex, boolean en) {
        for (Entry e : entries) if (e.ext.equals(ex)) return;
        entries.add(new Entry(en, ex));
        fireTableDataChanged();
    }

    public void removeAt(int i) {
        if (i >= 0 && i < entries.size()) {
            entries.remove(i);
            fireTableDataChanged();
        }
    }

    public void setAllEnabled(boolean en) {
        for (Entry e : entries) e.enabled = en;
        fireTableDataChanged();
    }

    public boolean contains(String ex) {
        for (Entry e : entries) if (e.ext.equals(ex)) return true;
        return false;
    }

    @Override public int getRowCount() { return entries.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int c) { return cols[c]; }
    @Override public Class<?> getColumnClass(int c) { return c==0?Boolean.class:(c==1?String.class:Object.class); }
    @Override public boolean isCellEditable(int r,int c) { return c==0||c==1||c==2; }
    @Override public Object getValueAt(int r,int c) { Entry e = entries.get(r); if (c==0) return e.enabled; if (c==1) return e.ext; return "Entfernen"; }
    @Override public void setValueAt(Object val,int r,int c) {
        if (r<0||r>=entries.size()) return; Entry e = entries.get(r);
        if (c==0 && val instanceof Boolean) { e.enabled=(Boolean)val; fireTableCellUpdated(r,c); }
        else if (c==1 && val instanceof String) { String v=((String)val).trim().toLowerCase(); if (v.isEmpty()) return; if (!v.startsWith(".")) v = "."+v; for (int i=0;i<entries.size();i++){ if (i==r) continue; if (entries.get(i).ext.equals(v)){ JOptionPane.showMessageDialog(null, "Endung existiert bereits.", "Fehler", JOptionPane.WARNING_MESSAGE); return; } } e.ext=v; fireTableCellUpdated(r,c); }
    }

    public int getRemoveColumnIndex() { return 2; }
}
