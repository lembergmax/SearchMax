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

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(parent.getDrivePanel(), gbc);
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
