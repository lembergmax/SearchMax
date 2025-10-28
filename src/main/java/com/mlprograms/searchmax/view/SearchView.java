package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.controller.SearchController;
import com.mlprograms.searchmax.model.SearchModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
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

/**
 * Die Hauptansicht für die SearchMax-Anwendung.
 * Verwaltet die UI-Komponenten, das Laden und Speichern der Einstellungen sowie die Interaktion mit dem Controller und Model.
 */
@Slf4j
@Getter
public final class SearchView extends JFrame {

    private final SearchController controller;
    private final SearchModel model;

    /**
     * Pfad zur Datei, in der die Einstellungen gespeichert werden.
     */
    private final Path settingsFile = Paths.get(System.getProperty("user.home"), ".searchmax.properties");

    /**
     * Panel zur Auswahl der Laufwerke.
     */
    private final DrivePanel drivePanel;
    /**
     * Panel für die oberen Bedienelemente (Suchfeld, Buttons).
     */
    private final TopPanel topPanel;
    /**
     * Panel für die Anzeige der Suchergebnisse.
     */
    private final CenterPanel centerPanel;
    /**
     * Panel für die unteren Bedienelemente (Status, Fortschritt).
     */
    private final BottomPanel bottomPanel;
    /**
     * Hilfsklasse zur Aktualisierung des Status in der UI.
     */
    private final StatusUpdater statusUpdater;

    /**
     * Log-Viewer Instanz (wird beim Öffnen gesetzt, null wenn geschlossen)
     */
    private LogViewer logViewer = null;

    /**
     * Gibt an, ob aktuell eine Suche läuft.
     */
    private boolean running = false;

    /**
     * Bekannte "Enthält"-Filter.
     */
    private final Map<String, Boolean> knownIncludes = new LinkedHashMap<>();
    /**
     * Bekannte "Enthält nicht"-Filter.
     */
    private final Map<String, Boolean> knownExcludes = new LinkedHashMap<>();
    /**
     * Groß-/Kleinschreibung für "Enthält"-Filter.
     */
    private final Map<String, Boolean> knownIncludesCase = new LinkedHashMap<>();
    /**
     * Groß-/Kleinschreibung für "Enthält nicht"-Filter.
     */
    private final Map<String, Boolean> knownExcludesCase = new LinkedHashMap<>();
    /**
     * Erlaubte Dateiendungen.
     */
    private final Map<String, Boolean> knownExtensionsAllow = new LinkedHashMap<>();
    /**
     * Nicht erlaubte Dateiendungen.
     */
    private final Map<String, Boolean> knownExtensionsDeny = new LinkedHashMap<>();

    // Content (Dateiinhalt) filter state
    private final Map<String, Boolean> knownContentIncludes = new LinkedHashMap<>();
    private final Map<String, Boolean> knownContentExcludes = new LinkedHashMap<>();
    private final Map<String, Boolean> knownContentIncludesCase = new LinkedHashMap<>();
    private final Map<String, Boolean> knownContentExcludesCase = new LinkedHashMap<>();
    private boolean knownIncludesAllMode = false;
    private boolean knownContentIncludesAllMode = false;

    // Zeitfilter: bekannte Einträge (werden als TimeRangeTableModel.Entry gespeichert)
    private java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> knownTimeIncludes = new java.util.ArrayList<>();
    private java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> knownTimeExcludes = new java.util.ArrayList<>();
    private boolean knownTimeIncludesAllMode = false;
    private boolean useAllCores = false;
    private com.mlprograms.searchmax.ExtractionMode extractionMode = com.mlprograms.searchmax.ExtractionMode.POI_THEN_TIKA;

