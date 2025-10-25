package com.mlprograms.searchmax.view;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FiltersDialog extends JDialog {

    private final FiltersTableModel includesModel = new FiltersTableModel();
    private final FiltersTableModel excludesModel = new FiltersTableModel();
    private boolean confirmed = false;

    public FiltersDialog(Frame owner, Map<String, Boolean> initialIncludes, Map<String, Boolean> initialExcludes) {
        super(owner, "Dateiname-Filter verwalten", true);
        if (initialIncludes != null) {
            for (Map.Entry<String, Boolean> e : initialIncludes.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                includesModel.addEntry(t, enabled);
            }
        }
        if (initialExcludes != null) {
            for (Map.Entry<String, Boolean> e : initialExcludes.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                excludesModel.addEntry(t, enabled);
            }
        }
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout(8,8));

        JPanel center = new JPanel(new GridLayout(1,2,8,8));

        // Includes
        JPanel incPanel = new JPanel(new BorderLayout(4,4));
        incPanel.setBorder(BorderFactory.createTitledBorder("Dateiname enthält"));
        JTable incTable = new JTable(includesModel);
        incTable.setFillsViewportHeight(true);
        incPanel.add(new JScrollPane(incTable), BorderLayout.CENTER);
        JPanel incBtns = new JPanel(new GridLayout(0,1,4,4));
        JButton incAdd = new JButton("Hinzufügen");
        JButton incRemove = new JButton("Entfernen");
        JButton incEnableAll = new JButton("Alle aktivieren");
        JButton incDisableAll = new JButton("Alle deaktivieren");
        incBtns.add(incAdd); incBtns.add(incRemove);
        incBtns.add(incEnableAll); incBtns.add(incDisableAll);
        incPanel.add(incBtns, BorderLayout.EAST);

        incAdd.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, "Neues Muster (z.B. Teil des Dateinamens):", "Hinzufügen", JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim();
                if (!t.isEmpty()) includesModel.addEntry(t, true);
            }
        });
        incRemove.addActionListener(e -> {
            int sel = incTable.getSelectedRow(); if (sel >= 0) includesModel.removeAt(sel);
        });
        incEnableAll.addActionListener(e -> includesModel.setAllEnabled(true));
        incDisableAll.addActionListener(e -> includesModel.setAllEnabled(false));

        // Excludes
        JPanel excPanel = new JPanel(new BorderLayout(4,4));
        excPanel.setBorder(BorderFactory.createTitledBorder("Dateiname enthält nicht"));
        JTable excTable = new JTable(excludesModel);
        excTable.setFillsViewportHeight(true);
        excPanel.add(new JScrollPane(excTable), BorderLayout.CENTER);
        JPanel excBtns = new JPanel(new GridLayout(0,1,4,4));
        JButton excAdd = new JButton("Hinzufügen");
        JButton excRemove = new JButton("Entfernen");
        JButton excEnableAll = new JButton("Alle aktivieren");
        JButton excDisableAll = new JButton("Alle deaktivieren");
        excBtns.add(excAdd); excBtns.add(excRemove);
        excBtns.add(excEnableAll); excBtns.add(excDisableAll);
        excPanel.add(excBtns, BorderLayout.EAST);

        excAdd.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, "Neues Muster (z.B. Teil des Dateinamens):", "Hinzufügen", JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim();
                if (!t.isEmpty()) excludesModel.addEntry(t, true);
            }
        });
        excRemove.addActionListener(e -> {
            int sel = excTable.getSelectedRow(); if (sel >= 0) excludesModel.removeAt(sel);
        });
        excEnableAll.addActionListener(e -> excludesModel.setAllEnabled(true));
        excDisableAll.addActionListener(e -> excludesModel.setAllEnabled(false));

        center.add(incPanel);
        center.add(excPanel);
        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK"); JButton cancel = new JButton("Abbrechen");
        bottom.add(ok); bottom.add(cancel);
        add(bottom, BorderLayout.SOUTH);

        ok.addActionListener(e -> { confirmed = true; setVisible(false); });
        cancel.addActionListener(e -> { confirmed = false; setVisible(false); });

        // ESC
        getRootPane().registerKeyboardAction(e -> { confirmed = false; setVisible(false); }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public boolean isConfirmed() { return confirmed; }

    public Map<String, Boolean> getIncludesMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : includesModel.getEntries()) out.put(en.pattern, en.enabled);
        return out;
    }

    public Map<String, Boolean> getExcludesMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : excludesModel.getEntries()) out.put(en.pattern, en.enabled);
        return out;
    }

    // TableModel
    static class FiltersTableModel extends AbstractTableModel {
        static class Entry { boolean enabled; String pattern; Entry(boolean enabled, String pattern) { this.enabled = enabled; this.pattern = pattern; } }
        private final List<Entry> entries = new ArrayList<>();
        private final String[] cols = {"Aktiv","Muster"};
        public List<Entry> getEntries() { return entries; }
        public void addEntry(String p, boolean enabled) { for (Entry e : entries) if (e.pattern.equals(p)) return; entries.add(new Entry(enabled,p)); fireTableDataChanged(); }
        public void removeAt(int idx) { if (idx>=0 && idx<entries.size()) { entries.remove(idx); fireTableDataChanged(); } }
        public void setAllEnabled(boolean enabled) { for (Entry e: entries) e.enabled = enabled; fireTableDataChanged(); }
        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }
        @Override public Class<?> getColumnClass(int columnIndex) { return columnIndex==0?Boolean.class:String.class; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return columnIndex==0; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) { Entry e = entries.get(rowIndex); return columnIndex==0?e.enabled:e.pattern; }
        @Override public void setValueAt(Object aValue,int rowIndex,int columnIndex) { if (rowIndex<0||rowIndex>=entries.size()) return; Entry e=entries.get(rowIndex); if (columnIndex==0 && aValue instanceof Boolean) { e.enabled=(Boolean)aValue; fireTableCellUpdated(rowIndex,columnIndex);} }
    }
}
