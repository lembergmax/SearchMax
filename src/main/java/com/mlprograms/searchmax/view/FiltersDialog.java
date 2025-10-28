package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.model.AllowExtensionsTableModel;
import com.mlprograms.searchmax.model.DenyExtensionsTableModel;
import com.mlprograms.searchmax.model.ExtensionsTableModelBase;
import com.mlprograms.searchmax.model.TextFiltersTableModel;
import com.mlprograms.searchmax.model.TimeRangeTableModel;

import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.TimePicker;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Date;

public class FiltersDialog extends JDialog {

    // --- Anpassbare UI-Größen (zentral hier ändern) -----------------
    private static final int DATE_PICKER_WIDTH = 220; // Breite des DatePickers (inkl. '...'-Button)
    private static final int TIME_PICKER_WIDTH = 110; // Breite des TimePickers
    private static final int PICKER_HEIGHT = 28;
    private static final int DIALOG_EXTRA_WIDTH = 250; // zusätzlicher Raum für Ränder
    private static final int DIALOG_EXTRA_HEIGHT = 75; // zusätzlicher Raum für Titel/Buttons
    // ----------------------------------------------------------------

    private final TextFiltersTableModel includesTextModel = new TextFiltersTableModel();
    private final TextFiltersTableModel excludesTextModel = new TextFiltersTableModel();
    // Neue Modelle für Datei-Inhalt
    private final TextFiltersTableModel includesContentModel = new TextFiltersTableModel();
    private final TextFiltersTableModel excludesContentModel = new TextFiltersTableModel();
    // Zeitspannen-Modelle
    private final TimeRangeTableModel includesTimeModel = new TimeRangeTableModel();
    private final TimeRangeTableModel excludesTimeModel = new TimeRangeTableModel();

    private final Map<String, Boolean> initialExtensionsAllow;
    private final Map<String, Boolean> initialExtensionsDeny;
    private final Map<String, Boolean> initialIncludesCase;
    private final Map<String, Boolean> initialExcludesCase;
    private final Map<String, Boolean> initialContentIncludes;
    private final Map<String, Boolean> initialContentExcludes;
    private final Map<String, Boolean> initialContentIncludesCase;
    private final Map<String, Boolean> initialContentExcludesCase;
    private boolean confirmed = false;
    private boolean includeAllMode; // Standard ist false, explizite Initialisierung entfernt
    // Modus für Inhalts-Filter: ob alle Filter passen müssen
    private boolean contentIncludeAllMode; // Standard ist false
    private boolean timeIncludeAllMode; // eigener Modus für Zeitfilter

