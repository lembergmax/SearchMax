package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.ExtractionMode;
import com.mlprograms.searchmax.controller.SearchController;
import com.mlprograms.searchmax.model.SearchModel;
import com.mlprograms.searchmax.model.TimeRangeTableModel;
import com.mlprograms.searchmax.view.logging.InMemoryLogAppender;
import com.mlprograms.searchmax.view.panel.BottomPanel;
import com.mlprograms.searchmax.view.panel.CenterPanel;
import com.mlprograms.searchmax.view.panel.DrivePanel;
import com.mlprograms.searchmax.view.panel.TopPanel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Getter
public final class SearchView extends JFrame {

    private record FilterSet(List<String> includes, Map<String, Boolean> includesCase, List<String> excludes,
                             Map<String, Boolean> excludesCase) {
    }

    private record SearchParameters(String folderPath, String searchQuery, List<String> selectedDrives,
                                    boolean caseSensitive, List<String> allowedExtensions,
                                    List<String> deniedExtensions, FilterSet filenameFilters,
                                    FilterSet contentFilters) {

        public List<String> getFilenameIncludes() {
            return filenameFilters.includes;
        }

        public Map<String, Boolean> getFilenameIncludeCaseMap() {
            return filenameFilters.includesCase;
        }

        public List<String> getFilenameExcludes() {
            return filenameFilters.excludes;
        }

        public Map<String, Boolean> getFilenameExcludeCaseMap() {
            return filenameFilters.excludesCase;
        }

        public List<String> getContentIncludes() {
            return contentFilters.includes;
        }

        public Map<String, Boolean> getContentIncludeCaseMap() {
            return contentFilters.includesCase;
        }

        public List<String> getContentExcludes() {
            return contentFilters.excludes;
        }

        public Map<String, Boolean> getContentExcludeCaseMap() {
            return contentFilters.excludesCase;
        }
    }

    private static final String SETTINGS_FILENAME = ".searchmax.properties";
    private static final String PROPERTY_START_FOLDER = "startFolder";
    private static final String PROPERTY_QUERY = "query";
    private static final String PROPERTY_CASE_SENSITIVE = "caseSensitive";
    private static final String PROPERTY_DRIVES = "drives";
    private static final String PROPERTY_INCLUDES = "includes";
    private static final String PROPERTY_EXCLUDES = "excludes";
    private static final String PROPERTY_INCLUDES_CASE = "includesCase";
    private static final String PROPERTY_EXCLUDES_CASE = "excludesCase";
    private static final String PROPERTY_EXTENSIONS_ALLOW = "extensionsAllow";
    private static final String PROPERTY_EXTENSIONS_DENY = "extensionsDeny";
    private static final String PROPERTY_CONTENT_INCLUDES = "contentIncludes";
    private static final String PROPERTY_CONTENT_EXCLUDES = "contentExcludes";
    private static final String PROPERTY_CONTENT_INCLUDES_CASE = "contentIncludesCase";
    private static final String PROPERTY_CONTENT_EXCLUDES_CASE = "contentExcludesCase";
    private static final String PROPERTY_TIME_INCLUDES = "timeIncludes";
    private static final String PROPERTY_TIME_EXCLUDES = "timeExcludes";
    private static final String PROPERTY_TIME_INCLUDES_MODE = "timeIncludesMode";
    private static final String PROPERTY_INCLUDES_MODE = "includesMode";
    private static final String PROPERTY_CONTENT_INCLUDES_MODE = "contentIncludesMode";
    private static final String PROPERTY_USE_ALL_CORES = "useAllCores";
    private static final String PROPERTY_EXTRACTION_MODE = "extractionMode";

    private final SearchController searchController;
    private final SearchModel searchModel;
    private final Path settingsFilePath = Paths.get(System.getProperty("user.home"), SETTINGS_FILENAME);
    private final DrivePanel drivePanel;
    private final TopPanel topPanel;
    private final CenterPanel centerPanel;
    private final BottomPanel bottomPanel;
    private final StatusUpdater statusUpdater;

    private LogViewer logViewer = null;
    private boolean isSearchRunning = false;

