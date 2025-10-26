package com.mlprograms.searchmax.view;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class FiltersDialog extends JDialog {

    private final FiltersTableModel filtersModel = new FiltersTableModel();
    private final Map<String, Boolean> initialExtensionsAllow;
    private final Map<String, Boolean> initialExtensionsDeny;
    private final Map<String, Boolean> initialIncludesCase;
    private final Map<String, Boolean> initialExcludesCase;
    private boolean confirmed = false;

    public FiltersDialog(Frame owner, Map<String, Boolean> initialIncludes, Map<String, Boolean> initialExcludes, Map<String, Boolean> initialExtensionsAllow, Map<String, Boolean> initialExtensionsDeny, Map<String, Boolean> initialIncludesCase, Map<String, Boolean> initialExcludesCase) {
        super(owner, "Dateiname-Filter verwalten", true);
        this.initialExtensionsAllow = initialExtensionsAllow == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExtensionsAllow);
        this.initialExtensionsDeny = initialExtensionsDeny == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExtensionsDeny);
        this.initialIncludesCase = initialIncludesCase == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialIncludesCase);
        this.initialExcludesCase = initialExcludesCase == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExcludesCase);

        populateFilters(initialIncludes, initialExcludes);
        applyCaseFlags();
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void populateFilters(Map<String, Boolean> inc, Map<String, Boolean> exc) {
        if (inc != null) {
            for (Map.Entry<String, Boolean> e : inc.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                filtersModel.addEntry(t, enabled, false);
            }
        }

        if (exc != null) {
            for (Map.Entry<String, Boolean> e : exc.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                filtersModel.addEntry(t, enabled, true);
            }
        }
    }

    private void applyCaseFlags() {
        for (FiltersTableModel.Entry en : filtersModel.getEntries()) {
            boolean cs = false;
            if (!en.exclude) {
                Boolean v = this.initialIncludesCase.get(en.pattern);
                cs = Boolean.TRUE.equals(v);
            } else {
                Boolean v = this.initialExcludesCase.get(en.pattern);
                cs = Boolean.TRUE.equals(v);
            }
            en.caseSensitive = cs;
        }
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.add(createFiltersPanel());
        center.add(createExtensionsPanel());
        add(center, BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);
        registerEscKey();
    }

    private Component createFiltersPanel() {
        JPanel fltPanel = new JPanel(new BorderLayout(4, 4));
        fltPanel.setBorder(BorderFactory.createTitledBorder("Dateiname-Filter"));
        JTable fltTable = new JTable(filtersModel);
        fltTable.setFillsViewportHeight(true);
        fltTable.setRowHeight(24);
        fltTable.getColumnModel().getColumn(filtersModel.getRemoveColumnIndex()).setCellRenderer(new ButtonCellRenderer());
        fltTable.getColumnModel().getColumn(filtersModel.getRemoveColumnIndex()).setCellEditor(new ButtonCellEditor(() -> {
            int r = fltTable.getEditingRow();
            if (r >= 0) filtersModel.removeAt(r);
        }));
        fltPanel.add(new JScrollPane(fltTable), BorderLayout.CENTER);

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

        return fltPanel;
    }

    private Component createExtensionsPanel() {
        JPanel extPanel = new JPanel(new BorderLayout(4, 4));
        extPanel.setBorder(BorderFactory.createTitledBorder("Dateiendungen (Suffix, z.B. .txt)"));

        AllowExtensionsTableModel allowModel = new AllowExtensionsTableModel();
        DenyExtensionsTableModel denyModel = new DenyExtensionsTableModel();

        populateExtensionModels(allowModel, denyModel);

        JTable allowTable = new JTable(allowModel);
        configureExtensionTable(allowTable, allowModel);
        JTable denyTable = new JTable(denyModel);
        configureExtensionTable(denyTable, denyModel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Zulassen", new JScrollPane(allowTable));
        tabs.add("Ausschließen", new JScrollPane(denyTable));

        extPanel.add(tabs, BorderLayout.CENTER);

        JPanel extBtnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        Dimension small = new Dimension(120, 24);
        JButton extAdd = new JButton("Hinzufügen");
        extAdd.setPreferredSize(small);
        JButton extEnableAll = new JButton("Alle aktivieren");
        extEnableAll.setPreferredSize(small);
        JButton extDisableAll = new JButton("Alle deaktivieren");
        extDisableAll.setPreferredSize(small);
        extBtnBar.add(extAdd);
        extBtnBar.add(extEnableAll);
        extBtnBar.add(extDisableAll);
        extPanel.add(extBtnBar, BorderLayout.SOUTH);

        extAdd.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, "Neue Dateiendung (z.B. .txt oder txt):", "Hinzufügen", JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim().toLowerCase();
                if (t.isEmpty()) return;
                if (!t.startsWith(".")) t = "." + t;
                int idx = tabs.getSelectedIndex();
                if (idx == 0) {
                    if (allowModel.contains(t)) {
                        JOptionPane.showMessageDialog(this, "Endung existiert bereits.", "Fehler", JOptionPane.WARNING_MESSAGE);
                    } else {
                        allowModel.add(t, true);
                    }
                } else {
                    if (denyModel.contains(t)) {
                        JOptionPane.showMessageDialog(this, "Endung existiert bereits.", "Fehler", JOptionPane.WARNING_MESSAGE);
                    } else {
                        denyModel.add(t, true);
                    }
                }
            }
        });

        extEnableAll.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) allowModel.setAllEnabled(true); else denyModel.setAllEnabled(true);
        });
        extDisableAll.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) allowModel.setAllEnabled(false); else denyModel.setAllEnabled(false);
        });

        this.extensionsAllowGetter = () -> {
            Map<String, Boolean> out = new LinkedHashMap<>();
            for (ExtensionsTableModelBase.Entry en : allowModel.getEntries()) out.put(en.ext, en.enabled);
            return out;
        };

        this.extensionsDenyGetter = () -> {
            Map<String, Boolean> out = new LinkedHashMap<>();
            for (ExtensionsTableModelBase.Entry en : denyModel.getEntries()) out.put(en.ext, en.enabled);
            return out;
        };

        return extPanel;
    }

    private void populateExtensionModels(AllowExtensionsTableModel allowModel, DenyExtensionsTableModel denyModel) {
        if (initialExtensionsAllow != null && !initialExtensionsAllow.isEmpty()) {
            for (Map.Entry<String, Boolean> en : initialExtensionsAllow.entrySet()) {
                String ex = en.getKey();
                boolean enabled = Boolean.TRUE.equals(en.getValue());
                if (ex == null) continue;
                String t = ex.trim().toLowerCase();
                if (t.isEmpty()) continue;
                if (!t.startsWith(".")) t = "." + t;
                allowModel.add(t, enabled);
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
                boolean foundInAllow = false;
                int allowIndex = -1;
                for (int i = 0; i < allowModel.getEntries().size(); i++) {
                    ExtensionsTableModelBase.Entry e = allowModel.getEntries().get(i);
                    if (e.ext.equals(t)) { foundInAllow = true; allowIndex = i; break; }
                }
                if (foundInAllow) {
                    allowModel.getEntries().remove(allowIndex);
                    allowModel.fireTableDataChanged();
                    denyModel.add(t, enabled);
                } else {
                    denyModel.add(t, enabled);
                }
            }
        }
    }

    private void configureExtensionTable(JTable table, ExtensionsTableModelBase model) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(model.getRemoveColumnIndex()).setCellRenderer(new ButtonCellRenderer());
        table.getColumnModel().getColumn(model.getRemoveColumnIndex()).setCellEditor(new ButtonCellEditor(() -> {
            int r = table.getEditingRow();
            if (r >= 0) model.removeAt(r);
        }));
    }

    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Abbrechen");
        bottom.add(ok);
        bottom.add(cancel);
        ok.addActionListener(e -> {
            confirmed = true;
            setVisible(false);
        });
        cancel.addActionListener(e -> {
            confirmed = false;
            setVisible(false);
        });
        return bottom;
    }

    private void registerEscKey() {
        getRootPane().registerKeyboardAction(e -> {
            confirmed = false;
            setVisible(false);
        }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public boolean isConfirmed() { return confirmed; }

    public java.util.Map<String, Boolean> getIncludesMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : filtersModel.getEntries()) if (!en.exclude) out.put(en.pattern, en.enabled);
        return out;
    }

    public java.util.Map<String, Boolean> getIncludesCaseMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : filtersModel.getEntries()) if (!en.exclude) out.put(en.pattern, en.caseSensitive);
        return out;
    }

    public java.util.Map<String, Boolean> getExcludesMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : filtersModel.getEntries()) if (en.exclude) out.put(en.pattern, en.enabled);
        return out;
    }

    public java.util.Map<String, Boolean> getExcludesCaseMap() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FiltersTableModel.Entry en : filtersModel.getEntries()) if (en.exclude) out.put(en.pattern, en.caseSensitive);
        return out;
    }

    private java.util.function.Supplier<java.util.Map<String,Boolean>> extensionsAllowGetter = () -> new LinkedHashMap<>();
    private java.util.function.Supplier<java.util.Map<String,Boolean>> extensionsDenyGetter = () -> new LinkedHashMap<>();

    public java.util.Map<String, Boolean> getExtensionsAllowMap() { return extensionsAllowGetter.get(); }
    public java.util.Map<String, Boolean> getExtensionsDenyMap() { return extensionsDenyGetter.get(); }
}