    public FiltersDialog(Frame owner, Map<String, Boolean> initialIncludes, Map<String, Boolean> initialExcludes, Map<String, Boolean> initialExtensionsAllow, Map<String, Boolean> initialExtensionsDeny, Map<String, Boolean> initialIncludesCase, Map<String, Boolean> initialExcludesCase, boolean initialIncludeAllMode, Map<String, Boolean> initialContentIncludes, Map<String, Boolean> initialContentExcludes, Map<String, Boolean> initialContentIncludesCase, Map<String, Boolean> initialContentExcludesCase, boolean initialContentIncludeAllMode) {
        super(owner, GuiConstants.FILTERS_DIALOG_TITLE, true);
        this.initialExtensionsAllow = initialExtensionsAllow == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExtensionsAllow);
        this.initialExtensionsDeny = initialExtensionsDeny == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExtensionsDeny);
        this.initialIncludesCase = initialIncludesCase == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialIncludesCase);
        this.initialExcludesCase = initialExcludesCase == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialExcludesCase);
        this.initialContentIncludes = initialContentIncludes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialContentIncludes);
        this.initialContentExcludes = initialContentExcludes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialContentExcludes);
        this.initialContentIncludesCase = initialContentIncludesCase == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialContentIncludesCase);
        this.initialContentExcludesCase = initialContentExcludesCase == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initialContentExcludesCase);
        this.includeAllMode = initialIncludeAllMode;
        this.contentIncludeAllMode = initialContentIncludeAllMode;

        populateFilters(initialIncludes, initialExcludes);
        populateContentFilters(this.initialContentIncludes, this.initialContentExcludes);
        // Zeitwerte können später hinzugefügt werden - aktuell keine initialen TimeRanges geladen
        applyCaseFlags();
        applyContentCaseFlags();
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

    // Populate content (file-content) filters into the content table models
    private void populateContentFilters(Map<String, Boolean> inc, Map<String, Boolean> exc) {
        if (inc != null) {
            for (Map.Entry<String, Boolean> e : inc.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                includesContentModel.addEntry(t, enabled);
            }
        }

        if (exc != null) {
            for (Map.Entry<String, Boolean> e : exc.entrySet()) {
                String k = e.getKey();
                boolean enabled = Boolean.TRUE.equals(e.getValue());
                if (k == null) continue;
                String t = k.trim();
                if (t.isEmpty()) continue;
                excludesContentModel.addEntry(t, enabled);
            }
        }
    }

    // Apply initial case-sensitivity flags to content filter entries
    private void applyContentCaseFlags() {
        for (TextFiltersTableModel.Entry en : includesContentModel.getEntries()) {
            Boolean v = this.initialContentIncludesCase.get(en.pattern);
            en.caseSensitive = Boolean.TRUE.equals(v);
        }
        for (TextFiltersTableModel.Entry en : excludesContentModel.getEntries()) {
            Boolean v = this.initialContentExcludesCase.get(en.pattern);
            en.caseSensitive = Boolean.TRUE.equals(v);
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
        // Content models haben derzeit keine initialen Case-Flags (können später ergänzt werden)
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        // GridLayout auf 1x4 erweitert: Text | Content | Extensions | TimeRanges
        JPanel center = new JPanel(new GridLayout(1, 4, 8, 8));
        center.add(createTextFiltersPanel());
        center.add(createContentFiltersPanel());
        center.add(createExtensionsPanel());
        center.add(createTimeFiltersPanel());
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

    // Neues Panel für Datei-Inhalt
    private Component createContentFiltersPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(GuiConstants.CONTENT_PANEL_TITLE));

        JTable includeTable = new JTable(includesContentModel);
        configureTextTable(includeTable, includesContentModel);
        JTable excludeTable = new JTable(excludesContentModel);
        configureTextTable(excludeTable, excludesContentModel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add(GuiConstants.TAB_ALLOW, new JScrollPane(includeTable));
        tabs.add(GuiConstants.TAB_DENY, new JScrollPane(excludeTable));

        panel.add(tabs, BorderLayout.CENTER);

        // Top options: Any / All für Inhalts-Filter
        JPanel topOptions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton anyBtn = new JRadioButton(GuiConstants.RADIO_ANY);
        JRadioButton allBtn = new JRadioButton(GuiConstants.RADIO_ALL);
        ButtonGroup group = new ButtonGroup();
        group.add(anyBtn);
        group.add(allBtn);
        anyBtn.setSelected(!contentIncludeAllMode);
        allBtn.setSelected(contentIncludeAllMode);
        anyBtn.addActionListener(e -> contentIncludeAllMode = false);
        allBtn.addActionListener(e -> contentIncludeAllMode = true);
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
            String raw = JOptionPane.showInputDialog(this, GuiConstants.INPUT_ADD_CONTENT_PATTERN, GuiConstants.INPUT_ADD_TITLE, JOptionPane.PLAIN_MESSAGE);
            if (raw != null) {
                String t = raw.trim();
                if (t.isEmpty()) return;
                int idx = tabs.getSelectedIndex();
                if (idx == 0) includesContentModel.addEntry(t, true);
                else excludesContentModel.addEntry(t, true);
            }
        });

        enableAll.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) includesContentModel.setAllEnabled(true);
            else excludesContentModel.setAllEnabled(true);
        });
        disableAll.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) includesContentModel.setAllEnabled(false);
            else excludesContentModel.setAllEnabled(false);
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

        this.extensionsAllowGetter = LinkedHashMap::new;

        this.extensionsDenyGetter = LinkedHashMap::new;

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

    // Neues Panel für Zeitfilter
    private Component createTimeFiltersPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(GuiConstants.TIME_PANEL_TITLE));

        JTable includeTable = new JTable(includesTimeModel);
        configureTimeTable(includeTable, includesTimeModel);
        JTable excludeTable = new JTable(excludesTimeModel);
        configureTimeTable(excludeTable, excludesTimeModel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add(GuiConstants.TAB_ALLOW, new JScrollPane(includeTable));
        tabs.add(GuiConstants.TAB_DENY, new JScrollPane(excludeTable));

        panel.add(tabs, BorderLayout.CENTER);

        // Top options: Any / All für Zeit-Filter
        JPanel topOptions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton anyBtn = new JRadioButton(GuiConstants.RADIO_ANY);
        JRadioButton allBtn = new JRadioButton(GuiConstants.RADIO_ALL);
        ButtonGroup group = new ButtonGroup();
        group.add(anyBtn);
        group.add(allBtn);
        anyBtn.setSelected(!timeIncludeAllMode);
        allBtn.setSelected(timeIncludeAllMode);
        anyBtn.addActionListener(e -> timeIncludeAllMode = false);
        allBtn.addActionListener(e -> timeIncludeAllMode = true);
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
            // Dialog: Modus-Auswahl + klickbare Eingaben (DatePicker + TimePicker)
            JPanel inputPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 6, 6, 6);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JLabel modeLbl = new JLabel(GuiConstants.LABEL_MODE);
            String[] modes = {GuiConstants.MODE_TIME, GuiConstants.MODE_DATE, GuiConstants.MODE_DATETIME};
            JComboBox<String> modeCombo = new JComboBox<>(modes);

            // DatePicker / TimePicker setup (LGoodDatePicker)
            DatePicker startDatePicker = new DatePicker();
            DatePicker endDatePicker = new DatePicker();
            TimePicker startTimePicker = new TimePicker();
            TimePicker endTimePicker = new TimePicker();

            // Keine erzwungene preferredSize: DatePicker/TimePicker verwenden ihre nativen PreferredSizes
            // (so werden Panels nur so groß wie nötig)
            // moderate PreferredSizes so the DatePicker button is visible, but panels stay as small as necessary
            startDatePicker.setPreferredSize(new Dimension(DATE_PICKER_WIDTH, PICKER_HEIGHT));
            endDatePicker.setPreferredSize(new Dimension(DATE_PICKER_WIDTH, PICKER_HEIGHT));
            startTimePicker.setPreferredSize(new Dimension(TIME_PICKER_WIDTH, PICKER_HEIGHT));
            endTimePicker.setPreferredSize(new Dimension(TIME_PICKER_WIDTH, PICKER_HEIGHT));

            // Panels that combine Date + Time horizontally for a cleaner layout
            JPanel startPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            startPanel.add(startDatePicker);
            startPanel.add(startTimePicker);
            JPanel endPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            endPanel.add(endDatePicker);
            endPanel.add(endTimePicker);

            // sensible defaults
            startTimePicker.setTime(LocalTime.now().withSecond(0).withNano(0));
            endTimePicker.setTime(LocalTime.now().plusHours(1).withSecond(0).withNano(0));
            startDatePicker.setDate(LocalDate.now());
            endDatePicker.setDate(LocalDate.now());

            // Scroll pane wrapper so the dialog can shrink and still allow access to all controls
            inputPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

            // Editor switching based on mode; will revalidate the scroll's viewport if present
            final JScrollPane[] scrollRef = new JScrollPane[1];
            Runnable applyEditor = () -> {
                String sel = (String) modeCombo.getSelectedItem();
                boolean showDate = !GuiConstants.MODE_TIME.equals(sel);
                boolean showTime = !GuiConstants.MODE_DATE.equals(sel);
                startDatePicker.setVisible(showDate);
                endDatePicker.setVisible(showDate);
                startTimePicker.setVisible(showTime);
                endTimePicker.setVisible(showTime);
                // ensure defaults when switching to modes
                if (GuiConstants.MODE_DATETIME.equals(sel)) {
                    if (startDatePicker.getDate() == null) startDatePicker.setDate(LocalDate.now());
                    if (endDatePicker.getDate() == null) endDatePicker.setDate(LocalDate.now());
                    if (startTimePicker.getTime() == null) startTimePicker.setTime(LocalTime.now().withSecond(0).withNano(0));
                    if (endTimePicker.getTime() == null) endTimePicker.setTime(LocalTime.now().plusHours(1).withSecond(0).withNano(0));
                }
                if (scrollRef[0] != null) {
                    scrollRef[0].getViewport().revalidate();
                    scrollRef[0].revalidate();
                    scrollRef[0].repaint();
                } else {
                    inputPanel.revalidate();
                    inputPanel.repaint();
                }
            };
            applyEditor.run();
            modeCombo.addActionListener(ev -> applyEditor.run());

            // layout: Mode row
            gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1; inputPanel.add(modeLbl, gbc);
            gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2; inputPanel.add(modeCombo, gbc);
            // From row: label + combined panel
            gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; inputPanel.add(new JLabel(GuiConstants.LABEL_FROM), gbc);
            gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2; inputPanel.add(startPanel, gbc);
            // To row
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; inputPanel.add(new JLabel(GuiConstants.LABEL_TO), gbc);
            gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 2; inputPanel.add(endPanel, gbc);

            // Let the inputPanel size itself and only use a JScrollPane so the dialog can be smaller on demand
            JScrollPane scroll = new JScrollPane(inputPanel);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scrollRef[0] = scroll;

            // Build dialog from the scroll pane; pack so it's only as large as needed, but allow resizing
            JOptionPane pane = new JOptionPane(scroll, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            JDialog dialog = pane.createDialog(this, GuiConstants.INPUT_ADD_TIME_TITLE);
            dialog.pack();
            dialog.setResizable(true);
            // Make sure the dialog is at least as large as the inputPanel wants to be
            Dimension contentPref = inputPanel.getPreferredSize();
            if (contentPref != null && contentPref.width > 0 && contentPref.height > 0) {
                // account for dialog chrome (insets) and option button area
                Insets ins = dialog.getInsets();
                int extraW = ins.left + ins.right + DIALOG_EXTRA_WIDTH; // safety padding for borders
                int extraH = ins.top + ins.bottom + DIALOG_EXTRA_HEIGHT; // title + buttons area
                Dimension packed = dialog.getSize();
                int w = Math.max(packed.width, contentPref.width + extraW);
                int h = Math.max(packed.height, contentPref.height + extraH);
                dialog.setSize(w, h);
            }
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
             Object selected = pane.getValue();
             int result = JOptionPane.CLOSED_OPTION;
             if (selected instanceof Integer) result = (Integer) selected;
             if (result == JOptionPane.OK_OPTION) {
                String sel = (String) modeCombo.getSelectedItem();
                TimeRangeTableModel.Mode mode = TimeRangeTableModel.Mode.TIME;
                if (GuiConstants.MODE_DATE.equals(sel)) mode = TimeRangeTableModel.Mode.DATE;
                else if (GuiConstants.MODE_DATETIME.equals(sel)) mode = TimeRangeTableModel.Mode.DATETIME;

                Date start = null;
                Date end = null;

                // Compose Date from pickers according to mode
                if (mode == TimeRangeTableModel.Mode.TIME) {
                    // only times are relevant: use today's date normalized to epoch day for consistency
                    LocalTime st = startTimePicker.getTime();
                    LocalTime en = endTimePicker.getTime();
                    LocalDate base = LocalDate.of(1970, 1, 1);
                    LocalDateTime sdt = LocalDateTime.of(base, st == null ? LocalTime.MIDNIGHT : st);
                    LocalDateTime edt = LocalDateTime.of(base, en == null ? LocalTime.MIDNIGHT : en);
                    start = Date.from(sdt.atZone(ZoneId.systemDefault()).toInstant());
                    end = Date.from(edt.atZone(ZoneId.systemDefault()).toInstant());
                } else if (mode == TimeRangeTableModel.Mode.DATE) {
                    // only dates are relevant: times set to midnight
                    LocalDate sd = startDatePicker.getDate();
                    LocalDate ed = endDatePicker.getDate();
                    if (sd != null) start = Date.from(sd.atStartOfDay(ZoneId.systemDefault()).toInstant());
                    if (ed != null) end = Date.from(ed.atStartOfDay(ZoneId.systemDefault()).toInstant());
                } else { // DATETIME
                    LocalDate sd = startDatePicker.getDate();
                    LocalTime st = startTimePicker.getTime();
                    LocalDate ed = endDatePicker.getDate();
                    LocalTime en = endTimePicker.getTime();
                    if (sd != null && st != null) start = Date.from(LocalDateTime.of(sd, st).atZone(ZoneId.systemDefault()).toInstant());
                    if (ed != null && en != null) end = Date.from(LocalDateTime.of(ed, en).atZone(ZoneId.systemDefault()).toInstant());
                }

                // Validierung
                if (start == null || end == null) {
                    JOptionPane.showMessageDialog(this, GuiConstants.MSG_INVALID_TIME_RANGE, GuiConstants.MSG_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (start.after(end)) {
                    JOptionPane.showMessageDialog(this, GuiConstants.MSG_INVALID_TIME_RANGE, GuiConstants.MSG_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
                    return;
                }


                int idx = tabs.getSelectedIndex();
                if (idx == 0) includesTimeModel.addEntry(start, end, mode, true);
                else excludesTimeModel.addEntry(start, end, mode, true);
            }
        });

        enableAll.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) includesTimeModel.setAllEnabled(true);
            else excludesTimeModel.setAllEnabled(true);
        });
        disableAll.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) includesTimeModel.setAllEnabled(false);
            else excludesTimeModel.setAllEnabled(false);
        });

        return panel;
    }

    private void configureTimeTable(JTable table, TimeRangeTableModel model) {
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

    // Getter für Inhaltsfilter
    public java.util.Map<String, Boolean> getContentIncludesMap() {
        java.util.Map<String, Boolean> out = new LinkedHashMap<>();
        for (TextFiltersTableModel.Entry en : includesContentModel.getEntries()) out.put(en.pattern, en.enabled);
        return out;
    }

    public java.util.Map<String, Boolean> getContentIncludesCaseMap() {
        java.util.Map<String, Boolean> out = new LinkedHashMap<>();
        for (TextFiltersTableModel.Entry en : includesContentModel.getEntries()) out.put(en.pattern, en.caseSensitive);
        return out;
    }

    public java.util.Map<String, Boolean> getContentExcludesMap() {
        java.util.Map<String, Boolean> out = new LinkedHashMap<>();
        for (TextFiltersTableModel.Entry en : excludesContentModel.getEntries()) out.put(en.pattern, en.enabled);
        return out;
    }

    public java.util.Map<String, Boolean> getContentExcludesCaseMap() {
        java.util.Map<String, Boolean> out = new LinkedHashMap<>();
        for (TextFiltersTableModel.Entry en : excludesContentModel.getEntries()) out.put(en.pattern, en.caseSensitive);
        return out;
    }

    // Getter für Zeitfilter
    public java.util.List<TimeRangeTableModel.Entry> getTimeIncludes() {
        return new java.util.ArrayList<>(includesTimeModel.getEntries());
    }

    public java.util.List<TimeRangeTableModel.Entry> getTimeExcludes() {
        return new java.util.ArrayList<>(excludesTimeModel.getEntries());
    }

    private java.util.function.Supplier<java.util.Map<String, Boolean>> extensionsAllowGetter = LinkedHashMap::new;
    private java.util.function.Supplier<java.util.Map<String, Boolean>> extensionsDenyGetter = LinkedHashMap::new;

    public java.util.Map<String, Boolean> getExtensionsAllowMap() {
        return extensionsAllowGetter.get();
    }

    public java.util.Map<String, Boolean> getExtensionsDenyMap() {
        return extensionsDenyGetter.get();
    }

    public boolean isIncludeAllMode() {
        return includeAllMode;
    }

    // Getter für Inhaltsmodus
    public boolean isContentIncludeAllMode() {
        return contentIncludeAllMode;
    }
}
