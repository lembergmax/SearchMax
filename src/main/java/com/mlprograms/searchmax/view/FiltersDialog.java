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
        setLayout(new BorderLayout(8, 8));

        // drei Spalten: Includes | Excludes | Extensions
        JPanel center = new JPanel(new GridLayout(1, 3, 8, 8));

        // Includes
        JPanel incPanel = new JPanel(new BorderLayout(4, 4));
        incPanel.setBorder(BorderFactory.createTitledBorder("Dateiname enthält"));
        JTable incTable = new JTable(includesModel);
        incTable.setFillsViewportHeight(true);
        incTable.setRowHeight(24);
        // Entfernen-Button Spalte Renderer/Editor
        incTable.getColumnModel().getColumn(includesModel.getRemoveColumnIndex()).setCellRenderer(new ButtonRenderer());
        incTable.getColumnModel().getColumn(includesModel.getRemoveColumnIndex()).setCellEditor(new ButtonEditor(new JButton("Entfernen"), includesModel));
        incPanel.add(new JScrollPane(incTable), BorderLayout.CENTER);

        // Buttons unterhalb (klein) - Hinweis: der einzelne 'Entfernen'-Knopf neben 'Hinzufügen' wurde entfernt
        JPanel incBtnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        Dimension small = new Dimension(120, 24);
        JButton incAdd = new JButton("Hinzufügen");
        incAdd.setPreferredSize(small);
        JButton incEnableAll = new JButton("Alle aktivieren");
        incEnableAll.setPreferredSize(small);
        JButton incDisableAll = new JButton("Alle deaktivieren");
        incDisableAll.setPreferredSize(small);
        incBtnBar.add(incAdd);
        incBtnBar.add(incEnableAll);
        incBtnBar.add(incDisableAll);
        incPanel.add(incBtnBar, BorderLayout.SOUTH);

        incAdd.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, "Neues Muster (z.B. Teil des Dateinamens):", "Hinzufügen", JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim();
                if (!t.isEmpty()) includesModel.addEntry(t, true);
            }
        });
        incEnableAll.addActionListener(e -> includesModel.setAllEnabled(true));
        incDisableAll.addActionListener(e -> includesModel.setAllEnabled(false));

        // Excludes
        JPanel excPanel = new JPanel(new BorderLayout(4, 4));
        excPanel.setBorder(BorderFactory.createTitledBorder("Dateiname enthält nicht"));
        JTable excTable = new JTable(excludesModel);
        excTable.setFillsViewportHeight(true);
        excTable.setRowHeight(24);
        excTable.getColumnModel().getColumn(excludesModel.getRemoveColumnIndex()).setCellRenderer(new ButtonRenderer());
        excTable.getColumnModel().getColumn(excludesModel.getRemoveColumnIndex()).setCellEditor(new ButtonEditor(new JButton("Entfernen"), excludesModel));
        excPanel.add(new JScrollPane(excTable), BorderLayout.CENTER);

        JPanel excBtnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        JButton excAdd = new JButton("Hinzufügen");
        excAdd.setPreferredSize(small);
        JButton excEnableAll = new JButton("Alle aktivieren");
        excEnableAll.setPreferredSize(small);
        JButton excDisableAll = new JButton("Alle deaktivieren");
        excDisableAll.setPreferredSize(small);
        excBtnBar.add(excAdd);
        excBtnBar.add(excEnableAll);
        excBtnBar.add(excDisableAll);
        excPanel.add(excBtnBar, BorderLayout.SOUTH);

        excAdd.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, "Neues Muster (z.B. Teil des Dateinamens):", "Hinzufügen", JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim();
                if (!t.isEmpty()) excludesModel.addEntry(t, true);
            }
        });
        excEnableAll.addActionListener(e -> excludesModel.setAllEnabled(true));
        excDisableAll.addActionListener(e -> excludesModel.setAllEnabled(false));

        // ------------------ Extensions Panel ------------------
        JPanel extPanel = new JPanel(new BorderLayout(4,4));
        extPanel.setBorder(BorderFactory.createTitledBorder("Dateiendungen (Suffix, z.B. .txt)"));
        // Internes TableModel für Endungen (ähnlich wie in ExtensionsDialog)
        class ExtModel extends AbstractTableModel {
            class E { boolean enabled; String ext; E(boolean en, String ex) { this.enabled = en; this.ext = ex; } }
            private final List<E> list = new ArrayList<>();
            private final String[] cols = {"Aktiv","Endung",""};
            public List<E> getEntries() { return list; }
            public void add(String ex, boolean en) { for (E e : list) if (e.ext.equals(ex)) return; list.add(new E(en, ex)); fireTableDataChanged(); }
            public void removeAt(int i) { if (i>=0 && i<list.size()) { list.remove(i); fireTableDataChanged(); } }
            public void setAllEnabled(boolean en) { for (E e : list) e.enabled = en; fireTableDataChanged(); }
            public boolean contains(String ex) { for (E e : list) if (e.ext.equals(ex)) return true; return false; }
            @Override public int getRowCount() { return list.size(); }
            @Override public int getColumnCount() { return cols.length; }
            @Override public String getColumnName(int c) { return cols[c]; }
            @Override public Class<?> getColumnClass(int c) { return c==0?Boolean.class:(c==1?String.class:Object.class); }
            @Override public boolean isCellEditable(int r,int c) { return c==0||c==1||c==2; }
            @Override public Object getValueAt(int r,int c) { E e = list.get(r); if (c==0) return e.enabled; if (c==1) return e.ext; return "Entfernen"; }
            @Override public void setValueAt(Object val,int r,int c) { if (r<0||r>=list.size()) return; E e=list.get(r); if (c==0 && val instanceof Boolean) { e.enabled=(Boolean)val; fireTableCellUpdated(r,c); } else if (c==1 && val instanceof String) { String v=((String)val).trim().toLowerCase(); if (v.isEmpty()) return; if (!v.startsWith(".")) v = "."+v; for (int i=0;i<list.size();i++){ if (i==r) continue; if (list.get(i).ext.equals(v)){ JOptionPane.showMessageDialog(null, "Endung existiert bereits.", "Fehler", JOptionPane.WARNING_MESSAGE); return; } } e.ext=v; fireTableCellUpdated(r,c); } else if (c==2) { removeAt(r); } }
            public int getRemoveColumnIndex() { return 2; }
        }

        ExtModel extModel = new ExtModel();
        JTable extTable = new JTable(extModel);
        extTable.setFillsViewportHeight(true);
        extTable.setRowHeight(24);
        // remove button col
        extTable.getColumnModel().getColumn(extModel.getRemoveColumnIndex()).setCellRenderer(new ButtonRenderer());
        // Note: ButtonEditor expects FiltersTableModel, but for extensions we'll reuse a simple editor below
        // Instead, set our own editor
        class ExtButtonEditor extends AbstractCellEditor implements TableCellEditor {
            private final JButton btn = new JButton("Entfernen");
            private JTable tbl;
            public ExtButtonEditor() {
                btn.addActionListener(a -> { if (tbl != null) { int r = tbl.getEditingRow(); if (r >= 0) extModel.removeAt(r); fireEditingStopped(); } });
            }
            @Override public Object getCellEditorValue() { return btn.getText(); }
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) { this.tbl = table; return btn; }
        }
        extTable.getColumnModel().getColumn(extModel.getRemoveColumnIndex()).setCellEditor(new ExtButtonEditor());
        extPanel.add(new JScrollPane(extTable), BorderLayout.CENTER);

        JPanel extBtnBar = new JPanel(new FlowLayout(FlowLayout.CENTER,6,6));
        JButton extAdd = new JButton("Hinzufügen"); extAdd.setPreferredSize(small);
        JButton extEnableAll = new JButton("Alle aktivieren"); extEnableAll.setPreferredSize(small);
        JButton extDisableAll = new JButton("Alle deaktivieren"); extDisableAll.setPreferredSize(small);
        extBtnBar.add(extAdd); extBtnBar.add(extEnableAll); extBtnBar.add(extDisableAll);
        extPanel.add(extBtnBar, BorderLayout.SOUTH);

        extAdd.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, "Neue Dateiendung (z.B. .txt oder txt):", "Hinzufügen", JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim().toLowerCase(); if (t.isEmpty()) return; if (!t.startsWith(".")) t = "."+t; if (extModel.contains(t)) { JOptionPane.showMessageDialog(this, "Endung existiert bereits.", "Fehler", JOptionPane.WARNING_MESSAGE); } else extModel.add(t, true);
            }
        });
        extEnableAll.addActionListener(e -> extModel.setAllEnabled(true));
        extDisableAll.addActionListener(e -> extModel.setAllEnabled(false));

        // add the extensions panel as third column
        center.add(incPanel);
        center.add(excPanel);
        center.add(extPanel);
        add(center, BorderLayout.CENTER);

        // store getter helpers for extensions
        this.extensionsGetter = () -> {
            Map<String, Boolean> out = new LinkedHashMap<>();
            for (ExtModel.E en : extModel.getEntries()) out.put(en.ext, en.enabled);
            return out;
        };

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

        // ESC
        getRootPane().registerKeyboardAction(e -> {
            confirmed = false;
            setVisible(false);
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public Map<String, Boolean> getIncludesMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : includesModel.getEntries()) out.put(en.pattern, en.enabled);
        return out;
    }

    public Map<String, Boolean> getIncludesCaseMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : includesModel.getEntries()) out.put(en.pattern, en.caseSensitive);
        return out;
    }

    public Map<String, Boolean> getExcludesMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : excludesModel.getEntries()) out.put(en.pattern, en.enabled);
        return out;
    }

    public Map<String, Boolean> getExcludesCaseMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : excludesModel.getEntries()) out.put(en.pattern, en.caseSensitive);
        return out;
    }

    // Extensions getter (gesetzt in initUI)
    private java.util.function.Supplier<Map<String,Boolean>> extensionsGetter = () -> new LinkedHashMap<>();

    public Map<String, Boolean> getExtensionsMap() {
        return extensionsGetter.get();
    }

    // TableModel
    static class FiltersTableModel extends AbstractTableModel {
        static class Entry {
            boolean enabled;
            String pattern;
            boolean caseSensitive;

            Entry(boolean enabled, String pattern) {
                this.enabled = enabled;
                this.pattern = pattern;
            }
        }

        private final List<Entry> entries = new ArrayList<>();
        // Spalten: Aktiv, Muster, Groß/Klein beachten, Entfernen
        private final String[] cols = {"Aktiv","Muster","Groß-/Kleinschreibung beachten",""};

        public List<Entry> getEntries() {
            return entries;
        }

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
            if (columnIndex == 0 || columnIndex == 2) return Boolean.class;
            return columnIndex == 1 ? String.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // Aktiv und Muster und Case sind editierbar; letzte Spalte ist Entfernen-Button
            return columnIndex == 0 || columnIndex == 1 || columnIndex == 2 || columnIndex == 3;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Entry e = entries.get(rowIndex);
            switch (columnIndex) {
                case 0: return e.enabled;
                case 1: return e.pattern;
                case 2: return e.caseSensitive;
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
                if (v.isEmpty()) return; // ignore empty
                // Prüfe Duplikate
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
            } else if (columnIndex == 3) {
                // Entfernen über Editor/Renderer
                removeAt(rowIndex);
            }
        }

        public int getRemoveColumnIndex() { return 3; }
    }

    // Button Renderer und Editor für die Entfernen-Spalte
    static class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(table.getFont());
            return this;
        }
    }

    static class ButtonEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
        private final JButton button;
        private final FiltersTableModel model;
        private JTable table;

        public ButtonEditor(JButton btn, FiltersTableModel model) {
            this.button = new JButton(btn.getText());
            this.button.addActionListener(this);
            this.model = model;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.table = table;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return button.getText();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (table != null) {
                int row = table.getEditingRow();
                if (row >= 0) model.removeAt(row);
            }
            fireEditingStopped();
        }
    }
}
