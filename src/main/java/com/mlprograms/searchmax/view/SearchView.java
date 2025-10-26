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

    /**
     * Konstruktor für die SearchView.
     * Initialisiert die UI-Komponenten, lädt Einstellungen und bindet das Model.
     *
     * @param controller Der zugehörige Controller
     * @param model      Das zugehörige Model
     */
    public SearchView(final SearchController controller, final SearchModel model) {
        super("SearchMax");
        this.controller = controller;
        this.model = model;

        this.drivePanel = new DrivePanel(this);
        this.topPanel = new TopPanel(this);
        this.centerPanel = new CenterPanel(this);
        this.bottomPanel = new BottomPanel();
        this.statusUpdater = new StatusUpdater(this);

        initUI();
        bindModel();
        loadSettings();

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
        jFileChooser.setDialogTitle("Ordner auswählen");
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

        if (!selectedDrives.isEmpty()) {
            if ((query == null || query.trim().isEmpty()) && extensionsAllow.isEmpty() && includes.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Bitte einen Suchtext oder Dateityp angeben.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
                return;
            }
            controller.startSearch("", query == null ? "" : query.trim(), selectedDrives, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase);
            return;
        }

        if (folder == null || folder.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte einen Startordner angeben oder ein Laufwerk auswählen.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if ((query == null || query.trim().isEmpty()) && extensionsAllow.isEmpty() && includes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte einen Suchtext, Dateityp oder mindestens einen 'Soll enthalten'-Filter angeben.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        controller.startSearch(folder.trim(), query == null ? "" : query.trim(), selectedDrives, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase);
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
        final FiltersDialog filtersDialog = new FiltersDialog(this, knownIncludes, knownExcludes, knownExtensionsAllow, knownExtensionsDeny, knownIncludesCase, knownExcludesCase);
        filtersDialog.setVisible(true);

        if (filtersDialog.isConfirmed()) {
            Map<String, Boolean> include = filtersDialog.getIncludesMap();
            Map<String, Boolean> exclude = filtersDialog.getExcludesMap();
            Map<String, Boolean> incCase = filtersDialog.getIncludesCaseMap();
            Map<String, Boolean> excCase = filtersDialog.getExcludesCaseMap();
            Map<String, Boolean> extensionsAllow = filtersDialog.getExtensionsAllowMap();
            Map<String, Boolean> extensionsDeny = filtersDialog.getExtensionsDenyMap();

            knownIncludes.clear();
            knownIncludes.putAll(include);
            knownExcludes.clear();
            knownExcludes.putAll(exclude);
            knownIncludesCase.clear();
            knownIncludesCase.putAll(incCase);
            knownExcludesCase.clear();
            knownExcludesCase.putAll(excCase);

            knownExtensionsAllow.clear();
            knownExtensionsAllow.putAll(extensionsAllow);
            knownExtensionsDeny.clear();
            knownExtensionsDeny.putAll(extensionsDeny);

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

    /**
     * Aktiviert oder deaktiviert die Buttons und Eingabefelder je nach Suchstatus.
     *
     * @param running Gibt an, ob eine Suche läuft
     */
    void updateButtons(final boolean running) {
        this.running = running;
        topPanel.getSearchButton().setEnabled(!running);
        topPanel.getCancelButton().setEnabled(running);
        topPanel.getQueryField().setEnabled(!running);
        topPanel.getCaseSensitiveCheck().setEnabled(!running);
        topPanel.getManageFiltersButton().setEnabled(!running);
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

            properties.put("startFolder", topPanel.getFolderField().getText() == null ? "" : topPanel.getFolderField().getText());
            properties.put("query", topPanel.getQueryField().getText() == null ? "" : topPanel.getQueryField().getText());
            properties.put("caseSensitive", Boolean.toString(topPanel.getCaseSensitiveCheck().isSelected()));
            properties.put("drives", String.join(",", drivePanel.getSelectedDrives()));

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

}
