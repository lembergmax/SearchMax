package com.mlprograms.searchmax.view;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;

@Getter
public final class TopPanel extends JPanel {

    private final JTextField folderField = new JTextField(30);
    private final JButton browseButton = new JButton("Durchsuchen...");
    private final JTextField queryField = new JTextField(20);
    private final JCheckBox caseSensitiveCheck = new JCheckBox("Groß-/Kleinschreibung beachten");
    private final JButton searchButton = new JButton("Suche");
    private final JButton cancelButton = new JButton("Abbrechen");
    private final JButton manageFiltersButton = new JButton("Filter verwalten...");

    private final SearchView parent;

    public TopPanel(SearchView parent) {
        super(new GridBagLayout());
        this.parent = parent;
        setupUI();
    }

    private void setupUI() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        // --- Ordner Zeile ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        add(new JLabel("Ordner"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        add(folderField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        add(browseButton, gbc);

        // --- Drive Panel ---
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(parent.getDrivePanel(), gbc);

        // --- Suchtext Zeile ---
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        add(new JLabel("Suchtext"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        add(queryField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        add(manageFiltersButton, gbc);

        // --- Buttons Zeile (Suchen / Abbrechen) ---
        gbc.gridy = 3;
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL; // Buttons füllen horizontal
        gbc.weightx = 0.5;                        // gleiche Breite
        gbc.anchor = GridBagConstraints.CENTER;
        add(searchButton, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.5;
        add(cancelButton, gbc);

        // --- Case-Sensitive Checkbox ---
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        add(caseSensitiveCheck, gbc);
    }

    public void addListeners() {
        browseButton.addActionListener(e -> parent.onBrowse());
        searchButton.addActionListener(e -> parent.onSearch());
        cancelButton.addActionListener(e -> parent.onCancel());
        manageFiltersButton.addActionListener(e -> parent.onManageFilters());
    }

    public void updateFolderFieldState() {
        parent.updateFolderFieldState();
    }

}
