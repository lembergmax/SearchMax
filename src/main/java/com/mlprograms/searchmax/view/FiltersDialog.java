package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.model.*;
import com.mlprograms.searchmax.view.timerange.TimeRangeInputDialog;
import com.mlprograms.searchmax.view.timerange.TimeRangeInputResult;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public final class FiltersDialog extends JDialog {

    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 24;
    private static final int TABLE_ROW_HEIGHT = 24;
    private static final int LAYOUT_GAP = 8;
    private static final int BUTTON_GAP = 6;

    // Filter Models
    private final TextFiltersTableModel filenameIncludesModel = new TextFiltersTableModel();
    private final TextFiltersTableModel filenameExcludesModel = new TextFiltersTableModel();
    private final TextFiltersTableModel contentIncludesModel = new TextFiltersTableModel();
    private final TextFiltersTableModel contentExcludesModel = new TextFiltersTableModel();
    private final TimeRangeTableModel timeIncludesModel = new TimeRangeTableModel();
    private final TimeRangeTableModel timeExcludesModel = new TimeRangeTableModel();

    // Initial State
    private final Map<String, Boolean> initialFilenameIncludes;
    private final Map<String, Boolean> initialFilenameExcludes;
    private final Map<String, Boolean> initialExtensionsAllow;
    private final Map<String, Boolean> initialExtensionsDeny;
    private final Map<String, Boolean> initialFilenameIncludesCase;
    private final Map<String, Boolean> initialFilenameExcludesCase;
    private final Map<String, Boolean> initialContentIncludes;
    private final Map<String, Boolean> initialContentExcludes;
    private final Map<String, Boolean> initialContentIncludesCase;
    private final Map<String, Boolean> initialContentExcludesCase;
    private final List<TimeRangeTableModel.Entry> initialTimeIncludes;
    private final List<TimeRangeTableModel.Entry> initialTimeExcludes;

    // Mode Flags
    private boolean filenameIncludeAllMode;
    @Getter
    private boolean contentIncludeAllMode;
    @Getter
    private boolean timeIncludeAllMode;
    // Getters for filter data
    @Getter
    private boolean isConfirmed = false;

    // Extension Getters
    private Supplier<Map<String, Boolean>> extensionsAllowSupplier = LinkedHashMap::new;
    private Supplier<Map<String, Boolean>> extensionsDenySupplier = LinkedHashMap::new;

    public FiltersDialog(final Frame owner,
                         final Map<String, Boolean> initialFilenameIncludes,
                         final Map<String, Boolean> initialFilenameExcludes,
                         final Map<String, Boolean> initialExtensionsAllow,
                         final Map<String, Boolean> initialExtensionsDeny,
                         final Map<String, Boolean> initialFilenameIncludesCase,
                         final Map<String, Boolean> initialFilenameExcludesCase,
                         final boolean initialFilenameIncludeAllMode,
                         final Map<String, Boolean> initialContentIncludes,
                         final Map<String, Boolean> initialContentExcludes,
                         final Map<String, Boolean> initialContentIncludesCase,
                         final Map<String, Boolean> initialContentExcludesCase,
                         final boolean initialContentIncludeAllMode,
                         final List<TimeRangeTableModel.Entry> initialTimeIncludes,
                         final List<TimeRangeTableModel.Entry> initialTimeExcludes,
                         final boolean initialTimeIncludeAllMode) {

        super(owner, GuiConstants.FILTERS_DIALOG_TITLE, true);

        // Initialize state with safe copies
        this.initialFilenameIncludes = createSafeCopy(initialFilenameIncludes);
        this.initialFilenameExcludes = createSafeCopy(initialFilenameExcludes);
        this.initialExtensionsAllow = createSafeCopy(initialExtensionsAllow);
        this.initialExtensionsDeny = createSafeCopy(initialExtensionsDeny);
        this.initialFilenameIncludesCase = createSafeCopy(initialFilenameIncludesCase);
        this.initialFilenameExcludesCase = createSafeCopy(initialFilenameExcludesCase);
        this.initialContentIncludes = createSafeCopy(initialContentIncludes);
        this.initialContentExcludes = createSafeCopy(initialContentExcludes);
        this.initialContentIncludesCase = createSafeCopy(initialContentIncludesCase);
        this.initialContentExcludesCase = createSafeCopy(initialContentExcludesCase);
        this.initialTimeIncludes = createSafeCopy(initialTimeIncludes);
        this.initialTimeExcludes = createSafeCopy(initialTimeExcludes);

        this.filenameIncludeAllMode = initialFilenameIncludeAllMode;
        this.contentIncludeAllMode = initialContentIncludeAllMode;
        this.timeIncludeAllMode = initialTimeIncludeAllMode;

        initializeFilterData();
        initializeUserInterface();
    }

    private <T> Map<String, T> createSafeCopy(final Map<String, T> original) {
        return original == null ? new LinkedHashMap<>() : new LinkedHashMap<>(original);
    }

    private <T> List<T> createSafeCopy(final List<T> original) {
        return original == null ? new ArrayList<>() : new ArrayList<>(original);
    }

    private void initializeFilterData() {
        // Populate filename filters from the filename initial maps (bugfix: previously used content maps here)
        populateTextFilters(initialFilenameIncludes, initialFilenameExcludes, filenameIncludesModel, filenameExcludesModel);
        // Populate content filters from the content initial maps
        populateTextFilters(initialContentIncludes, initialContentExcludes, contentIncludesModel, contentExcludesModel);
        populateTimeFilters(initialTimeIncludes, initialTimeExcludes);
        applyCaseSensitivityFlags();
    }

    private void populateTextFilters(final Map<String, Boolean> includesMap,
                                     final Map<String, Boolean> excludesMap,
                                     final TextFiltersTableModel includesModel,
                                     final TextFiltersTableModel excludesModel) {
        populateTextFilterModel(includesMap, includesModel);
        populateTextFilterModel(excludesMap, excludesModel);
    }

    private void populateTextFilterModel(final Map<String, Boolean> sourceMap, final TextFiltersTableModel targetModel) {
        if (sourceMap != null) {
            for (final Map.Entry<String, Boolean> entry : sourceMap.entrySet()) {
                final String key = entry.getKey();
                final boolean enabled = Boolean.TRUE.equals(entry.getValue());

                if (key != null) {
                    final String trimmedKey = key.trim();
                    if (!trimmedKey.isEmpty()) {
                        targetModel.addEntry(trimmedKey, enabled);
                    }
                }
            }
        }
    }

    private void populateTimeFilters(final List<TimeRangeTableModel.Entry> includesList,
                                     final List<TimeRangeTableModel.Entry> excludesList) {
        populateTimeFilterModel(includesList, timeIncludesModel);
        populateTimeFilterModel(excludesList, timeExcludesModel);
    }

    private void populateTimeFilterModel(final List<TimeRangeTableModel.Entry> sourceList,
                                         final TimeRangeTableModel targetModel) {
        if (sourceList != null) {
            for (final TimeRangeTableModel.Entry entry : sourceList) {
                if (entry != null) {
                    targetModel.addEntry(entry.start, entry.end, entry.mode, entry.enabled);
                }
            }
        }
    }

    private void applyCaseSensitivityFlags() {
        applyCaseSensitivityToModel(initialFilenameIncludesCase, filenameIncludesModel);
        applyCaseSensitivityToModel(initialFilenameExcludesCase, filenameExcludesModel);
        applyCaseSensitivityToModel(initialContentIncludesCase, contentIncludesModel);
        applyCaseSensitivityToModel(initialContentExcludesCase, contentExcludesModel);
    }

    private void applyCaseSensitivityToModel(final Map<String, Boolean> caseMap,
                                             final TextFiltersTableModel model) {
        for (final TextFiltersTableModel.Entry entry : model.getEntries()) {
            final Boolean isCaseSensitive = caseMap.get(entry.pattern);
            entry.caseSensitive = Boolean.TRUE.equals(isCaseSensitive);
        }
    }

    private void initializeUserInterface() {
        setLayout(new BorderLayout(LAYOUT_GAP, LAYOUT_GAP));

        final JPanel centerPanel = createCenterPanel();
        final JPanel bottomPanel = createBottomPanel();

        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        registerEscapeKeyHandler();
        pack();
        setLocationRelativeTo(getOwner());
    }

    private JPanel createCenterPanel() {
        final JPanel panel = new JPanel(new GridLayout(1, 4, LAYOUT_GAP, LAYOUT_GAP));
        panel.add(createFilenameFiltersPanel());
        panel.add(createContentFiltersPanel());
        panel.add(createExtensionsPanel());
        panel.add(createTimeFiltersPanel());
        return panel;
    }

    private JPanel createFilenameFiltersPanel() {
        return createTextFiltersPanel(
                filenameIncludesModel,
                filenameExcludesModel,
                GuiConstants.FILTERS_PANEL_TITLE,
                filenameIncludeAllMode,
                this::setFilenameIncludeAllMode,
                GuiConstants.INPUT_ADD_PATTERN
        );
    }

    private JPanel createContentFiltersPanel() {
        return createTextFiltersPanel(
                contentIncludesModel,
                contentExcludesModel,
                GuiConstants.CONTENT_PANEL_TITLE,
                contentIncludeAllMode,
                this::setContentIncludeAllMode,
                GuiConstants.INPUT_ADD_CONTENT_PATTERN
        );
    }

    private JPanel createTextFiltersPanel(final TextFiltersTableModel includesModel,
                                          final TextFiltersTableModel excludesModel,
                                          final String panelTitle,
                                          final boolean currentIncludeAllMode,
                                          final java.util.function.Consumer<Boolean> includeAllModeSetter,
                                          final String addDialogMessage) {

        final JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(panelTitle));

        final JTable includeTable = createTextTable(includesModel);
        final JTable excludeTable = createTextTable(excludesModel);

        final JTabbedPane tabbedPane = createFilterTabbedPane(includeTable, excludeTable);
        panel.add(tabbedPane, BorderLayout.CENTER);

        final JPanel modeSelectionPanel = createModeSelectionPanel(currentIncludeAllMode, includeAllModeSetter);
        panel.add(modeSelectionPanel, BorderLayout.NORTH);

        final JPanel buttonPanel = createTextFilterButtonPanel(tabbedPane, includesModel, excludesModel, addDialogMessage);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JTable createTextTable(final TextFiltersTableModel model) {
        final JTable table = new JTable(model);
        configureTextTable(table, model);
        return table;
    }

    private void configureTextTable(final JTable table, final TextFiltersTableModel model) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(TABLE_ROW_HEIGHT);

        final int removeColumnIndex = model.getRemoveColumnIndex();
        table.getColumnModel().getColumn(removeColumnIndex).setCellRenderer(new ButtonCellRenderer());
        table.getColumnModel().getColumn(removeColumnIndex).setCellEditor(new ButtonCellEditor(model::removeAt));
    }

    private JTabbedPane createFilterTabbedPane(final JTable includeTable, final JTable excludeTable) {
        final JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add(GuiConstants.TAB_ALLOW, new JScrollPane(includeTable));
        tabbedPane.add(GuiConstants.TAB_DENY, new JScrollPane(excludeTable));
        return tabbedPane;
    }

    private JPanel createModeSelectionPanel(final boolean currentIncludeAllMode,
                                            final java.util.function.Consumer<Boolean> includeAllModeSetter) {

        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        final JRadioButton anyRadioButton = new JRadioButton(GuiConstants.RADIO_ANY);
        final JRadioButton allRadioButton = new JRadioButton(GuiConstants.RADIO_ALL);

        final ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(anyRadioButton);
        buttonGroup.add(allRadioButton);

        anyRadioButton.setSelected(!currentIncludeAllMode);
        allRadioButton.setSelected(currentIncludeAllMode);

        anyRadioButton.addActionListener(actionEvent -> includeAllModeSetter.accept(false));
        allRadioButton.addActionListener(actionEvent -> includeAllModeSetter.accept(true));

        panel.add(anyRadioButton);
        panel.add(allRadioButton);

        return panel;
    }

    private JPanel createTextFilterButtonPanel(final JTabbedPane tabbedPane,
                                               final TextFiltersTableModel includesModel,
                                               final TextFiltersTableModel excludesModel,
                                               final String addDialogMessage) {

        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, BUTTON_GAP, BUTTON_GAP));

        final JButton addButton = createButton(GuiConstants.BUTTON_ADD,
                actionEvent -> handleAddTextFilter(tabbedPane, includesModel, excludesModel, addDialogMessage));

        final JButton enableAllButton = createButton(GuiConstants.BUTTON_ENABLE_ALL,
                actionEvent -> handleEnableAllTextFilters(tabbedPane, includesModel, excludesModel));

        final JButton disableAllButton = createButton(GuiConstants.BUTTON_DISABLE_ALL,
                actionEvent -> handleDisableAllTextFilters(tabbedPane, includesModel, excludesModel));

        panel.add(addButton);
        panel.add(enableAllButton);
        panel.add(disableAllButton);

        return panel;
    }

    private JButton createButton(final String text, final java.awt.event.ActionListener actionListener) {
        final JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.addActionListener(actionListener);
        return button;
    }

    private void handleAddTextFilter(final JTabbedPane tabbedPane,
                                     final TextFiltersTableModel includesModel,
                                     final TextFiltersTableModel excludesModel,
                                     final String dialogMessage) {

        final String input = JOptionPane.showInputDialog(this, dialogMessage,
                GuiConstants.INPUT_ADD_TITLE, JOptionPane.PLAIN_MESSAGE);

        if (input != null) {
            final String trimmedInput = input.trim();
            if (!trimmedInput.isEmpty()) {
                final int selectedTabIndex = tabbedPane.getSelectedIndex();
                if (selectedTabIndex == 0) {
                    includesModel.addEntry(trimmedInput, true);
                } else {
                    excludesModel.addEntry(trimmedInput, true);
                }
            }
        }
    }

    private void handleEnableAllTextFilters(final JTabbedPane tabbedPane,
                                            final TextFiltersTableModel includesModel,
                                            final TextFiltersTableModel excludesModel) {

        final int selectedTabIndex = tabbedPane.getSelectedIndex();
        if (selectedTabIndex == 0) {
            includesModel.setAllEnabled(true);
        } else {
            excludesModel.setAllEnabled(true);
        }
    }

    private void handleDisableAllTextFilters(final JTabbedPane tabbedPane,
                                             final TextFiltersTableModel includesModel,
                                             final TextFiltersTableModel excludesModel) {

        final int selectedTabIndex = tabbedPane.getSelectedIndex();
        if (selectedTabIndex == 0) {
            includesModel.setAllEnabled(false);
        } else {
            excludesModel.setAllEnabled(false);
        }
    }

    private JPanel createExtensionsPanel() {
        final JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(GuiConstants.EXT_PANEL_TITLE));

        final AllowExtensionsTableModel allowModel = new AllowExtensionsTableModel();
        final DenyExtensionsTableModel denyModel = new DenyExtensionsTableModel();

        populateExtensionModels(allowModel, denyModel);

        final JTable allowTable = createExtensionTable(allowModel);
        final JTable denyTable = createExtensionTable(denyModel);

        final JTabbedPane tabbedPane = createFilterTabbedPane(allowTable, denyTable);
        panel.add(tabbedPane, BorderLayout.CENTER);

        final JPanel buttonPanel = createExtensionButtonPanel(tabbedPane, allowModel, denyModel);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        initializeExtensionSuppliers(allowModel, denyModel);

        return panel;
    }

    private void populateExtensionModels(final AllowExtensionsTableModel allowModel,
                                         final DenyExtensionsTableModel denyModel) {

        populateExtensionModel(initialExtensionsAllow, allowModel);
        populateExtensionModel(initialExtensionsDeny, denyModel);
    }

    private void populateExtensionModel(final Map<String, Boolean> sourceMap,
                                        final ExtensionsTableModelBase targetModel) {

        if (sourceMap != null) {
            for (final Map.Entry<String, Boolean> entry : sourceMap.entrySet()) {
                final String extension = entry.getKey();
                final boolean enabled = Boolean.TRUE.equals(entry.getValue());

                if (extension != null) {
                    final String normalizedExtension = normalizeExtension(extension.trim());
                    if (!normalizedExtension.isEmpty()) {
                        targetModel.add(normalizedExtension, enabled);
                    }
                }
            }
        }
    }

    private String normalizeExtension(final String extension) {
        if (extension.isEmpty()) {
            return extension;
        }
        final String lowerCaseExtension = extension.toLowerCase();
        return lowerCaseExtension.startsWith(".") ? lowerCaseExtension : "." + lowerCaseExtension;
    }

    private JTable createExtensionTable(final ExtensionsTableModelBase model) {
        final JTable table = new JTable(model);
        configureExtensionTable(table, model);
        return table;
    }

    private void configureExtensionTable(final JTable table, final ExtensionsTableModelBase model) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(TABLE_ROW_HEIGHT);

        final int removeColumnIndex = model.getRemoveColumnIndex();
        table.getColumnModel().getColumn(removeColumnIndex).setCellRenderer(new ButtonCellRenderer());
        table.getColumnModel().getColumn(removeColumnIndex).setCellEditor(new ButtonCellEditor(model::removeAt));
    }

    private JPanel createExtensionButtonPanel(final JTabbedPane tabbedPane,
                                              final AllowExtensionsTableModel allowModel,
                                              final DenyExtensionsTableModel denyModel) {

        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, BUTTON_GAP, BUTTON_GAP));

        final JButton addButton = createButton(GuiConstants.BUTTON_ADD,
                actionEvent -> handleAddExtension(tabbedPane, allowModel, denyModel));

        final JButton enableAllButton = createButton(GuiConstants.BUTTON_ENABLE_ALL,
                actionEvent -> handleEnableAllExtensions(tabbedPane, allowModel, denyModel));

        final JButton disableAllButton = createButton(GuiConstants.BUTTON_DISABLE_ALL,
                actionEvent -> handleDisableAllExtensions(tabbedPane, allowModel, denyModel));

        panel.add(addButton);
        panel.add(enableAllButton);
        panel.add(disableAllButton);

        return panel;
    }

    private void handleAddExtension(final JTabbedPane tabbedPane,
                                    final AllowExtensionsTableModel allowModel,
                                    final DenyExtensionsTableModel denyModel) {

        final String input = JOptionPane.showInputDialog(this, GuiConstants.INPUT_NEW_EXTENSION,
                GuiConstants.INPUT_ADD_TITLE, JOptionPane.PLAIN_MESSAGE);

        if (input != null) {
            final String normalizedExtension = normalizeExtension(input.trim());
            if (!normalizedExtension.isEmpty()) {
                final int selectedTabIndex = tabbedPane.getSelectedIndex();

                if (selectedTabIndex == 0) {
                    addExtensionToModel(allowModel, normalizedExtension, GuiConstants.MSG_EXTENSION_EXISTS);
                } else {
                    addExtensionToModel(denyModel, normalizedExtension, GuiConstants.MSG_EXTENSION_EXISTS);
                }
            }
        }
    }

    private void addExtensionToModel(final ExtensionsTableModelBase model,
                                     final String extension,
                                     final String duplicateMessage) {

        if (model.contains(extension)) {
            JOptionPane.showMessageDialog(this, duplicateMessage,
                    GuiConstants.MSG_ERROR_TITLE, JOptionPane.WARNING_MESSAGE);
        } else {
            model.add(extension, true);
        }
    }

    private void handleEnableAllExtensions(final JTabbedPane tabbedPane,
                                           final AllowExtensionsTableModel allowModel,
                                           final DenyExtensionsTableModel denyModel) {

        final int selectedTabIndex = tabbedPane.getSelectedIndex();
        if (selectedTabIndex == 0) {
            allowModel.setAllEnabled(true);
        } else {
            denyModel.setAllEnabled(true);
        }
    }

    private void handleDisableAllExtensions(final JTabbedPane tabbedPane,
                                            final AllowExtensionsTableModel allowModel,
                                            final DenyExtensionsTableModel denyModel) {

        final int selectedTabIndex = tabbedPane.getSelectedIndex();
        if (selectedTabIndex == 0) {
            allowModel.setAllEnabled(false);
        } else {
            denyModel.setAllEnabled(false);
        }
    }

    private void initializeExtensionSuppliers(final AllowExtensionsTableModel allowModel,
                                              final DenyExtensionsTableModel denyModel) {

        this.extensionsAllowSupplier = () -> createExtensionMap(allowModel);
        this.extensionsDenySupplier = () -> createExtensionMap(denyModel);
    }

    private Map<String, Boolean> createExtensionMap(final ExtensionsTableModelBase model) {
        final Map<String, Boolean> map = new LinkedHashMap<>();
        for (final ExtensionsTableModelBase.Entry entry : model.getEntries()) {
            map.put(entry.ext, entry.enabled);
        }
        return map;
    }

    private JPanel createTimeFiltersPanel() {
        final JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(GuiConstants.TIME_PANEL_TITLE));

        final JTable includeTable = createTimeTable(timeIncludesModel);
        final JTable excludeTable = createTimeTable(timeExcludesModel);

        final JTabbedPane tabbedPane = createFilterTabbedPane(includeTable, excludeTable);
        panel.add(tabbedPane, BorderLayout.CENTER);

        final JPanel modeSelectionPanel = createModeSelectionPanel(timeIncludeAllMode, this::setTimeIncludeAllMode);
        panel.add(modeSelectionPanel, BorderLayout.NORTH);

        final JPanel buttonPanel = createTimeFilterButtonPanel(tabbedPane);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JTable createTimeTable(final TimeRangeTableModel model) {
        final JTable table = new JTable(model);
        configureTimeTable(table, model);
        return table;
    }

    private void configureTimeTable(final JTable table, final TimeRangeTableModel model) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(TABLE_ROW_HEIGHT);

        final int removeColumnIndex = model.getRemoveColumnIndex();
        table.getColumnModel().getColumn(removeColumnIndex).setCellRenderer(new ButtonCellRenderer());
        table.getColumnModel().getColumn(removeColumnIndex).setCellEditor(new ButtonCellEditor(model::removeAt));
    }

    private JPanel createTimeFilterButtonPanel(final JTabbedPane tabbedPane) {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, BUTTON_GAP, BUTTON_GAP));

        final JButton addButton = createButton(GuiConstants.BUTTON_ADD,
                actionEvent -> handleAddTimeFilter(tabbedPane));

        final JButton enableAllButton = createButton(GuiConstants.BUTTON_ENABLE_ALL,
                actionEvent -> handleEnableAllTimeFilters(tabbedPane));

        final JButton disableAllButton = createButton(GuiConstants.BUTTON_DISABLE_ALL,
                actionEvent -> handleDisableAllTimeFilters(tabbedPane));

        panel.add(addButton);
        panel.add(enableAllButton);
        panel.add(disableAllButton);

        return panel;
    }

    private void handleAddTimeFilter(final JTabbedPane tabbedPane) {
        final TimeRangeInputResult inputResult = showTimeRangeInputDialog();
        if (inputResult != null && inputResult.isValid()) {
            final int selectedTabIndex = tabbedPane.getSelectedIndex();
            if (selectedTabIndex == 0) {
                timeIncludesModel.addEntry(inputResult.startDate(), inputResult.endDate(),
                        inputResult.mode(), true);
            } else {
                timeExcludesModel.addEntry(inputResult.startDate(), inputResult.endDate(),
                        inputResult.mode(), true);
            }
        }
    }

    private TimeRangeInputResult showTimeRangeInputDialog() {
        final TimeRangeInputDialog dialog = new TimeRangeInputDialog((Frame) getOwner());
        dialog.setVisible(true);
        return dialog.getResult();
    }

    private void handleEnableAllTimeFilters(final JTabbedPane tabbedPane) {
        final int selectedTabIndex = tabbedPane.getSelectedIndex();
        if (selectedTabIndex == 0) {
            timeIncludesModel.setAllEnabled(true);
        } else {
            timeExcludesModel.setAllEnabled(true);
        }
    }

    private void handleDisableAllTimeFilters(final JTabbedPane tabbedPane) {
        final int selectedTabIndex = tabbedPane.getSelectedIndex();
        if (selectedTabIndex == 0) {
            timeIncludesModel.setAllEnabled(false);
        } else {
            timeExcludesModel.setAllEnabled(false);
        }
    }

    private JPanel createBottomPanel() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        final JButton confirmButton = new JButton(GuiConstants.BUTTON_OK);
        final JButton cancelButton = new JButton(GuiConstants.CANCEL_BUTTON);

        confirmButton.addActionListener(actionEvent -> {
            isConfirmed = true;
            setVisible(false);
        });

        cancelButton.addActionListener(actionEvent -> {
            isConfirmed = false;
            setVisible(false);
        });

        panel.add(confirmButton);
        panel.add(cancelButton);

        return panel;
    }

    private void registerEscapeKeyHandler() {
        getRootPane().registerKeyboardAction(
                actionEvent -> {
                    isConfirmed = false;
                    setVisible(false);
                },
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    public Map<String, Boolean> getIncludesMap() {
        return createFilterMap(filenameIncludesModel);
    }

    public Map<String, Boolean> getIncludesCaseMap() {
        return createCaseSensitivityMap(filenameIncludesModel);
    }

    public Map<String, Boolean> getExcludesMap() {
        return createFilterMap(filenameExcludesModel);
    }

    public Map<String, Boolean> getExcludesCaseMap() {
        return createCaseSensitivityMap(filenameExcludesModel);
    }

    public Map<String, Boolean> getContentIncludesMap() {
        return createFilterMap(contentIncludesModel);
    }

    public Map<String, Boolean> getContentIncludesCaseMap() {
        return createCaseSensitivityMap(contentIncludesModel);
    }

    public Map<String, Boolean> getContentExcludesMap() {
        return createFilterMap(contentExcludesModel);
    }

    public Map<String, Boolean> getContentExcludesCaseMap() {
        return createCaseSensitivityMap(contentExcludesModel);
    }

    public List<TimeRangeTableModel.Entry> getTimeIncludes() {
        return new ArrayList<>(timeIncludesModel.getEntries());
    }

    public List<TimeRangeTableModel.Entry> getTimeExcludes() {
        return new ArrayList<>(timeExcludesModel.getEntries());
    }

    public Map<String, Boolean> getExtensionsAllowMap() {
        return extensionsAllowSupplier.get();
    }

    public Map<String, Boolean> getExtensionsDenyMap() {
        return extensionsDenySupplier.get();
    }

    public boolean isIncludeAllMode() {
        return filenameIncludeAllMode;
    }

    private Map<String, Boolean> createFilterMap(final TextFiltersTableModel model) {
        final Map<String, Boolean> map = new LinkedHashMap<>();
        for (final TextFiltersTableModel.Entry entry : model.getEntries()) {
            map.put(entry.pattern, entry.enabled);
        }
        return map;
    }

    private Map<String, Boolean> createCaseSensitivityMap(final TextFiltersTableModel model) {
        final Map<String, Boolean> map = new LinkedHashMap<>();
        for (final TextFiltersTableModel.Entry entry : model.getEntries()) {
            map.put(entry.pattern, entry.caseSensitive);
        }
        return map;
    }

    private void setFilenameIncludeAllMode(final boolean includeAllMode) {
        this.filenameIncludeAllMode = includeAllMode;
    }

    private void setContentIncludeAllMode(final boolean includeAllMode) {
        this.contentIncludeAllMode = includeAllMode;
    }

    private void setTimeIncludeAllMode(final boolean includeAllMode) {
        this.timeIncludeAllMode = includeAllMode;
    }

}
