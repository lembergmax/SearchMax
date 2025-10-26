package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.controller.SearchController;
import com.mlprograms.searchmax.model.SearchModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Getter
public final class SearchView extends JFrame {

    private final SearchController controller;
    private final SearchModel model;

    private final Path settingsFile = Paths.get(System.getProperty("user.home"), ".searchmax.properties");

    private final DrivePanel drivePanel;
    private final TopPanel topPanel;
    private final CenterPanel centerPanel;
    private final BottomPanel bottomPanel;
    private final StatusUpdater statusUpdater;

    private boolean running = false;

    private final Map<String, Boolean> knownIncludes = new LinkedHashMap<>();
    private final Map<String, Boolean> knownExcludes = new LinkedHashMap<>();
    private final Map<String, Boolean> knownIncludesCase = new LinkedHashMap<>();
    private final Map<String, Boolean> knownExcludesCase = new LinkedHashMap<>();
    private final Map<String, Boolean> knownExtensionsAllow = new LinkedHashMap<>();
    private final Map<String, Boolean> knownExtensionsDeny = new LinkedHashMap<>();

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

    private void initUI() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        Container content = getContentPane();
        content.setLayout(new BorderLayout(6, 6));
        content.add(topPanel, BorderLayout.NORTH);
        content.add(centerPanel, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);

        topPanel.addListeners();
        updateButtons(false);
        updateFolderFieldState();

        pack();
        setMinimumSize(new Dimension(700, 400));
        setSize(800, 600);
        setLocationRelativeTo(null);

        SwingUtilities.invokeLater(() -> topPanel.getQueryField().requestFocusInWindow());
    }

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
        final Map<String, Boolean> includesCase = new java.util.LinkedHashMap<>();
        final List<String> excludes = new ArrayList<>();
        final Map<String, Boolean> excludesCase = new java.util.LinkedHashMap<>();

        for (Map.Entry<String, Boolean> entry : knownIncludes.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                String key = entry.getKey();
                includes.add(key);
                includesCase.put(key, Boolean.TRUE.equals(knownIncludesCase.get(key)));
            }
        }
        for (Map.Entry<String, Boolean> entry : knownExcludes.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                String key = entry.getKey();
                excludes.add(key);
                excludesCase.put(key, Boolean.TRUE.equals(knownExcludesCase.get(key)));
            }
        }

        if (!selectedDrives.isEmpty()) {
            if ((query == null || query.trim().isEmpty()) && extensionsAllow.isEmpty() && includes.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Bitte einen Suchtext oder Dateiendungen angeben.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
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
            JOptionPane.showMessageDialog(this, "Bitte einen Suchtext, Dateiendungen oder mindestens einen 'Soll enthalten'-Filter angeben.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
            return;
        }
        controller.startSearch(folder.trim(), query == null ? "" : query.trim(), selectedDrives, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase);
    }

    void onCancel() {
        if (controller.cancelSearch()) {
            updateButtons(false);
        } else {
            updateFolderFieldState();
        }
    }

    void onManageFilters() {
        // pass also the case maps so the dialog can initialize the case checkboxes correctly
        FiltersDialog filtersDialog = new FiltersDialog(this, knownIncludes, knownExcludes, knownExtensionsAllow, knownExtensionsDeny, knownIncludesCase, knownExcludesCase);
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

    void updateFolderFieldState() {
        if (running) {
            topPanel.getFolderField().setEnabled(false);
            topPanel.getBrowseButton().setEnabled(false);
            return;
        }

        boolean anyDriveSelected = !drivePanel.getSelectedDrives().isEmpty();
        topPanel.getFolderField().setEnabled(!anyDriveSelected);
        topPanel.getBrowseButton().setEnabled(!anyDriveSelected);
    }

    void updateButtons(final boolean running) {
        this.running = running;
        topPanel.getSearchButton().setEnabled(!running);
        topPanel.getCancelButton().setEnabled(running);
        topPanel.getQueryField().setEnabled(!running);
        updateFolderFieldState();
    }

    private void bindModel() {
        model.addPropertyChangeListener(statusUpdater::onModelChange);
    }

    private void saveExtensionsToSettings() {
        try {
            final Properties properties = new java.util.Properties();

            properties.put("includes", String.join(",", knownIncludes.keySet()));
            properties.put("excludes", String.join(",", knownExcludes.keySet()));
            properties.put("includesCase", mapToString(knownIncludesCase));
            properties.put("excludesCase", mapToString(knownExcludesCase));
            properties.put("extensionsAllow", String.join(",", knownExtensionsAllow.keySet()));
            properties.put("extensionsDeny", String.join(",", knownExtensionsDeny.keySet()));

            java.io.File file = settingsFile.toFile();
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                properties.store(fos, "SearchMax Filter Settings");
            }
        } catch (Exception e) {
            log.warn("Fehler beim Speichern der Filtereinstellungen", e);
        }
    }

    private String mapToString(Map<String, Boolean> map) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, Boolean> entry : map.entrySet()) {
            stringBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
        }

        return stringBuilder.toString();
    }

    // neue Methode: lade Einstellungen beim Start
    private void loadSettings() {
        try {
            java.io.File file = settingsFile.toFile();
            if (!file.exists()) return;
            final Properties properties = new Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                properties.load(fis);
            }

            String inc = properties.getProperty("includes", "").trim();
            if (!inc.isEmpty()) {
                String[] parts = inc.split(",");
                for (String p : parts) {
                    String t = p.trim();
                    if (!t.isEmpty()) knownIncludes.put(t, true);
                }
            }
            String exc = properties.getProperty("excludes", "").trim();
            if (!exc.isEmpty()) {
                String[] parts = exc.split(",");
                for (String p : parts) {
                    String t = p.trim();
                    if (!t.isEmpty()) knownExcludes.put(t, true);
                }
            }

            String incCase = properties.getProperty("includesCase", "").trim();
            if (!incCase.isEmpty()) {
                Map<String, Boolean> m = stringToMapBoolean(incCase);
                knownIncludesCase.clear();
                knownIncludesCase.putAll(m);
            }
            String excCase = properties.getProperty("excludesCase", "").trim();
            if (!excCase.isEmpty()) {
                Map<String, Boolean> m = stringToMapBoolean(excCase);
                knownExcludesCase.clear();
                knownExcludesCase.putAll(m);
            }

            String allow = properties.getProperty("extensionsAllow", "").trim();
            if (!allow.isEmpty()) {
                String[] parts = allow.split(",");
                for (String p : parts) {
                    String t = p.trim();
                    if (!t.isEmpty()) knownExtensionsAllow.put(t, true);
                }
            }
            String deny = properties.getProperty("extensionsDeny", "").trim();
            if (!deny.isEmpty()) {
                String[] parts = deny.split(",");
                for (String p : parts) {
                    String t = p.trim();
                    if (!t.isEmpty()) knownExtensionsDeny.put(t, true);
                }
            }

        } catch (Exception e) {
            log.warn("Fehler beim Laden der Filtereinstellungen", e);
        }
    }

    private Map<String, Boolean> stringToMapBoolean(String s) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        String[] parts = s.split(";");
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            int idx = t.indexOf('=');
            if (idx <= 0) continue;
            String k = t.substring(0, idx);
            String v = t.substring(idx + 1);
            out.put(k, Boolean.TRUE.toString().equalsIgnoreCase(v) || "true".equalsIgnoreCase(v));
        }
        return out;
    }


}
