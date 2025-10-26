package com.mlprograms.searchmax.view;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;

/**
 * Das TopPanel ist ein UI-Panel, das die Steuerelemente für die Suche bereitstellt.
 * Es enthält Felder zur Auswahl des Ordners, zur Eingabe des Suchtexts,
 * Schaltflächen für die Suche, das Abbrechen, das Verwalten von Filtern sowie eine Checkbox für die Groß-/Kleinschreibung.
 * Die Anordnung erfolgt mit GridBagLayout.
 */
@Getter
public final class TopPanel extends JPanel {

    /**
     * Textfeld zur Anzeige und Auswahl des Ordners.
     */
    private final JTextField folderField = new JTextField(30);
    /**
     * Schaltfläche zum Durchsuchen von Ordnern.
     */
    private final JButton browseButton = new JButton(GuiConstants.BROWSE_BUTTON);
    /**
     * Textfeld zur Eingabe des Suchtexts.
     */
    private final JTextField queryField = new JTextField(20);
    /**
     * Checkbox zur Beachtung der Groß-/Kleinschreibung.
     */
    private final JCheckBox caseSensitiveCheck = new JCheckBox(GuiConstants.CASE_SENSITIVE);
    /**
     * Schaltfläche zum Starten der Suche.
     */
    private final JButton searchButton = new JButton(GuiConstants.SEARCH_BUTTON);
    /**
     * Schaltfläche zum Abbrechen der Suche.
     */
    private final JButton cancelButton = new JButton(GuiConstants.CANCEL_BUTTON);
    /**
     * Schaltfläche zum Verwalten der Filter.
     */
    private final JButton manageFiltersButton = new JButton(GuiConstants.MANAGE_FILTERS);

    /**
     * Referenz auf die übergeordnete SearchView.
     */
    private final SearchView parent;

    /**
     * Konstruktor für das TopPanel.
     *
     * @param parent Die übergeordnete SearchView-Instanz.
     */
    public TopPanel(SearchView parent) {
        super(new GridBagLayout());
        this.parent = parent;
        setupUI();
    }

    /**
     * Initialisiert und arrangiert die UI-Komponenten im Panel.
     */
    private void setupUI() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        add(new JLabel(GuiConstants.LABEL_FOLDER), gbc);

        extracted(gbc, folderField, browseButton);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(parent.getDrivePanel(), gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        add(new JLabel(GuiConstants.LABEL_SEARCHTEXT), gbc);

        extracted(gbc, queryField, manageFiltersButton);

        gbc.gridy = 3;
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.anchor = GridBagConstraints.CENTER;
        add(searchButton, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.5;
        add(cancelButton, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        add(caseSensitiveCheck, gbc);
    }

    private void extracted(GridBagConstraints gbc, JTextField folderField, JButton browseButton) {
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        add(folderField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        add(browseButton, gbc);
    }

    /**
     * Fügt die ActionListener zu den Schaltflächen hinzu.
     */
    public void addListeners() {
        browseButton.addActionListener(e -> parent.onBrowse());
        searchButton.addActionListener(e -> parent.onSearch());
        cancelButton.addActionListener(e -> parent.onCancel());
        manageFiltersButton.addActionListener(e -> parent.onManageFilters());
    }

    /**
     * Aktualisiert den Status des Ordnerfeldes über die Parent-View.
     */
    public void updateFolderFieldState() {
        parent.updateFolderFieldState();
    }

}
