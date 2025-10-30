package com.mlprograms.searchmax.view;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public final class DrivePanel extends JPanel {

    private final JCheckBox[] driveCheckboxes;
    private final SearchView parentView;

    public DrivePanel(final SearchView parentView) {
        super(new FlowLayout(FlowLayout.LEFT));
        this.parentView = parentView;
        setBorder(BorderFactory.createTitledBorder(GuiConstants.DRIVE_PANEL_TITLE));

        final File[] rootDirectories = File.listRoots();
        driveCheckboxes = new JCheckBox[rootDirectories.length];

        initializeDriveCheckboxes(rootDirectories);
    }

    private void initializeDriveCheckboxes(final File[] rootDirectories) {
        for (int index = 0; index < rootDirectories.length; index++) {
            final JCheckBox checkbox = createDriveCheckbox(rootDirectories[index]);
            driveCheckboxes[index] = checkbox;
            add(checkbox);
        }
    }

    private JCheckBox createDriveCheckbox(final File rootDirectory) {
        final JCheckBox checkbox = new JCheckBox(rootDirectory.getPath());
        checkbox.setSelected(false);
        checkbox.addActionListener(actionEvent -> {
            parentView.getTopPanel().updateFolderFieldState();
            parentView.saveSettings();
        });
        return checkbox;
    }

    public List<String> getSelectedDrives() {
        return Arrays.stream(driveCheckboxes)
                .filter(AbstractButton::isSelected)
                .map(AbstractButton::getText)
                .collect(Collectors.toList());
    }

    public void setDrivesEnabled(final boolean enabled) {
        for (final JCheckBox checkbox : driveCheckboxes) {
            checkbox.setEnabled(enabled);
        }
    }

    public void setSelectedDrives(final List<String> selectedDrives) {
        if (selectedDrives == null || selectedDrives.isEmpty()) {
            return;
        }

        final List<String> normalizedDrivePaths = selectedDrives.stream()
                .map(String::trim)
                .toList();

        for (final JCheckBox checkbox : driveCheckboxes) {
            checkbox.setSelected(normalizedDrivePaths.contains(checkbox.getText()));
        }

        parentView.getTopPanel().updateFolderFieldState();
    }

    public boolean hasSelectedDrives() {
        return Arrays.stream(driveCheckboxes)
                .anyMatch(AbstractButton::isSelected);
    }

    public int getSelectedDriveCount() {
        return (int) Arrays.stream(driveCheckboxes)
                .filter(AbstractButton::isSelected)
                .count();
    }

}