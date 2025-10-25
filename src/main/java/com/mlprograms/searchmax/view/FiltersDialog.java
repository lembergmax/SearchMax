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

    // Ein einziges Modell für Dateinamen-Filter (mit Flag ob Ausschließen)
    private final FiltersTableModel filtersModel = new FiltersTableModel();
    private final Map<String, Boolean> initialExtensionsAllow; // initial allow extensions
    private final Map<String, Boolean> initialExtensionsDeny; // initial deny extensions
    private final Map<String, Boolean> initialIncludesCase; // initial case flags for includes
    private final Map<String, Boolean> initialExcludesCase; // initial case flags for excludes
    private boolean confirmed = false;

    public FiltersDialog(Frame owner, Map<String, Boolean> initialIncludes, Map<String, Boolean> initialExcludes) {
        super(owner, "Dateiname-Filter verwalten", true);
        this.initialExtensionsAllow = null;
        this.initialExtensionsDeny = null;
        this.initialIncludesCase = null;
        this.initialExcludesCase = null;
        if (initialIncludes != null) {
            for (Map.Entry<String, Boolean> e : initialIncludes.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                filtersModel.addEntry(t, enabled, false);
            }
        }
        if (initialExcludes != null) {
            for (Map.Entry<String, Boolean> e : initialExcludes.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                filtersModel.addEntry(t, enabled, true);
            }
        }
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    // Neuer Konstruktor mit initialen Endungen
    public FiltersDialog(Frame owner, Map<String, Boolean> initialIncludes, Map<String, Boolean> initialExcludes, Map<String, Boolean> initialExtensionsAllow, Map<String, Boolean> initialExtensionsDeny) {
        super(owner, "Dateiname-Filter verwalten", true);
        this.initialExtensionsAllow = initialExtensionsAllow == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExtensionsAllow);
        this.initialExtensionsDeny = initialExtensionsDeny == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExtensionsDeny);
        this.initialIncludesCase = null;
        this.initialExcludesCase = null;
        if (initialIncludes != null) {
            for (Map.Entry<String, Boolean> e : initialIncludes.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                filtersModel.addEntry(t, enabled, false);
            }
        }
        if (initialExcludes != null) {
            for (Map.Entry<String, Boolean> e : initialExcludes.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                filtersModel.addEntry(t, enabled, true);
            }
        }
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    // Neuer Konstruktor: inkl. initiale Groß-/Kleinschreibungs-Maps
    public FiltersDialog(Frame owner, Map<String, Boolean> initialIncludes, Map<String, Boolean> initialExcludes, Map<String, Boolean> initialExtensionsAllow, Map<String, Boolean> initialExtensionsDeny, Map<String, Boolean> initialIncludesCase, Map<String, Boolean> initialExcludesCase) {
        super(owner, "Dateiname-Filter verwalten", true);
        this.initialExtensionsAllow = initialExtensionsAllow == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExtensionsAllow);
        this.initialExtensionsDeny = initialExtensionsDeny == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExtensionsDeny);
        this.initialIncludesCase = initialIncludesCase == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialIncludesCase);
        this.initialExcludesCase = initialExcludesCase == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExcludesCase);

        if (initialIncludes != null) {
            for (Map.Entry<String, Boolean> e : initialIncludes.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                filtersModel.addEntry(t, enabled, false);
            }
        }
        if (initialExcludes != null) {
            for (Map.Entry<String, Boolean> e : initialExcludes.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                filtersModel.addEntry(t, enabled, true);
            }
        }

        // apply case-sensitivity flags if provided
        if (this.initialIncludesCase != null || this.initialExcludesCase != null) {
            for (FiltersTableModel.Entry en : filtersModel.getEntries()) {
                boolean cs = false;
                if (!en.exclude && this.initialIncludesCase != null) {
                    Boolean v = this.initialIncludesCase.get(en.pattern);
                    cs = Boolean.TRUE.equals(v);
                } else if (en.exclude && this.initialExcludesCase != null) {
                    Boolean v = this.initialExcludesCase.get(en.pattern);
                    cs = Boolean.TRUE.equals(v);
                }
                en.caseSensitive = cs;
            }
        }

        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        // zwei Spalten: Dateiname-Filter (inkl. Ausschließen) | Extensions
        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));

        // Combined Filters Panel
        JPanel fltPanel = new JPanel(new BorderLayout(4, 4));
        fltPanel.setBorder(BorderFactory.createTitledBorder("Dateiname-Filter"));
        JTable fltTable = new JTable(filtersModel);
        fltTable.setFillsViewportHeight(true);
        fltTable.setRowHeight(24);
        // Entfernen-Button Spalte Renderer/Editor
        fltTable.getColumnModel().getColumn(filtersModel.getRemoveColumnIndex()).setCellRenderer(new ButtonRenderer());
        fltTable.getColumnModel().getColumn(filtersModel.getRemoveColumnIndex()).setCellEditor(new ButtonEditor(new JButton("Entfernen"), filtersModel));
        fltPanel.add(new JScrollPane(fltTable), BorderLayout.CENTER);

        // Buttons unterhalb
        JPanel fltBtnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        Dimension small = new Dimension(120, 24);
        JButton fltAdd = new JButton("Hinzufügen");
        fltAdd.setPreferredSize(small);
        JButton fltEnableAll = new JButton("Alle aktivieren");
        fltEnableAll.setPreferredSize(small);
        JButton fltDisableAll = new JButton("Alle deaktivieren");
        fltDisableAll.setPreferredSize(small);
        fltBtnBar.add(fltAdd);
        fltBtnBar.add(fltEnableAll);
        fltBtnBar.add(fltDisableAll);
        fltPanel.add(fltBtnBar, BorderLayout.SOUTH);

        fltAdd.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, "Neues Muster (z.B. Teil des Dateinamens):", "Hinzufügen", JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim();
                if (!t.isEmpty()) filtersModel.addEntry(t, true, false);
            }
        });
        fltEnableAll.addActionListener(e -> filtersModel.setAllEnabled(true));
        fltDisableAll.addActionListener(e -> filtersModel.setAllEnabled(false));

        // ------------------ Extensions Panel ------------------
        JPanel extPanel = new JPanel(new BorderLayout(4,4));
        extPanel.setBorder(BorderFactory.createTitledBorder("Dateiendungen (Suffix, z.B. .txt)"));
        // Internes TableModel für Endungen (Allow / Exclude)
        class ExtModel extends AbstractTableModel {
            class E { boolean enabled; String ext; boolean exclude; E(boolean en, String ex, boolean exl) { this.enabled = en; this.ext = ex; this.exclude = exl; } }
            private final List<E> list = new ArrayList<>();
            // Spalten: Aktiv, Endung, Ausschließen, Entfernen
            private final String[] cols = {"Aktiv","Endung","Ausschließen",""};
            public List<E> getEntries() { return list; }
            public void add(String ex, boolean en) { for (E e : list) if (e.ext.equals(ex)) return; list.add(new E(en, ex, false)); fireTableDataChanged(); }
            public void removeAt(int i) { if (i>=0 && i<list.size()) { list.remove(i); fireTableDataChanged(); } }
            public void setAllEnabled(boolean en) { for (E e : list) e.enabled = en; fireTableDataChanged(); }
            public boolean contains(String ex) { for (E e : list) if (e.ext.equals(ex)) return true; return false; }
            @Override public int getRowCount() { return list.size(); }
            @Override public int getColumnCount() { return cols.length; }
            @Override public String getColumnName(int c) { return cols[c]; }
            @Override public Class<?> getColumnClass(int c) { return c==0||c==2?Boolean.class:(c==1?String.class:Object.class); }
            @Override public boolean isCellEditable(int r,int c) { return c==0||c==1||c==2||c==3; }
            @Override public Object getValueAt(int r,int c) { E e = list.get(r); if (c==0) return e.enabled; if (c==1) return e.ext; if (c==2) return e.exclude; return "Entfernen"; }
            @Override public void setValueAt(Object val,int r,int c) { if (r<0||r>=list.size()) return; E e=list.get(r); if (c==0 && val instanceof Boolean) { e.enabled=(Boolean)val; fireTableCellUpdated(r,c); } else if (c==1 && val instanceof String) { String v=((String)val).trim().toLowerCase(); if (v.isEmpty()) return; if (!v.startsWith(".")) v = "."+v; for (int i=0;i<list.size();i++){ if (i==r) continue; if (list.get(i).ext.equals(v)){ JOptionPane.showMessageDialog(null, "Endung existiert bereits.", "Fehler", JOptionPane.WARNING_MESSAGE); return; } } e.ext=v; fireTableCellUpdated(r,c); } else if (c==2 && val instanceof Boolean) { e.exclude=(Boolean)val; fireTableCellUpdated(r,c); } else if (c==3) { removeAt(r); } }
            public int getRemoveColumnIndex() { return 3; }
        }

        ExtModel extModel = new ExtModel();
        // populate extModel from initialExtensionsAllow/initialExtensionsDeny if provided
        if (initialExtensionsAllow != null && !initialExtensionsAllow.isEmpty()) {
            for (Map.Entry<String, Boolean> en : initialExtensionsAllow.entrySet()) {
                String ex = en.getKey();
                boolean enabled = Boolean.TRUE.equals(en.getValue());
                if (ex == null) continue;
                String t = ex.trim().toLowerCase();
                if (t.isEmpty()) continue;
                if (!t.startsWith(".")) t = "." + t;
                extModel.add(t, enabled);
            }
        }
        if (initialExtensionsDeny != null && !initialExtensionsDeny.isEmpty()) {
            for (Map.Entry<String, Boolean> en : initialExtensionsDeny.entrySet()) {
                String ex = en.getKey();
                boolean enabled = Boolean.TRUE.equals(en.getValue());
                if (ex == null) continue;
                String t = ex.trim().toLowerCase();
                if (t.isEmpty()) continue;
                if (!t.startsWith(".")) t = "." + t;
                // if already present, mark exclude; else add as exclude
                boolean found = false;
                for (ExtModel.E e : extModel.getEntries()) {
                    if (e.ext.equals(t)) { e.exclude = true; e.enabled = enabled; found = true; break; }
                }
                if (!found) {
                    ExtModel.E ne = extModel.new E(enabled, t, true);
                    extModel.getEntries().add(ne);
                    extModel.fireTableDataChanged();
                }
            }
        }
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

        // add the two panels
        center.add(fltPanel);
        center.add(extPanel);
        add(center, BorderLayout.CENTER);

        // store getter helpers for extensions (allow / deny)
        this.extensionsAllowGetter = () -> {
             Map<String, Boolean> out = new LinkedHashMap<>();
             for (ExtModel.E en : extModel.getEntries()) if (!en.exclude) out.put(en.ext, en.enabled);
             return out;
         };
         this.extensionsDenyGetter = () -> {
             Map<String, Boolean> out = new LinkedHashMap<>();
             for (ExtModel.E en : extModel.getEntries()) if (en.exclude) out.put(en.ext, en.enabled);
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
        for (FiltersTableModel.Entry en : filtersModel.getEntries()) if (!en.exclude) out.put(en.pattern, en.enabled);
        return out;
    }

    public Map<String, Boolean> getIncludesCaseMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : filtersModel.getEntries()) if (!en.exclude) out.put(en.pattern, en.caseSensitive);
        return out;
    }

    public Map<String, Boolean> getExcludesMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : filtersModel.getEntries()) if (en.exclude) out.put(en.pattern, en.enabled);
        return out;
    }

    public Map<String, Boolean> getExcludesCaseMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : filtersModel.getEntries()) if (en.exclude) out.put(en.pattern, en.caseSensitive);
        return out;
    }

    // Extensions getters (gesetzt in initUI)
    private java.util.function.Supplier<Map<String,Boolean>> extensionsAllowGetter = () -> new LinkedHashMap<>();
    private java.util.function.Supplier<Map<String,Boolean>> extensionsDenyGetter = () -> new LinkedHashMap<>();

    public Map<String, Boolean> getExtensionsAllowMap() { return extensionsAllowGetter.get(); }
    public Map<String, Boolean> getExtensionsDenyMap() { return extensionsDenyGetter.get(); }

    // TableModel
    static class FiltersTableModel extends AbstractTableModel {
        static class Entry {
            boolean enabled;
            String pattern;
            boolean caseSensitive;
            boolean exclude;

            Entry(boolean enabled, String pattern, boolean exclude) {
                this.enabled = enabled;
                this.pattern = pattern;
                this.exclude = exclude;
            }
        }

        private final List<Entry> entries = new ArrayList<>();
        // Spalten: Aktiv, Muster, Groß/Klein beachten, Ausschließen, Entfernen
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
            // Aktiv, Muster, Case, Ausschließen editierbar; letzte Spalte ist Entfernen-Button
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
            } else if (columnIndex == 3 && aValue instanceof Boolean) {
                e.exclude = (Boolean) aValue;
                fireTableCellUpdated(rowIndex, columnIndex);
            } else if (columnIndex == 4) {
                // Entfernen über Editor/Renderer
                removeAt(rowIndex);
            }
        }

        public int getRemoveColumnIndex() { return 4; }
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