    // Filter-Zustände
    private final Map<String, Boolean> filenameIncludeFilters = new LinkedHashMap<>();
    private final Map<String, Boolean> filenameExcludeFilters = new LinkedHashMap<>();
    private final Map<String, Boolean> filenameIncludeCaseMap = new LinkedHashMap<>();
    private final Map<String, Boolean> filenameExcludeCaseMap = new LinkedHashMap<>();
    private final Map<String, Boolean> allowedFileExtensions = new LinkedHashMap<>();
    private final Map<String, Boolean> deniedFileExtensions = new LinkedHashMap<>();
    private final Map<String, Boolean> contentIncludeFilters = new LinkedHashMap<>();
    private final Map<String, Boolean> contentExcludeFilters = new LinkedHashMap<>();
    private final Map<String, Boolean> contentIncludeCaseMap = new LinkedHashMap<>();
    private final Map<String, Boolean> contentExcludeCaseMap = new LinkedHashMap<>();
    private final List<TimeRangeTableModel.Entry> timeIncludeRanges = new ArrayList<>();
    private final List<TimeRangeTableModel.Entry> timeExcludeRanges = new ArrayList<>();

    private boolean filenameIncludeAllMode = false;
    private boolean contentIncludeAllMode = false;
    private boolean timeIncludeAllMode = false;
    private boolean useAllCores = false;
    private ExtractionMode extractionMode = ExtractionMode.POI_THEN_TIKA;

    public SearchView(final SearchController searchController, final SearchModel searchModel) {
        super(GuiConstants.TITLE_SEARCHMAX);
        this.searchController = searchController;
        this.searchModel = searchModel;

        this.drivePanel = new DrivePanel(this);
        this.topPanel = new TopPanel(this);
        this.centerPanel = new CenterPanel(this);
        this.bottomPanel = new BottomPanel(this);
        this.statusUpdater = new StatusUpdater(this);

        initializeUserInterface();
        bindModelToView();
        loadApplicationSettings();
        initializeAutoSaveListeners();
        initializeForceClickSupport();
    }

    private void initializeUserInterface() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        final Container contentContainer = getContentPane();
        contentContainer.setLayout(new BorderLayout(6, 6));
        contentContainer.add(topPanel, BorderLayout.NORTH);
        contentContainer.add(centerPanel, BorderLayout.CENTER);
        contentContainer.add(bottomPanel, BorderLayout.SOUTH);

