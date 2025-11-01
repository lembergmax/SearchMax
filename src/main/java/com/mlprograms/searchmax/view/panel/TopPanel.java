package com.mlprograms.searchmax.view.panel;

import com.mlprograms.searchmax.view.GuiConstants;
import com.mlprograms.searchmax.view.SearchView;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;

@Getter
public final class TopPanel extends JPanel {

    private final JTextField folderPathTextField = new JTextField(30);
    private final JButton browseFolderButton = new JButton(GuiConstants.BROWSE_BUTTON);
    private final JTextField searchQueryTextField = new JTextField(20);
    private final JCheckBox caseSensitiveCheckbox = new JCheckBox(GuiConstants.CASE_SENSITIVE);
    private final JButton searchButton = new JButton(GuiConstants.SEARCH_BUTTON);
    private final JButton cancelSearchButton = new JButton(GuiConstants.CANCEL_BUTTON);
    private final JButton manageFiltersButton = new JButton(GuiConstants.MANAGE_FILTERS);
    private final SearchView parentView;

    public TopPanel(final SearchView parentView) {
        super(new GridBagLayout());
        this.parentView = parentView;
        initializeUserInterface();
    }

    private void initializeUserInterface() {
        final GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.insets = new Insets(4, 4, 4, 4);

        addFolderSelectionComponents(gridBagConstraints);
        addDrivePanelComponent(gridBagConstraints);
        addSearchQueryComponents(gridBagConstraints);
        addActionButtons(gridBagConstraints);
        addCaseSensitiveCheckbox(gridBagConstraints);
    }

    private void addFolderSelectionComponents(final GridBagConstraints gridBagConstraints) {
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        add(new JLabel(GuiConstants.LABEL_FOLDER), gridBagConstraints);

        addTextFieldWithButton(gridBagConstraints, folderPathTextField, browseFolderButton);
    }

    private void addDrivePanelComponent(final GridBagConstraints gridBagConstraints) {
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        add(parentView.getDrivePanel(), gridBagConstraints);
    }

    private void addSearchQueryComponents(final GridBagConstraints gridBagConstraints) {
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0;
        add(new JLabel(GuiConstants.LABEL_SEARCHTEXT), gridBagConstraints);

        addTextFieldWithButton(gridBagConstraints, searchQueryTextField, manageFiltersButton);
    }

    private void addActionButtons(final GridBagConstraints gridBagConstraints) {
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.anchor = GridBagConstraints.CENTER;
        add(searchButton, gridBagConstraints);

        gridBagConstraints.gridx = 2;
        gridBagConstraints.weightx = 0.5;
        add(cancelSearchButton, gridBagConstraints);
    }

    private void addCaseSensitiveCheckbox(final GridBagConstraints gridBagConstraints) {
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        add(caseSensitiveCheckbox, gridBagConstraints);
    }

    private void addTextFieldWithButton(final GridBagConstraints gridBagConstraints,
                                        final JTextField textField, final JButton button) {
        gridBagConstraints.gridx = 1;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        add(textField, gridBagConstraints);

        gridBagConstraints.gridx = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0;
        add(button, gridBagConstraints);
    }

    public void initializeEventListeners() {
        browseFolderButton.addActionListener(event -> parentView.onBrowseFolder());
        searchButton.addActionListener(event -> parentView.onSearch());
        cancelSearchButton.addActionListener(event -> parentView.onCancelSearch());
        manageFiltersButton.addActionListener(event -> parentView.onManageFilters());
    }

    public void updateFolderFieldState() {
        parentView.updateFolderFieldState();
    }

}