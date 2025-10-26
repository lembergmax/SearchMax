package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.model.AllowExtensionsTableModel;
import com.mlprograms.searchmax.model.DenyExtensionsTableModel;
import com.mlprograms.searchmax.model.ExtensionsTableModelBase;
import com.mlprograms.searchmax.model.TextFiltersTableModel;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class FiltersDialog extends JDialog {

    private final TextFiltersTableModel includesTextModel = new TextFiltersTableModel();
    private final TextFiltersTableModel excludesTextModel = new TextFiltersTableModel();
    private final Map<String, Boolean> initialExtensionsAllow;
    private final Map<String, Boolean> initialExtensionsDeny;
    private final Map<String, Boolean> initialIncludesCase;
    private final Map<String, Boolean> initialExcludesCase;
    private boolean confirmed = false;
    private boolean includeAllMode = false;

    public FiltersDialog(Frame owner, Map<String, Boolean> initialIncludes, Map<String, Boolean> initialExcludes, Map<String, Boolean> initialExtensionsAllow, Map<String, Boolean> initialExtensionsDeny, Map<String, Boolean> initialIncludesCase, Map<String, Boolean> initialExcludesCase, boolean initialIncludeAllMode) {
        super(owner, GuiConstants.FILTERS_DIALOG_TITLE, true);
        this.initialExtensionsAllow = initialExtensionsAllow == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExtensionsAllow);
        this.initialExtensionsDeny = initialExtensionsDeny == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExtensionsDeny);
        this.initialIncludesCase = initialIncludesCase == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialIncludesCase);
        this.initialExcludesCase = initialExcludesCase == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExcludesCase);
        this.includeAllMode = initialIncludeAllMode;

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
                includesTextModel.addEntry(t, enabled);
            }
        }

        if (exc != null) {
            for (Map.Entry<String, Boolean> e : exc.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                excludesTextModel.addEntry(t, enabled);
            }
        }
    }

    private void applyCaseFlags() {
        for (TextFiltersTableModel.Entry en : includesTextModel.getEntries()) {
            Boolean v = this.initialIncludesCase.get(en.pattern);
            en.caseSensitive = Boolean.TRUE.equals(v);
        }
        for (TextFiltersTableModel.Entry en : excludesTextModel.getEntries()) {
            Boolean v = this.initialExcludesCase.get(en.pattern);
            en.caseSensitive = Boolean.TRUE.equals(v);
        }
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.add(createTextFiltersPanel());
        center.add(createExtensionsPanel());
        add(center, BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);
        registerEscKey();
    }

    private Component createTextFiltersPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(GuiConstants.FILTERS_PANEL_TITLE));

        JTable includeTable = new JTable(includesTextModel);
        configureTextTable(includeTable, includesTextModel);
        JTable excludeTable = new JTable(excludesTextModel);
        configureTextTable(excludeTable, excludesTextModel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add(GuiConstants.TAB_ALLOW, new JScrollPane(includeTable));
        tabs.add(GuiConstants.TAB_DENY, new JScrollPane(excludeTable));

        panel.add(tabs, BorderLayout.CENTER);

        JPanel topOptions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton anyBtn = new JRadioButton(GuiConstants.RADIO_ANY);
        JRadioButton allBtn = new JRadioButton(GuiConstants.RADIO_ALL);
        ButtonGroup group = new ButtonGroup();
        group.add(anyBtn);
        group.add(allBtn);
        anyBtn.setSelected(!includeAllMode);
        allBtn.setSelected(includeAllMode);
        anyBtn.addActionListener(e -> includeAllMode = false);
        allBtn.addActionListener(e -> includeAllMode = true);
        topOptions.add(anyBtn);
        topOptions.add(allBtn);
        panel.add(topOptions, BorderLayout.NORTH);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        Dimension small = new Dimension(120, 24);
        JButton add = new JButton(GuiConstants.BUTTON_ADD);
        add.setPreferredSize(small);
        JButton enableAll = new JButton(GuiConstants.BUTTON_ENABLE_ALL);
        enableAll.setPreferredSize(small);
        JButton disableAll = new JButton(GuiConstants.BUTTON_DISABLE_ALL);
        disableAll.setPreferredSize(small);
        btnBar.add(add);
        btnBar.add(enableAll);
        btnBar.add(disableAll);
        panel.add(btnBar, BorderLayout.SOUTH);

        add.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, GuiConstants.INPUT_ADD_PATTERN, GuiConstants.INPUT_ADD_TITLE, JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim();
                if (t.isEmpty()) return;
                int idx = tabs.getSelectedIndex();
                if (idx == 0) includesTextModel.addEntry(t, true);
                else excludesTextModel.addEntry(t, true);
            }
        });

        enableAll.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) includesTextModel.setAllEnabled(true);
            else excludesTextModel.setAllEnabled(true);
        });
        disableAll.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) includesTextModel.setAllEnabled(false);
            else excludesTextModel.setAllEnabled(false);
        });

        return panel;
    }

    private void configureTextTable(JTable table, TextFiltersTableModel model) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(model.getRemoveColumnIndex()).setCellRenderer(new ButtonCellRenderer());
        table.getColumnModel().getColumn(model.getRemoveColumnIndex()).setCellEditor(new ButtonCellEditor(model::removeAt));
    }

    private Component createExtensionsPanel() {
        JPanel extPanel = new JPanel(new BorderLayout(4, 4));
        extPanel.setBorder(BorderFactory.createTitledBorder(GuiConstants.EXT_PANEL_TITLE));

        AllowExtensionsTableModel allowModel = new AllowExtensionsTableModel();
        DenyExtensionsTableModel denyModel = new DenyExtensionsTableModel();

        populateExtensionModels(allowModel, denyModel);

        JTable allowTable = new JTable(allowModel);
        configureExtensionTable(allowTable, allowModel);
        JTable denyTable = new JTable(denyModel);
        configureExtensionTable(denyTable, denyModel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add(GuiConstants.TAB_ALLOW, new JScrollPane(allowTable));
        tabs.add(GuiConstants.TAB_DENY, new JScrollPane(denyTable));

        extPanel.add(tabs, BorderLayout.CENTER);

        JPanel extBtnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        Dimension small = new Dimension(120, 24);
        JButton extAdd = new JButton(GuiConstants.BUTTON_ADD);
        extAdd.setPreferredSize(small);
        JButton extEnableAll = new JButton(GuiConstants.BUTTON_ENABLE_ALL);
        extEnableAll.setPreferredSize(small);
        JButton extDisableAll = new JButton(GuiConstants.BUTTON_DISABLE_ALL);
        extDisableAll.setPreferredSize(small);
        extBtnBar.add(extAdd);
        extBtnBar.add(extEnableAll);
        extBtnBar.add(extDisableAll);
        extPanel.add(extBtnBar, BorderLayout.SOUTH);

        extAdd.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, GuiConstants.INPUT_NEW_EXTENSION, GuiConstants.INPUT_ADD_TITLE, JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim().toLowerCase();
                if (t.isEmpty()) return;
                if (!t.startsWith(".")) t = "." + t;
                int idx = tabs.getSelectedIndex();
                if (idx == 0) {
                    if (allowModel.contains(t)) {
                        JOptionPane.showMessageDialog(this, GuiConstants.MSG_EXTENSION_EXISTS, GuiConstants.MSG_ERROR_TITLE, JOptionPane.WARNING_MESSAGE);
                    } else {
                        allowModel.add(t, true);
                    }
                } else {
                    if (denyModel.contains(t)) {
                        JOptionPane.showMessageDialog(this, GuiConstants.MSG_EXTENSION_EXISTS, GuiConstants.MSG_ERROR_TITLE, JOptionPane.WARNING_MESSAGE);
                    } else {
                        denyModel.add(t, true);
                    }
                }
            }
        });

        extEnableAll.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) allowModel.setAllEnabled(true);
            else denyModel.setAllEnabled(true);
        });
        extDisableAll.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) allowModel.setAllEnabled(false);
            else denyModel.setAllEnabled(false);
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
                    if (e.ext.equals(t)) {
                        foundInAllow = true;
                        allowIndex = i;
                        break;
                    }
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
        table.getColumnModel().getColumn(model.getRemoveColumnIndex()).setCellEditor(new ButtonCellEditor(model::removeAt));
    }

    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton(GuiConstants.BUTTON_OK);
        JButton cancel = new JButton(GuiConstants.CANCEL_BUTTON);
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

    public boolean isConfirmed() {
        return confirmed;
    }

    public java.util.Map<String, Boolean> getIncludesMap() {
        java.util.Map<String, Boolean> out = new LinkedHashMap<>();
        for (TextFiltersTableModel.Entry en : includesTextModel.getEntries()) out.put(en.pattern, en.enabled);
        return out;
    }

    public java.util.Map<String, Boolean> getIncludesCaseMap() {
        java.util.Map<String, Boolean> out = new LinkedHashMap<>();
        for (TextFiltersTableModel.Entry en : includesTextModel.getEntries()) out.put(en.pattern, en.caseSensitive);
        return out;
    }

    public java.util.Map<String, Boolean> getExcludesMap() {
        java.util.Map<String, Boolean> out = new LinkedHashMap<>();
        for (TextFiltersTableModel.Entry en : excludesTextModel.getEntries()) out.put(en.pattern, en.enabled);
        return out;
    }

    public java.util.Map<String, Boolean> getExcludesCaseMap() {
        java.util.Map<String, Boolean> out = new LinkedHashMap<>();
        for (TextFiltersTableModel.Entry en : excludesTextModel.getEntries()) out.put(en.pattern, en.caseSensitive);
        return out;
    }

    private java.util.function.Supplier<java.util.Map<String, Boolean>> extensionsAllowGetter = () -> new LinkedHashMap<>();
    private java.util.function.Supplier<java.util.Map<String, Boolean>> extensionsDenyGetter = () -> new LinkedHashMap<>();

    public java.util.Map<String, Boolean> getExtensionsAllowMap() {
        return extensionsAllowGetter.get();
    }

    public java.util.Map<String, Boolean> getExtensionsDenyMap() {
        return extensionsDenyGetter.get();
    }

    public boolean isIncludeAllMode() {
        return includeAllMode;
    }
}
