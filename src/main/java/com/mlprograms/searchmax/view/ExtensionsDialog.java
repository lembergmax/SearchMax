package com.mlprograms.searchmax.view;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
        table.setRowHeight(24);
        table.setPreferredScrollableViewportSize(new Dimension(320, 220));
        // Entfernen-Button Spalte Renderer/Editor
        table.getColumnModel().getColumn(model.getRemoveColumnIndex()).setCellRenderer(new ButtonRenderer());
        table.getColumnModel().getColumn(model.getRemoveColumnIndex()).setCellEditor(new ButtonEditor(new JButton("Entfernen"), model));
        JScrollPane sp = new JScrollPane(table);
        add(sp, BorderLayout.CENTER);

        // Kleine Buttons unter der Tabelle
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        Dimension small = new Dimension(120, 24);
        JButton addBtn = new JButton("Hinzufügen"); addBtn.setPreferredSize(small);
        JButton removeBtn = new JButton("Entfernen"); removeBtn.setPreferredSize(small);
        JButton enableAllBtn = new JButton("Alle aktivieren"); enableAllBtn.setPreferredSize(small);
        JButton disableAllBtn = new JButton("Alle deaktivieren"); disableAllBtn.setPreferredSize(small);
        btnBar.add(addBtn); btnBar.add(removeBtn); btnBar.add(enableAllBtn); btnBar.add(disableAllBtn);
        add(btnBar, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, "Neue Dateiendung (z.B. .txt oder txt):", "Hinzufügen", JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim().toLowerCase();
                if (!t.isEmpty()) {
                    if (!t.startsWith(".")) t = "." + t;
                    if (model.containsExtension(t)) {
                        JOptionPane.showMessageDialog(this, "Endung existiert bereits.", "Fehler", JOptionPane.WARNING_MESSAGE);
                    } else {
                        model.addEntry(t, true);
                    }
                }
            }
        });

        removeBtn.addActionListener(e -> {
            int sel = table.getSelectedRow();
            if (sel >= 0) {
                model.removeAt(sel);
            }
        });

        enableAllBtn.addActionListener(e -> model.setAllEnabled(true));
        disableAllBtn.addActionListener(e -> model.setAllEnabled(false));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Abbrechen");
        bottom.add(ok);
        bottom.add(cancel);
        add(bottom, BorderLayout.NORTH);

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
        // Spalten: Aktiv, Endung, Entfernen
        private final String[] cols = {"Aktiv", "Endung", ""};

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
        public void setAllEnabled(boolean enabled) {
            for (Entry e : entries) {
                e.enabled = enabled;
            }
            fireTableDataChanged();
        }
        public boolean containsExtension(String ext) {
            for (Entry e : entries) if (e.extension.equals(ext)) return true;
            return false;
        }

        @Override
        public int getRowCount() { return entries.size(); }

        @Override
        public int getColumnCount() { return cols.length; }

        @Override
        public String getColumnName(int column) { return cols[column]; }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : (columnIndex == 1 ? String.class : Object.class);
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0 || columnIndex == 1 || columnIndex == 2; // Aktiv-Checkbox und Endung editierbar; 2 = Entfernen-Button
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Entry e = entries.get(rowIndex);
            if (columnIndex == 0) return e.enabled;
            if (columnIndex == 1) return e.extension;
            return "Entfernen";
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= entries.size()) return;
            Entry e = entries.get(rowIndex);
            if (columnIndex == 0 && aValue instanceof Boolean) {
                e.enabled = (Boolean) aValue;
                fireTableCellUpdated(rowIndex, columnIndex);
            } else if (columnIndex == 1 && aValue instanceof String) {
                String v = ((String) aValue).trim().toLowerCase();
                if (v.isEmpty()) return;
                if (!v.startsWith(".")) v = "." + v;
                // Prüfe Duplikate
                for (int i = 0; i < entries.size(); i++) {
                    if (i == rowIndex) continue;
                    if (entries.get(i).extension.equals(v)) {
                        JOptionPane.showMessageDialog(null, "Endung existiert bereits.", "Fehler", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }
                e.extension = v;
                fireTableCellUpdated(rowIndex, columnIndex);
            } else if (columnIndex == 2) {
                removeAt(rowIndex);
            }
        }
        public int getRemoveColumnIndex() { return 2; }
    }

    // Button Renderer und Editor für die Entfernen-Spalte
    static class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() { setOpaque(true); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(table.getFont());
            return this;
        }
    }

    static class ButtonEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
        private final JButton button;
        private final ExtensionsTableModel model;
        private JTable table;

        public ButtonEditor(JButton btn, ExtensionsTableModel model) {
            this.button = new JButton(btn.getText());
            this.button.addActionListener(this);
            this.model = model;
        }

        @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.table = table;
            return button;
        }

        @Override public Object getCellEditorValue() { return button.getText(); }

        @Override public void actionPerformed(ActionEvent e) {
            if (table != null) {
                int row = table.getEditingRow();
                if (row >= 0) model.removeAt(row);
            }
            fireEditingStopped();
        }
    }
}