        topPanel.initializeEventListeners();
        updateButtonStates(false);
        updateFolderFieldState();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent windowEvent) {
                saveApplicationSettings();
            }
        });

        pack();
        setMinimumSize(new Dimension(700, 400));
        setSize(800, 600);
        setLocationRelativeTo(null);

        SwingUtilities.invokeLater(() -> topPanel.getSearchQueryTextField().requestFocusInWindow());
    }

    private void bindModelToView() {
        searchModel.addPropertyChangeListener(statusUpdater::onModelChange);
    }

    private void initializeAutoSaveListeners() {
        final DocumentListener autoSaveListener = new DocumentListener() {
            private void triggerAutoSave() {
                saveApplicationSettings();
            }

            @Override
            public void insertUpdate(final DocumentEvent documentEvent) {
                triggerAutoSave();
            }

            @Override
            public void removeUpdate(final DocumentEvent documentEvent) {
                triggerAutoSave();
            }

            @Override
            public void changedUpdate(final DocumentEvent documentEvent) {
                triggerAutoSave();
            }
        };

        topPanel.getSearchQueryTextField().getDocument().addDocumentListener(autoSaveListener);
        topPanel.getFolderPathTextField().getDocument().addDocumentListener(autoSaveListener);

        topPanel.getCaseSensitiveCheckbox().addActionListener(actionEvent -> saveApplicationSettings());
        bottomPanel.getPerformanceModeCheckbox().addActionListener(actionEvent -> {
            useAllCores = bottomPanel.getPerformanceModeCheckbox().isSelected();
            searchController.setUseAllCores(useAllCores);
            saveApplicationSettings();
        });
    }

    private void initializeForceClickSupport() {
        SwingUtilities.invokeLater(() -> {
            final Consumer<Component> installForceClick = new Consumer<>() {
                @Override
                public void accept(final Component component) {
                    if (component instanceof AbstractButton abstractButton) {
                        if (!Boolean.TRUE.equals(abstractButton.getClientProperty("ForceDoClickInstalled"))) {
                            abstractButton.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mousePressed(final MouseEvent mouseEvent) {
                                    if (mouseEvent.getButton() == MouseEvent.BUTTON1 && abstractButton.isEnabled()) {
                                        abstractButton.doClick();
                                    }
                                }
                            });
                            abstractButton.putClientProperty("ForceDoClickInstalled", Boolean.TRUE);
                        }
                    }

                    if (component instanceof Container container) {
                        for (final Component innerComponent : container.getComponents()) {
                            accept(innerComponent);
                        }
                    }
                }
            };

            installForceClick.accept(getContentPane());
        });
    }

    public void onBrowseFolder() {
        final JFileChooser directoryChooser = new JFileChooser();
        directoryChooser.setDialogTitle(GuiConstants.CHOOSER_SELECT_FOLDER);
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        directoryChooser.setAcceptAllFileFilterUsed(false);

        final String currentPath = topPanel.getFolderPathTextField().getText();
        if (currentPath != null && !currentPath.isBlank()) {
            final File currentDirectory = new File(currentPath);
            if (currentDirectory.exists() && currentDirectory.isDirectory()) {
                directoryChooser.setCurrentDirectory(currentDirectory);
            }
        }

        if (directoryChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            final File selectedDirectory = directoryChooser.getSelectedFile();
            if (selectedDirectory != null && selectedDirectory.isDirectory()) {
                topPanel.getFolderPathTextField().setText(selectedDirectory.getAbsolutePath());
                saveApplicationSettings();
            }
        }
    }

    public void onShowLogs() {
        try {
            if (logViewer != null) {
                logViewer.toFront();
                logViewer.requestFocus();
                return;
            }

            final LoggerContext loggerContext =
                    LoggerContext.getContext(false);
            final Configuration configuration = loggerContext.getConfiguration();
            final Appender appender = configuration.getAppender("InMemory");

            if (appender instanceof InMemoryLogAppender inMemoryLogAppender) {
                logViewer = new LogViewer(inMemoryLogAppender);
                logViewer.setVisible(true);
                logViewer.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(final java.awt.event.WindowEvent windowEvent) {
                        logViewer = null;
                    }
                });
            } else {
                JOptionPane.showMessageDialog(this, GuiConstants.MSG_INMEMORY_APPENDER_NOT_FOUND,
                        GuiConstants.MSG_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            }
        } catch (final Exception exception) {
            log.warn("Fehler beim Öffnen des Log-Viewers", exception);
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_ERROR_OPEN_LOGVIEWER_PREFIX + exception.getMessage(),
                    GuiConstants.MSG_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    public void onShowSettings() {
        try {
            final ExtractionSettingsDialog settingsDialog = new ExtractionSettingsDialog(this, extractionMode);
            settingsDialog.setVisible(true);
            final ExtractionMode selectedMode = settingsDialog.getSelectedExtractionMode();

            if (selectedMode != null) {
                extractionMode = selectedMode;
                try {
                    searchController.setExtractionMode(extractionMode);
                } catch (final Exception exception) {
                    log.warn("Fehler beim Setzen des ExtractionMode", exception);
                }
                saveApplicationSettings();
            }
        } catch (final Exception exception) {
            log.warn("Fehler beim Öffnen der Einstellungen", exception);
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_ERROR_OPEN_SETTINGS_PREFIX + exception.getMessage(),
                    GuiConstants.MSG_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    public void onSearch() {
        final SearchParameters searchParameters = collectSearchParameters();

        if (!validateSearchParameters(searchParameters)) {
            return;
        }

        searchController.startSearch(
                searchParameters.folderPath(),
                searchParameters.searchQuery(),
                searchParameters.selectedDrives(),
                searchParameters.caseSensitive(),
                searchParameters.allowedExtensions(),
                searchParameters.deniedExtensions(),
                searchParameters.getFilenameIncludes(),
                searchParameters.getFilenameIncludeCaseMap(),
                searchParameters.getFilenameExcludes(),
                searchParameters.getFilenameExcludeCaseMap(),
                filenameIncludeAllMode,
                searchParameters.getContentIncludes(),
                searchParameters.getContentIncludeCaseMap(),
                searchParameters.getContentExcludes(),
                searchParameters.getContentExcludeCaseMap(),
                contentIncludeAllMode,
                timeIncludeRanges,
                timeExcludeRanges,
                timeIncludeAllMode
        );
    }

    private SearchParameters collectSearchParameters() {
        final List<String> selectedDrives = drivePanel.getSelectedDrives();
        final String folderPath = topPanel.getFolderPathTextField().getText();
        final String searchQuery = topPanel.getSearchQueryTextField().getText();
        final boolean caseSensitive = topPanel.getCaseSensitiveCheckbox().isSelected();

        final List<String> allowedExtensions = extractActiveFilters(allowedFileExtensions);
        final List<String> deniedExtensions = extractActiveFilters(deniedFileExtensions);

        final FilterSet filenameFilters = extractFilterSet(filenameIncludeFilters, filenameIncludeCaseMap,
                filenameExcludeFilters, filenameExcludeCaseMap);
        final FilterSet contentFilters = extractFilterSet(contentIncludeFilters, contentIncludeCaseMap,
                contentExcludeFilters, contentExcludeCaseMap);

        return new SearchParameters(folderPath, searchQuery, selectedDrives, caseSensitive,
                allowedExtensions, deniedExtensions, filenameFilters, contentFilters);
    }

    private boolean validateSearchParameters(final SearchParameters parameters) {
        if (!parameters.selectedDrives().isEmpty()) {
            return validateDriveSearchParameters(parameters);
        } else {
            return validateFolderSearchParameters(parameters);
        }
    }

    private boolean validateDriveSearchParameters(final SearchParameters parameters) {
        if (!hasActiveSearchFilters(parameters)) {
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_ENTER_QUERY_OR_TYPE,
                    GuiConstants.MSG_MISSING_INPUT_TITLE, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private boolean validateFolderSearchParameters(final SearchParameters parameters) {
        if (parameters.folderPath() == null || parameters.folderPath().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_PLEASE_START_FOLDER,
                    GuiConstants.MSG_MISSING_INPUT_TITLE, JOptionPane.WARNING_MESSAGE);
            return false;
        }

        if (!hasActiveSearchFilters(parameters)) {
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_PLEASE_QUERY_OR_TYPE_OR_FILTER,
                    GuiConstants.MSG_MISSING_INPUT_TITLE, JOptionPane.WARNING_MESSAGE);
            return false;
        }

        return true;
    }

    private boolean hasActiveSearchFilters(final SearchParameters parameters) {
        return !(parameters.searchQuery() == null || parameters.searchQuery().trim().isEmpty())
                || !parameters.allowedExtensions().isEmpty()
                || !parameters.getFilenameIncludes().isEmpty()
                || !parameters.getContentIncludes().isEmpty()
                || !parameters.getContentExcludes().isEmpty()
                || hasActiveTimeFilter(timeIncludeRanges)
                || hasActiveTimeFilter(timeExcludeRanges);
    }

    private List<String> extractActiveFilters(final Map<String, Boolean> filterMap) {
        final List<String> activeFilters = new ArrayList<>();
        for (final Map.Entry<String, Boolean> entry : filterMap.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                activeFilters.add(entry.getKey());
            }
        }
        return activeFilters;
    }

    private FilterSet extractFilterSet(final Map<String, Boolean> includeMap, final Map<String, Boolean> includeCaseMap,
                                       final Map<String, Boolean> excludeMap, final Map<String, Boolean> excludeCaseMap) {
        final List<String> includes = new ArrayList<>();
        final Map<String, Boolean> includesCase = new LinkedHashMap<>();
        final List<String> excludes = new ArrayList<>();
        final Map<String, Boolean> excludesCase = new LinkedHashMap<>();

        extractActiveFiltersWithCase(includeMap, includeCaseMap, includes, includesCase);
        extractActiveFiltersWithCase(excludeMap, excludeCaseMap, excludes, excludesCase);

        return new FilterSet(includes, includesCase, excludes, excludesCase);
    }

    private void extractActiveFiltersWithCase(final Map<String, Boolean> sourceMap, final Map<String, Boolean> sourceCaseMap,
                                              final List<String> targetList, final Map<String, Boolean> targetCaseMap) {
        for (final Map.Entry<String, Boolean> entry : sourceMap.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                final String filterKey = entry.getKey();
                targetList.add(filterKey);
                targetCaseMap.put(filterKey, Boolean.TRUE.equals(sourceCaseMap.get(filterKey)));
            }
        }
    }

    public void onCancelSearch() {
        if (searchController.cancelSearch()) {
            updateButtonStates(false);
        } else {
            updateFolderFieldState();
        }
    }

    public void onManageFilters() {
        final FiltersDialog filtersDialog = new FiltersDialog(
                this, filenameIncludeFilters, filenameExcludeFilters, allowedFileExtensions, deniedFileExtensions,
                filenameIncludeCaseMap, filenameExcludeCaseMap, filenameIncludeAllMode,
                contentIncludeFilters, contentExcludeFilters, contentIncludeCaseMap, contentExcludeCaseMap, contentIncludeAllMode,
                timeIncludeRanges, timeExcludeRanges, timeIncludeAllMode
        );

        filtersDialog.setVisible(true);

        if (filtersDialog.isConfirmed()) {
            updateFiltersFromDialog(filtersDialog);
            saveApplicationSettings();
        }
    }

    private void updateFiltersFromDialog(final FiltersDialog filtersDialog) {
        filenameIncludeFilters.clear();
        filenameIncludeFilters.putAll(filtersDialog.getIncludesMap());
        filenameExcludeFilters.clear();
        filenameExcludeFilters.putAll(filtersDialog.getExcludesMap());
        filenameIncludeCaseMap.clear();
        filenameIncludeCaseMap.putAll(filtersDialog.getIncludesCaseMap());
        filenameExcludeCaseMap.clear();
        filenameExcludeCaseMap.putAll(filtersDialog.getExcludesCaseMap());

        contentIncludeFilters.clear();
        contentIncludeFilters.putAll(filtersDialog.getContentIncludesMap());
        contentExcludeFilters.clear();
        contentExcludeFilters.putAll(filtersDialog.getContentExcludesMap());
        contentIncludeCaseMap.clear();
        contentIncludeCaseMap.putAll(filtersDialog.getContentIncludesCaseMap());
        contentExcludeCaseMap.clear();
        contentExcludeCaseMap.putAll(filtersDialog.getContentExcludesCaseMap());

        allowedFileExtensions.clear();
        allowedFileExtensions.putAll(filtersDialog.getExtensionsAllowMap());
        deniedFileExtensions.clear();
        deniedFileExtensions.putAll(filtersDialog.getExtensionsDenyMap());

        timeIncludeRanges.clear();
        timeIncludeRanges.addAll(filtersDialog.getTimeIncludes());
        timeExcludeRanges.clear();
        timeExcludeRanges.addAll(filtersDialog.getTimeExcludes());
        timeIncludeAllMode = filtersDialog.isTimeIncludeAllMode();

        filenameIncludeAllMode = filtersDialog.isIncludeAllMode();
        contentIncludeAllMode = filtersDialog.isContentIncludeAllMode();
    }

    public void updateFolderFieldState() {
        if (isSearchRunning) {
            topPanel.getFolderPathTextField().setEnabled(false);
            topPanel.getBrowseFolderButton().setEnabled(false);
            drivePanel.setDrivesEnabled(false);
            return;
        }

        final boolean anyDriveSelected = !drivePanel.getSelectedDrives().isEmpty();
        topPanel.getFolderPathTextField().setEnabled(!anyDriveSelected);
        topPanel.getBrowseFolderButton().setEnabled(!anyDriveSelected);
        drivePanel.setDrivesEnabled(true);
    }

    void updateButtonStates(final boolean isRunning) {
        this.isSearchRunning = isRunning;
        topPanel.getSearchButton().setEnabled(!isRunning);
        topPanel.getCancelSearchButton().setEnabled(isRunning);
        topPanel.getSearchQueryTextField().setEnabled(!isRunning);
        topPanel.getCaseSensitiveCheckbox().setEnabled(!isRunning);
        topPanel.getManageFiltersButton().setEnabled(!isRunning);
        bottomPanel.getPerformanceModeCheckbox().setEnabled(!isRunning);
        bottomPanel.getShowSettingsButton().setEnabled(!isRunning);
        drivePanel.setDrivesEnabled(!isRunning);
        updateFolderFieldState();
    }

    public void saveSettings() {
        saveApplicationSettings();
    }

    private void saveApplicationSettings() {
        try {
            final Properties settingsProperties = new Properties();

            settingsProperties.setProperty(PROPERTY_START_FOLDER,
                    getSafeText(topPanel.getFolderPathTextField().getText()));
            settingsProperties.setProperty(PROPERTY_QUERY,
                    getSafeText(topPanel.getSearchQueryTextField().getText()));
            settingsProperties.setProperty(PROPERTY_CASE_SENSITIVE,
                    Boolean.toString(topPanel.getCaseSensitiveCheckbox().isSelected()));
            settingsProperties.setProperty(PROPERTY_DRIVES,
                    String.join(",", drivePanel.getSelectedDrives()));

            saveFilterMapsToProperties(settingsProperties);
            saveTimeFiltersToProperties(settingsProperties);
            saveModeSettingsToProperties(settingsProperties);

            final File settingsFile = settingsFilePath.toFile();
            try (final FileOutputStream outputStream = new FileOutputStream(settingsFile)) {
                settingsProperties.store(outputStream, "SearchMax Filter Settings");
            }
        } catch (final Exception exception) {
            log.warn("Fehler beim Speichern der Filtereinstellungen", exception);
        }
    }

    private String getSafeText(final String text) {
        return text == null ? "" : text;
    }

    private void saveFilterMapsToProperties(final Properties properties) {
        properties.setProperty(PROPERTY_INCLUDES, convertMapToString(filenameIncludeFilters));
        properties.setProperty(PROPERTY_EXCLUDES, convertMapToString(filenameExcludeFilters));
        properties.setProperty(PROPERTY_INCLUDES_CASE, convertMapToString(filenameIncludeCaseMap));
        properties.setProperty(PROPERTY_EXCLUDES_CASE, convertMapToString(filenameExcludeCaseMap));
        properties.setProperty(PROPERTY_EXTENSIONS_ALLOW, convertMapToString(allowedFileExtensions));
        properties.setProperty(PROPERTY_EXTENSIONS_DENY, convertMapToString(deniedFileExtensions));
        properties.setProperty(PROPERTY_CONTENT_INCLUDES, convertMapToString(contentIncludeFilters));
        properties.setProperty(PROPERTY_CONTENT_EXCLUDES, convertMapToString(contentExcludeFilters));
        properties.setProperty(PROPERTY_CONTENT_INCLUDES_CASE, convertMapToString(contentIncludeCaseMap));
        properties.setProperty(PROPERTY_CONTENT_EXCLUDES_CASE, convertMapToString(contentExcludeCaseMap));
    }

    private void saveTimeFiltersToProperties(final Properties properties) {
        properties.setProperty(PROPERTY_TIME_INCLUDES, convertTimeListToString(timeIncludeRanges));
        properties.setProperty(PROPERTY_TIME_EXCLUDES, convertTimeListToString(timeExcludeRanges));
    }

    private void saveModeSettingsToProperties(final Properties properties) {
        properties.setProperty(PROPERTY_TIME_INCLUDES_MODE, timeIncludeAllMode ? "ALL" : "ANY");
        properties.setProperty(PROPERTY_INCLUDES_MODE, filenameIncludeAllMode ? "ALL" : "ANY");
        properties.setProperty(PROPERTY_CONTENT_INCLUDES_MODE, contentIncludeAllMode ? "ALL" : "ANY");
        properties.setProperty(PROPERTY_USE_ALL_CORES, Boolean.toString(useAllCores));
        properties.setProperty(PROPERTY_EXTRACTION_MODE,
                extractionMode == null ? "POI_THEN_TIKA" : extractionMode.name());
    }

    private String convertMapToString(final Map<String, Boolean> map) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final Map.Entry<String, Boolean> entry : map.entrySet()) {
            stringBuilder.append(entry.getKey())
                    .append("=")
                    .append(entry.getValue())
                    .append(";");
        }
        return stringBuilder.toString();
    }

    private String convertTimeListToString(final List<TimeRangeTableModel.Entry> entries) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final TimeRangeTableModel.Entry entry : entries) {
            final long startMillis = entry.start == null ? -1L : entry.start.getTime();
            final long endMillis = entry.end == null ? -1L : entry.end.getTime();
            final String mode = entry.mode == null ?
                    TimeRangeTableModel.Mode.DATETIME.name() : entry.mode.name();

            stringBuilder.append(entry.enabled)
                    .append("|")
                    .append(startMillis)
                    .append("|")
                    .append(endMillis)
                    .append("|")
                    .append(mode)
                    .append(";");
        }
        return stringBuilder.toString();
    }

    private void loadApplicationSettings() {
        try {
            final File settingsFile = settingsFilePath.toFile();
            if (!settingsFile.exists()) {
                return;
            }

            final Properties settingsProperties = new Properties();
            try (final FileInputStream inputStream = new FileInputStream(settingsFile)) {
                settingsProperties.load(inputStream);
            }

            loadBasicSettings(settingsProperties);
            loadFilterSettings(settingsProperties);
            loadTimeFilterSettings(settingsProperties);
            loadModeSettings(settingsProperties);
            loadPerformanceSettings(settingsProperties);

        } catch (final Exception exception) {
            log.warn("Fehler beim Laden der Filtereinstellungen", exception);
        }
    }

    private void loadBasicSettings(final Properties properties) {
        final String startFolder = properties.getProperty(PROPERTY_START_FOLDER, "").trim();
        if (!startFolder.isEmpty()) {
            topPanel.getFolderPathTextField().setText(startFolder);
        }

        final String query = properties.getProperty(PROPERTY_QUERY, "").trim();
        if (!query.isEmpty()) {
            topPanel.getSearchQueryTextField().setText(query);
        }

        final String caseSensitive = properties.getProperty(PROPERTY_CASE_SENSITIVE, "false").trim();
        topPanel.getCaseSensitiveCheckbox().setSelected("true".equalsIgnoreCase(caseSensitive));

        final String drives = properties.getProperty(PROPERTY_DRIVES, "").trim();
        if (!drives.isEmpty()) {
            final String[] driveParts = drives.split(",");
            final List<String> selectedDrives = new ArrayList<>();
            for (final String drivePart : driveParts) {
                final String trimmedDrive = drivePart.trim();
                if (!trimmedDrive.isEmpty()) {
                    selectedDrives.add(trimmedDrive);
                }
            }
            drivePanel.setSelectedDrives(selectedDrives);
        }
    }

    private void loadFilterSettings(final Properties properties) {
        loadFilterMap(properties.getProperty(PROPERTY_INCLUDES, "").trim(), filenameIncludeFilters);
        loadFilterMap(properties.getProperty(PROPERTY_EXCLUDES, "").trim(), filenameExcludeFilters);
        loadFilterMap(properties.getProperty(PROPERTY_INCLUDES_CASE, "").trim(), filenameIncludeCaseMap);
        loadFilterMap(properties.getProperty(PROPERTY_EXCLUDES_CASE, "").trim(), filenameExcludeCaseMap);
        loadFilterMap(properties.getProperty(PROPERTY_EXTENSIONS_ALLOW, "").trim(), allowedFileExtensions);
        loadFilterMap(properties.getProperty(PROPERTY_EXTENSIONS_DENY, "").trim(), deniedFileExtensions);
        loadFilterMap(properties.getProperty(PROPERTY_CONTENT_INCLUDES, "").trim(), contentIncludeFilters);
        loadFilterMap(properties.getProperty(PROPERTY_CONTENT_EXCLUDES, "").trim(), contentExcludeFilters);
        loadFilterMap(properties.getProperty(PROPERTY_CONTENT_INCLUDES_CASE, "").trim(), contentIncludeCaseMap);
        loadFilterMap(properties.getProperty(PROPERTY_CONTENT_EXCLUDES_CASE, "").trim(), contentExcludeCaseMap);
    }

    private void loadTimeFilterSettings(final Properties properties) {
        loadTimeFilterList(properties.getProperty(PROPERTY_TIME_INCLUDES, "").trim(), timeIncludeRanges);
        loadTimeFilterList(properties.getProperty(PROPERTY_TIME_EXCLUDES, "").trim(), timeExcludeRanges);
    }

    private void loadModeSettings(final Properties properties) {
        final String timeMode = properties.getProperty(PROPERTY_TIME_INCLUDES_MODE, "ANY").trim();
        timeIncludeAllMode = "ALL".equalsIgnoreCase(timeMode);

        final String includesMode = properties.getProperty(PROPERTY_INCLUDES_MODE, "ANY").trim();
        filenameIncludeAllMode = "ALL".equalsIgnoreCase(includesMode);

        final String contentMode = properties.getProperty(PROPERTY_CONTENT_INCLUDES_MODE, "ANY").trim();
        contentIncludeAllMode = "ALL".equalsIgnoreCase(contentMode);
    }

    private void loadPerformanceSettings(final Properties properties) {
        final String useAllCoresValue = properties.getProperty(PROPERTY_USE_ALL_CORES, "false").trim();
        useAllCores = "true".equalsIgnoreCase(useAllCoresValue);
        bottomPanel.getPerformanceModeCheckbox().setSelected(useAllCores);
        searchController.setUseAllCores(useAllCores);

        final String extractionModeString = properties.getProperty(PROPERTY_EXTRACTION_MODE, "POI_THEN_TIKA").trim();
        extractionMode = ExtractionMode.valueOf(extractionModeString);
        searchController.setExtractionMode(extractionMode);
    }

    private void loadFilterMap(final String filterString, final Map<String, Boolean> targetMap) {
        if (!filterString.isEmpty()) {
            if (filterString.contains("=") || filterString.contains(";")) {
                final Map<String, Boolean> parsedMap = parseStringToBooleanMap(filterString);
                targetMap.clear();
                targetMap.putAll(parsedMap);
            } else {
                final String[] filterParts = filterString.split(",");
                for (final String filterPart : filterParts) {
                    final String trimmedFilter = filterPart.trim();
                    if (!trimmedFilter.isEmpty()) {
                        targetMap.put(trimmedFilter, true);
                    }
                }
            }
        }
    }

    private Map<String, Boolean> parseStringToBooleanMap(final String inputString) {
        final Map<String, Boolean> resultMap = new LinkedHashMap<>();
        final String[] keyValuePairs = inputString.split(";");

        for (final String keyValuePair : keyValuePairs) {
            final String trimmedPair = keyValuePair.trim();
            if (trimmedPair.isEmpty()) {
                continue;
            }

            final int separatorIndex = trimmedPair.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            final String key = trimmedPair.substring(0, separatorIndex);
            final String value = trimmedPair.substring(separatorIndex + 1);
            resultMap.put(key, Boolean.TRUE.toString().equalsIgnoreCase(value) || "true".equalsIgnoreCase(value));
        }

        return resultMap;
    }

    private void loadTimeFilterList(final String timeString, final List<TimeRangeTableModel.Entry> targetList) {
        targetList.clear();
        if (!timeString.isEmpty()) {
            final String[] timeEntries = timeString.split(";");
            for (final String timeEntry : timeEntries) {
                final String trimmedEntry = timeEntry.trim();
                if (trimmedEntry.isEmpty()) {
                    continue;
                }

                final String[] entryValues = trimmedEntry.split("\\|");
                if (entryValues.length >= 3) {
                    try {
                        final boolean enabled = Boolean.parseBoolean(entryValues[0]);
                        final long startMillis = Long.parseLong(entryValues[1]);
                        final long endMillis = Long.parseLong(entryValues[2]);
                        TimeRangeTableModel.Mode mode =
                                TimeRangeTableModel.Mode.DATETIME;

                        if (entryValues.length >= 4 && entryValues[3] != null && !entryValues[3].isEmpty()) {
                            try {
                                mode = TimeRangeTableModel.Mode.valueOf(entryValues[3]);
                            } catch (final IllegalArgumentException illegalArgumentException) {
                                // Behalte Standardmodus
                            }
                        }

                        final Date startDate = startMillis < 0 ? null : new Date(startMillis);
                        final Date endDate = endMillis < 0 ? null : new Date(endMillis);
                        targetList.add(new TimeRangeTableModel.Entry(
                                enabled, startDate, endDate, mode));
                    } catch (final NumberFormatException numberFormatException) {
                        log.warn("Fehler beim Parsen des Zeitfilter-Eintrags: " + trimmedEntry, numberFormatException);
                    }
                }
            }
        }
    }

    private boolean hasActiveTimeFilter(final List<TimeRangeTableModel.Entry> timeEntries) {
        if (timeEntries == null || timeEntries.isEmpty()) {
            return false;
        }

        for (final TimeRangeTableModel.Entry entry : timeEntries) {
            if (entry != null && entry.enabled) {
                return true;
            }
        }
        return false;
    }

}