package com.mlprograms.searchmax.view;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExtensionsDialog extends JDialog {

    private final ExtensionsTableModel model = new ExtensionsTableModel();
    private boolean confirmed = false;

    public ExtensionsDialog(Frame owner, List<String> initialExtensions) {
        this(owner, toMap(initialExtensions));
    }

    public ExtensionsDialog(Frame owner, Map<String, Boolean> initialExtensions) {
        super(owner, "Dateiendungen verwalten", true);
        if (initialExtensions != null) {
            for (Map.Entry<String, Boolean> en : initialExtensions.entrySet()) {
                String e = en.getKey();
                boolean enabled = Boolean.TRUE.equals(en.getValue());
                if (e == null) continue;
                String t = e.trim().toLowerCase();
                if (t.isEmpty()) continue;
                if (!t.startsWith(".")) t = "." + t;
                model.addEntry(t, enabled);
            }
        }
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private static Map<String, Boolean> toMap(List<String> list) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        if (list == null) return m;
        for (String s : list) {
            if (s == null) continue;
            String t = s.trim().toLowerCase();
            if (t.isEmpty()) continue;
            if (!t.startsWith(".")) t = "." + t;
            m.putIfAbsent(t, Boolean.TRUE);
        }
        return m;
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(new Dimension(300, 200));
        JScrollPane sp = new JScrollPane(table);
        add(sp, BorderLayout.CENTER);

        JPanel leftButtons = new JPanel(new GridLayout(0, 1, 4, 4));
        JButton addBtn = new JButton("Hinzufügen");
        JButton removeBtn = new JButton("Entfernen");
        leftButtons.add(addBtn);
        leftButtons.add(removeBtn);

        add(leftButtons, BorderLayout.EAST);

        addBtn.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, "Neue Dateiendung (z.B. .txt oder txt):", "Hinzufügen", JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim().toLowerCase();
                if (!t.isEmpty()) {
                    if (!t.startsWith(".")) t = "." + t;
                    model.addEntry(t, true);
                }
            }
        });

        removeBtn.addActionListener(e -> {
            int sel = table.getSelectedRow();
            if (sel >= 0) {
                model.removeAt(sel);
            }
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Abbrechen");
        bottom.add(ok);
        bottom.add(cancel);
        add(bottom, BorderLayout.SOUTH);

        ok.addActionListener(e -> {
            confirmed = true;
            setVisible(false);
        });
        cancel.addActionListener(e -> {
            confirmed = false;
            setVisible(false);
        });

        // ESC to cancel
        getRootPane().registerKeyboardAction(e -> {
            confirmed = false;
            setVisible(false);
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public List<String> getActiveExtensions() {
        List<String> out = new ArrayList<>();
        for (ExtensionsTableModel.Entry en : model.getEntries()) {
            if (en.enabled) out.add(en.extension);
        }
        return out;
    }

    public Map<String, Boolean> getExtensionsMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (ExtensionsTableModel.Entry en : model.getEntries()) {
            out.put(en.extension, en.enabled);
        }
        return out;
    }

    public boolean hasEntries() {
        return !model.getEntries().isEmpty();
    }

    // Interne TableModel-Klasse
    static class ExtensionsTableModel extends AbstractTableModel {
        static class Entry { boolean enabled; String extension; Entry(boolean enabled, String extension) { this.enabled = enabled; this.extension = extension; } }

        private final List<Entry> entries = new ArrayList<>();
        private final String[] cols = {"Aktiv", "Endung"};

        public List<Entry> getEntries() { return entries; }
        public void addEntry(String ext, boolean enabled) {
            // vermeide Duplikate
            for (Entry e : entries) if (e.extension.equals(ext)) return;
            entries.add(new Entry(enabled, ext));
            fireTableDataChanged();
        }
        public void removeAt(int idx) {
            if (idx >= 0 && idx < entries.size()) {
                entries.remove(idx);
                fireTableDataChanged();
            }
        }

        @Override
        public int getRowCount() { return entries.size(); }

        @Override
        public int getColumnCount() { return cols.length; }

        @Override
        public String getColumnName(int column) { return cols[column]; }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0; // nur Aktiv-Checkbox editierbar
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Entry e = entries.get(rowIndex);
            return columnIndex == 0 ? e.enabled : e.extension;
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
    }
}