    /**
     * Konstruktor für die SearchView.
     * Initialisiert die UI-Komponenten, lädt Einstellungen und bindet das Model.
     *
     * @param controller Der zugehörige Controller
     * @param model      Das zugehörige Model
     */
    public SearchView(final SearchController controller, final SearchModel model) {
        super(GuiConstants.TITLE_SEARCHMAX);
        this.controller = controller;
        this.model = model;

        this.drivePanel = new DrivePanel(this);
        this.topPanel = new TopPanel(this);
        this.centerPanel = new CenterPanel(this);
        this.bottomPanel = new BottomPanel(this);
        this.statusUpdater = new StatusUpdater(this);

        initUI();
        bindModel();
        loadSettings();

        bottomPanel.getPerformanceModeCheck().addActionListener(e -> {
            useAllCores = bottomPanel.getPerformanceModeCheck().isSelected();
            controller.setUseAllCores(useAllCores);
            saveExtensionsToSettings();
        });

        // TODO: irgendwie anders lösen
        SwingUtilities.invokeLater(() -> {
            Consumer<Component> attachDoClick = new Consumer<>() {
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
                        for (Component innerComponent : container.getComponents()) {
                            accept(innerComponent);
                        }
                    }
                }
            };

            attachDoClick.accept(getContentPane());
        });
    }

    /**
     * Initialisiert die Benutzeroberfläche und deren Layout.
     */
    private void initUI() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        final Container container = getContentPane();
        container.setLayout(new BorderLayout(6, 6));
        container.add(topPanel, BorderLayout.NORTH);
        container.add(centerPanel, BorderLayout.CENTER);
        container.add(bottomPanel, BorderLayout.SOUTH);

        topPanel.addListeners();
        updateButtons(false);
        updateFolderFieldState();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent windowEvent) {
                saveExtensionsToSettings();
            }
        });

        pack();
        setMinimumSize(new Dimension(700, 400));
        setSize(800, 600);
        setLocationRelativeTo(null);

        SwingUtilities.invokeLater(() -> topPanel.getQueryField().requestFocusInWindow());
    }

    /**
     * Öffnet einen Dialog zum Auswählen eines Ordners.
     */
    void onBrowse() {
        final JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setDialogTitle(GuiConstants.CHOOSER_SELECT_FOLDER);
        jFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        jFileChooser.setAcceptAllFileFilterUsed(false);

        final String path = topPanel.getFolderField().getText();
        if (path != null && !path.isBlank()) {
            final File directory = new File(path);
            if (directory.exists() && directory.isDirectory()) {
                jFileChooser.setCurrentDirectory(directory);
            }
        }

        if (jFileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            final File selected = jFileChooser.getSelectedFile();
            if (selected != null && selected.isDirectory()) {
                topPanel.getFolderField().setText(selected.getAbsolutePath());
            }
        }
    }

    /**
     * Zeigt das Log-Viewer-Fenster an. Nutzt den InMemoryLogAppender, welcher in log4j2.xml konfiguriert sein muss.
     */
    public void onShowLogs() {
        try {
            if (logViewer != null) {
                logViewer.toFront();
                logViewer.requestFocus();
                return;
            }

            org.apache.logging.log4j.core.LoggerContext ctx = org.apache.logging.log4j.core.LoggerContext.getContext(false);
            org.apache.logging.log4j.core.config.Configuration cfg = ctx.getConfiguration();
            org.apache.logging.log4j.core.Appender app = cfg.getAppender("InMemory");
            if (app instanceof com.mlprograms.searchmax.view.logging.InMemoryLogAppender inMemory) {
                logViewer = new LogViewer(inMemory);
                logViewer.setVisible(true);
                logViewer.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        logViewer = null;
                    }
                });
            } else {
                JOptionPane.showMessageDialog(this, GuiConstants.MSG_INMEMORY_APPENDER_NOT_FOUND, GuiConstants.MSG_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            log.warn("Fehler beim Öffnen des Log-Viewers", ex);
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_ERROR_OPEN_LOGVIEWER_PREFIX + ex.getMessage(), GuiConstants.MSG_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    public void onShowSettings() {
        try {
            ExtractionSettingsDialog dlg = new ExtractionSettingsDialog(this, extractionMode);
            dlg.setVisible(true);
            com.mlprograms.searchmax.ExtractionMode sel = dlg.getSelected();
            if (sel != null) {
                extractionMode = sel;
                // propagate to service
                try {
                    controller.setExtractionMode(extractionMode);
                } catch (Exception e) {
                    log.warn("Fehler beim Setzen des ExtractionMode", e);
                }
            }
        } catch (Exception ex) {
            log.warn("Fehler beim Öffnen der Settings", ex);
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_ERROR_OPEN_SETTINGS_PREFIX + ex.getMessage(), GuiConstants.MSG_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Startet die Suche mit den aktuellen Filter- und Sucheinstellungen.
     */
    void onSearch() {
        final List<String> selectedDrives = drivePanel.getSelectedDrives();
        final String folder = topPanel.getFolderField().getText();
        final String query = topPanel.getQueryField().getText();
        final boolean caseSensitive = topPanel.getCaseSensitiveCheck().isSelected();

        final List<String> extensionsAllow = new ArrayList<>();
        final List<String> extensionsDeny = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : knownExtensionsAllow.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                extensionsAllow.add(entry.getKey());
            }
        }
        for (Map.Entry<String, Boolean> entry : knownExtensionsDeny.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                extensionsDeny.add(entry.getKey());
            }
        }

        final List<String> includes = new ArrayList<>();
        final Map<String, Boolean> includesCase = new LinkedHashMap<>();
        final List<String> excludes = new ArrayList<>();
        final Map<String, Boolean> excludesCase = new LinkedHashMap<>();

        filterActiveEntries(includes, includesCase, knownIncludes, knownIncludesCase);
        filterActiveEntries(excludes, excludesCase, knownExcludes, knownExcludesCase);

        // Content filters
        final List<String> contentIncludes = new ArrayList<>();
        final Map<String, Boolean> contentIncludesCase = new LinkedHashMap<>();
        final List<String> contentExcludes = new ArrayList<>();
        final Map<String, Boolean> contentExcludesCase = new LinkedHashMap<>();
        filterActiveEntries(contentIncludes, contentIncludesCase, knownContentIncludes, knownContentIncludesCase);
        filterActiveEntries(contentExcludes, contentExcludesCase, knownContentExcludes, knownContentExcludesCase);

        if (!selectedDrives.isEmpty()) {
            if ((query == null || query.trim().isEmpty()) && extensionsAllow.isEmpty() && includes.isEmpty() && contentIncludes.isEmpty() && contentExcludes.isEmpty()) {
                JOptionPane.showMessageDialog(this, GuiConstants.MSG_ENTER_QUERY_OR_TYPE, GuiConstants.MSG_MISSING_INPUT_TITLE, JOptionPane.WARNING_MESSAGE);
                return;
            }
            controller.startSearch("", query == null ? "" : query.trim(), selectedDrives, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, knownIncludesAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, knownContentIncludesAllMode, knownTimeIncludes, knownTimeExcludes, knownTimeIncludesAllMode);
            return;
        }

        if (folder == null || folder.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_PLEASE_START_FOLDER, GuiConstants.MSG_MISSING_INPUT_TITLE, JOptionPane.WARNING_MESSAGE);
            return;
        }
        if ((query == null || query.trim().isEmpty()) && extensionsAllow.isEmpty() && includes.isEmpty() && contentIncludes.isEmpty() && contentExcludes.isEmpty()) {
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_PLEASE_QUERY_OR_TYPE_OR_FILTER, GuiConstants.MSG_MISSING_INPUT_TITLE, JOptionPane.WARNING_MESSAGE);
            return;
        }

        controller.startSearch(folder.trim(), query == null ? "" : query.trim(), selectedDrives, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, knownIncludesAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, knownContentIncludesAllMode, knownTimeIncludes, knownTimeExcludes, knownTimeIncludesAllMode);
    }

    /**
     * Filtert aktive Einträge aus den bekannten Filtern und überträgt sie in die Ziel-Listen/Maps.
     *
     * @param list         Ziel-Liste für aktive Filter
     * @param mapCase      Ziel-Map für Groß-/Kleinschreibung
     * @param mapKnown     Quell-Map der bekannten Filter
     * @param mapKnownCase Quell-Map der Groß-/Kleinschreibung
     */
    private void filterActiveEntries(final List<String> list, final Map<String, Boolean> mapCase, final Map<String, Boolean> mapKnown, final Map<String, Boolean> mapKnownCase) {
        for (final Map.Entry<String, Boolean> entry : mapKnown.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                String key = entry.getKey();
                list.add(key);
                mapCase.put(key, Boolean.TRUE.equals(mapKnownCase.get(key)));
            }
        }
    }

    /**
     * Bricht die laufende Suche ab.
     */
    void onCancel() {
        if (controller.cancelSearch()) {
            updateButtons(false);
        } else {
            updateFolderFieldState();
        }
    }

    /**
     * Öffnet den Filter-Dialog und übernimmt ggf. die neuen Filtereinstellungen.
     */
    void onManageFilters() {
        final FiltersDialog filtersDialog = new FiltersDialog(this, knownIncludes, knownExcludes, knownExtensionsAllow, knownExtensionsDeny, knownIncludesCase, knownExcludesCase, knownIncludesAllMode, knownContentIncludes, knownContentExcludes, knownContentIncludesCase, knownContentExcludesCase, knownContentIncludesAllMode, knownTimeIncludes, knownTimeExcludes, knownTimeIncludesAllMode);
        filtersDialog.setVisible(true);

        if (filtersDialog.isConfirmed()) {
            Map<String, Boolean> include = filtersDialog.getIncludesMap();
            Map<String, Boolean> exclude = filtersDialog.getExcludesMap();
            Map<String, Boolean> incCase = filtersDialog.getIncludesCaseMap();
            Map<String, Boolean> excCase = filtersDialog.getExcludesCaseMap();
            Map<String, Boolean> extensionsAllow = filtersDialog.getExtensionsAllowMap();
            Map<String, Boolean> extensionsDeny = filtersDialog.getExtensionsDenyMap();
            Map<String, Boolean> contentInclude = filtersDialog.getContentIncludesMap();
            Map<String, Boolean> contentExclude = filtersDialog.getContentExcludesMap();
            Map<String, Boolean> contentIncludeCase = filtersDialog.getContentIncludesCaseMap();
            Map<String, Boolean> contentExcludeCase = filtersDialog.getContentExcludesCaseMap();

            knownIncludes.clear();
            knownIncludes.putAll(include);
            knownExcludes.clear();
            knownExcludes.putAll(exclude);
            knownIncludesCase.clear();
            knownIncludesCase.putAll(incCase);
            knownExcludesCase.clear();
            knownExcludesCase.putAll(excCase);

            knownContentIncludes.clear();
            knownContentIncludes.putAll(contentInclude);
            knownContentExcludes.clear();
            knownContentExcludes.putAll(contentExclude);
            knownContentIncludesCase.clear();
            knownContentIncludesCase.putAll(contentIncludeCase);
            knownContentExcludesCase.clear();
            knownContentExcludesCase.putAll(contentExcludeCase);

            knownExtensionsAllow.clear();
            knownExtensionsAllow.putAll(extensionsAllow);
            knownExtensionsDeny.clear();
            knownExtensionsDeny.putAll(extensionsDeny);

            // Zeitfilter übernehmen (nur aktivierte Einträge)
            knownTimeIncludes.clear();
            for (com.mlprograms.searchmax.model.TimeRangeTableModel.Entry en : filtersDialog.getTimeIncludes()) {
                if (en.enabled) knownTimeIncludes.add(en);
            }
            knownTimeExcludes.clear();
            for (com.mlprograms.searchmax.model.TimeRangeTableModel.Entry en : filtersDialog.getTimeExcludes()) {
                if (en.enabled) knownTimeExcludes.add(en);
            }
            knownTimeIncludesAllMode = filtersDialog.isTimeIncludeAllMode();

            knownIncludesAllMode = filtersDialog.isIncludeAllMode();
            knownContentIncludesAllMode = filtersDialog.isContentIncludeAllMode();
            // timeIncludeAllMode ist oben schon gesetzt

            saveExtensionsToSettings();
        }
    }

    /**
     * Aktualisiert den Zustand des Ordnerfeldes und der Drive-Auswahl abhängig vom Suchstatus.
     */
    void updateFolderFieldState() {
        if (running) {
            topPanel.getFolderField().setEnabled(false);
            topPanel.getBrowseButton().setEnabled(false);
            drivePanel.setDrivesEnabled(false);
            return;
        }

        final boolean anyDriveSelected = !drivePanel.getSelectedDrives().isEmpty();
        topPanel.getFolderField().setEnabled(!anyDriveSelected);
        topPanel.getBrowseButton().setEnabled(!anyDriveSelected);
        drivePanel.setDrivesEnabled(true);
    }

    void updateButtons(final boolean running) {
        this.running = running;
        topPanel.getSearchButton().setEnabled(!running);
        topPanel.getCancelButton().setEnabled(running);
        topPanel.getQueryField().setEnabled(!running);
        topPanel.getCaseSensitiveCheck().setEnabled(!running);
        topPanel.getManageFiltersButton().setEnabled(!running);
        bottomPanel.getPerformanceModeCheck().setEnabled(!running);
        bottomPanel.getSettingsButton().setEnabled(!running);
        drivePanel.setDrivesEnabled(!running);
        updateFolderFieldState();
    }

    /**
     * Bindet das Model an den StatusUpdater.
     */
    private void bindModel() {
        model.addPropertyChangeListener(statusUpdater::onModelChange);
    }

    /**
     * Speichert die aktuellen Filter- und Sucheinstellungen in einer Properties-Datei.
     */
    private void saveExtensionsToSettings() {
        try {
            final Properties properties = new Properties();

            properties.put("includes", mapToString(knownIncludes));
            properties.put("excludes", mapToString(knownExcludes));
            properties.put("includesCase", mapToString(knownIncludesCase));
            properties.put("excludesCase", mapToString(knownExcludesCase));
            properties.put("extensionsAllow", mapToString(knownExtensionsAllow));
            properties.put("extensionsDeny", mapToString(knownExtensionsDeny));
            properties.put("contentIncludes", mapToString(knownContentIncludes));
            properties.put("contentExcludes", mapToString(knownContentExcludes));
            properties.put("contentIncludesCase", mapToString(knownContentIncludesCase));
            properties.put("contentExcludesCase", mapToString(knownContentExcludesCase));
            // Zeitfilter speichern (enabled|startMillis|endMillis|MODE;...)
            properties.put("timeIncludes", timeListToString(knownTimeIncludes));
            properties.put("timeExcludes", timeListToString(knownTimeExcludes));
            properties.put("timeIncludesMode", knownTimeIncludesAllMode ? "ALL" : "ANY");

            properties.put("startFolder", topPanel.getFolderField().getText() == null ? "" : topPanel.getFolderField().getText());
            properties.put("query", topPanel.getQueryField().getText() == null ? "" : topPanel.getQueryField().getText());
            properties.put("caseSensitive", Boolean.toString(topPanel.getCaseSensitiveCheck().isSelected()));
            properties.put("drives", String.join(",", drivePanel.getSelectedDrives()));
            properties.put("includesMode", knownIncludesAllMode ? "ALL" : "ANY");
            properties.put("contentIncludesMode", knownContentIncludesAllMode ? "ALL" : "ANY");
            properties.put("useAllCores", Boolean.toString(useAllCores));
            properties.put("extractionMode", extractionMode == null ? "POI_THEN_TIKA" : extractionMode.name());

            final File file = settingsFile.toFile();
            try (final FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                properties.store(fileOutputStream, "SearchMax Filter Settings");
            }
        } catch (final Exception exception) {
            log.warn("Fehler beim Speichern der Filtereinstellungen", exception);
        }
    }

    /**
     * Wandelt eine Map in einen String um, der für die Speicherung in Properties geeignet ist.
     *
     * @param map Die zu konvertierende Map
     * @return String-Repräsentation der Map
     */
    private String mapToString(final Map<String, Boolean> map) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final Map.Entry<String, Boolean> entry : map.entrySet()) {
            stringBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
        }

        return stringBuilder.toString();
    }

    /**
     * Speichert eine Liste von TimeRangeTableModel.Entry in einen String zur Speicherung in Properties.
     *
     * @param list Die zu speichernde Liste
     * @return String-Repräsentation der Liste
     */
    private String timeListToString(final List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> list) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final com.mlprograms.searchmax.model.TimeRangeTableModel.Entry entry : list) {
            long start = entry.start == null ? -1L : entry.start.getTime();
            long end = entry.end == null ? -1L : entry.end.getTime();
            String mode = entry.mode == null ? com.mlprograms.searchmax.model.TimeRangeTableModel.Mode.DATETIME.name() : entry.mode.name();
            stringBuilder.append(entry.enabled).append("|").append(start).append("|").append(end).append("|").append(mode).append(";");
        }

        return stringBuilder.toString();
    }

    /**
     * Lädt die gespeicherten Einstellungen aus der Properties-Datei und übernimmt sie in die UI und Filter-Maps.
     */
    private void loadSettings() {
        try {
            final File file = settingsFile.toFile();
            if (!file.exists()) {
                return;
            }

            final Properties properties = new Properties();
            try (final FileInputStream fileInputStream = new FileInputStream(file)) {
                properties.load(fileInputStream);
            }

            final String startFolder = properties.getProperty("startFolder", "").trim();
            if (!startFolder.isEmpty()) {
                topPanel.getFolderField().setText(startFolder);
            }

            final String query = properties.getProperty("query", "").trim();
            if (!query.isEmpty()) {
                topPanel.getQueryField().setText(query);
            }

            final String caseString = properties.getProperty("caseSensitive", "false").trim();
            topPanel.getCaseSensitiveCheck().setSelected("true".equalsIgnoreCase(caseString));

            final String drives = properties.getProperty("drives", "").trim();
            if (!drives.isEmpty()) {
                String[] parts = drives.split(",");
                List<String> driveList = new java.util.ArrayList<>();

                for (String part : parts) {
                    String trimmedPart = part.trim();
                    if (!trimmedPart.isEmpty()) {
                        driveList.add(trimmedPart);
                    }
                }

                drivePanel.setSelectedDrives(driveList);
            }

            final String includes = properties.getProperty("includes", "").trim();
            parseFilterString(includes, knownIncludes);

            final String excludes = properties.getProperty("excludes", "").trim();
            parseFilterString(excludes, knownExcludes);

            final String includeCase = properties.getProperty("includesCase", "").trim();
            if (!includeCase.isEmpty()) {
                final Map<String, Boolean> booleanMap = stringToMapBoolean(includeCase);
                knownIncludesCase.clear();
                knownIncludesCase.putAll(booleanMap);
            }

            final String excludeCase = properties.getProperty("excludesCase", "").trim();
            if (!excludeCase.isEmpty()) {
                final Map<String, Boolean> booleanMap = stringToMapBoolean(excludeCase);
                knownExcludesCase.clear();
                knownExcludesCase.putAll(booleanMap);
            }

            final String allow = properties.getProperty("extensionsAllow", "").trim();
            parseFilterString(allow, knownExtensionsAllow);
            final String deny = properties.getProperty("extensionsDeny", "").trim();
            parseFilterString(deny, knownExtensionsDeny);

            final String contentIncludes = properties.getProperty("contentIncludes", "").trim();
            parseFilterString(contentIncludes, knownContentIncludes);
            final String contentExcludes = properties.getProperty("contentExcludes", "").trim();
            parseFilterString(contentExcludes, knownContentExcludes);

            final String contentIncludeCase = properties.getProperty("contentIncludesCase", "").trim();
            if (!contentIncludeCase.isEmpty()) {
                final Map<String, Boolean> booleanMap = stringToMapBoolean(contentIncludeCase);
                knownContentIncludesCase.clear();
                knownContentIncludesCase.putAll(booleanMap);
            }

            final String contentExcludeCase = properties.getProperty("contentExcludesCase", "").trim();
            if (!contentExcludeCase.isEmpty()) {
                final Map<String, Boolean> booleanMap = stringToMapBoolean(contentExcludeCase);
                knownContentExcludesCase.clear();
                knownContentExcludesCase.putAll(booleanMap);
            }

            final String timeIncludes = properties.getProperty("timeIncludes", "").trim();
            parseTimeListString(timeIncludes, knownTimeIncludes);
            final String timeExcludes = properties.getProperty("timeExcludes", "").trim();
            parseTimeListString(timeExcludes, knownTimeExcludes);

            String timeMode = properties.getProperty("timeIncludesMode", "ANY").trim();
            knownTimeIncludesAllMode = "ALL".equalsIgnoreCase(timeMode);

            String mode = properties.getProperty("includesMode", "ANY").trim();
            knownIncludesAllMode = "ALL".equalsIgnoreCase(mode);

            String contentMode = properties.getProperty("contentIncludesMode", "ANY").trim();
            knownContentIncludesAllMode = "ALL".equalsIgnoreCase(contentMode);

            String useAll = properties.getProperty("useAllCores", "false").trim();
            useAllCores = "true".equalsIgnoreCase(useAll);
            bottomPanel.getPerformanceModeCheck().setSelected(useAllCores);
            controller.setUseAllCores(useAllCores);

            String extractionModeString = properties.getProperty("extractionMode", "POI_THEN_TIKA").trim();
            extractionMode = com.mlprograms.searchmax.ExtractionMode.valueOf(extractionModeString);
            controller.setExtractionMode(extractionMode);
        } catch (final Exception exception) {
            log.warn("Fehler beim Laden der Filtereinstellungen", exception);
        }
    }

    /**
     * Parst einen Filter-String und überträgt die Werte in die Ziel-Map.
     *
     * @param filterString Der zu parsende String
     * @param targetMap    Die Ziel-Map
     */
    private void parseFilterString(final String filterString, final Map<String, Boolean> targetMap) {
        if (!filterString.isEmpty()) {
            if (filterString.contains("=") || filterString.contains(";")) {
                final Map<String, Boolean> booleanMap = stringToMapBoolean(filterString);
                targetMap.clear();
                targetMap.putAll(booleanMap);
            } else {
                final String[] parts = filterString.split(",");
                for (String part : parts) {
                    String trimmedPart = part.trim();
                    if (!trimmedPart.isEmpty()) {
                        targetMap.put(trimmedPart, true);
                    }
                }
            }
        }
    }

    /**
     * Wandelt einen String im Format "key=value;..." in eine Map um.
     *
     * @param string Der zu parsende String
     * @return Die erzeugte Map
     */
    private Map<String, Boolean> stringToMapBoolean(final String string) {
        final Map<String, Boolean> linkedHashMap = new LinkedHashMap<>();
        final String[] parts = string.split(";");
        for (final String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) {
                continue;
            }

            int index = trimmedPart.indexOf('=');
            if (index <= 0) {
                continue;
            }

            String key = trimmedPart.substring(0, index);
            String value = trimmedPart.substring(index + 1);
            linkedHashMap.put(key, Boolean.TRUE.toString().equalsIgnoreCase(value) || "true".equalsIgnoreCase(value));
        }

        return linkedHashMap;
    }

    /**
     * Parst einen String und überträgt die Werte in die Ziel-Liste für Zeitfilter.
     *
     * @param timeString Der zu parsende String
     * @param targetList Die Ziel-Liste
     */
    private void parseTimeListString(final String timeString, final List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> targetList) {
        targetList.clear();
        if (!timeString.isEmpty()) {
            final String[] parts = timeString.split(";");
            for (String part : parts) {
                String trimmedPart = part.trim();
                if (trimmedPart.isEmpty()) {
                    continue;
                }

                String[] values = trimmedPart.split("\\|");
                if (values.length >= 3) {
                    try {
                        boolean enabled = Boolean.parseBoolean(values[0]);
                        long startMillis = Long.parseLong(values[1]);
                        long endMillis = Long.parseLong(values[2]);
                        com.mlprograms.searchmax.model.TimeRangeTableModel.Mode mode = com.mlprograms.searchmax.model.TimeRangeTableModel.Mode.DATETIME;
                        if (values.length >= 4 && values[3] != null && !values[3].isEmpty()) {
                            try {
                                mode = com.mlprograms.searchmax.model.TimeRangeTableModel.Mode.valueOf(values[3]);
                            } catch (IllegalArgumentException iae) {
                                // keep default
                            }
                        }

                        Date startDate = startMillis < 0 ? null : new Date(startMillis);
                        Date endDate = endMillis < 0 ? null : new Date(endMillis);
                        targetList.add(new com.mlprograms.searchmax.model.TimeRangeTableModel.Entry(enabled, startDate, endDate, mode));
                    } catch (NumberFormatException e) {
                        log.warn("Fehler beim Parsen des Zeitfilter-Eintrags: " + trimmedPart, e);
                    }
                }
            }
        }
    }

}
