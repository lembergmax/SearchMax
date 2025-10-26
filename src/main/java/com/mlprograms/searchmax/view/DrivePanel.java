package com.mlprograms.searchmax.view;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class DrivePanel extends JPanel {

    private final JCheckBox[] driveCheckBoxes;
    private final SearchView parent;

    public DrivePanel(final SearchView parent) {
        super(new FlowLayout(FlowLayout.LEFT));
        this.parent = parent;
        setBorder(BorderFactory.createTitledBorder("Laufwerke durchsuchen"));

        final File[] roots = File.listRoots();
        driveCheckBoxes = new JCheckBox[roots.length];

        for (int i = 0; i < roots.length; i++) {
            final JCheckBox box = new JCheckBox(roots[i].getPath());
            box.setSelected(false);
            box.addActionListener(e -> this.parent.getTopPanel().updateFolderFieldState());
            driveCheckBoxes[i] = box;
            add(box);
        }
    }

    public List<String> getSelectedDrives() {
        return Arrays.stream(driveCheckBoxes)
                .filter(AbstractButton::isSelected)
                .map(AbstractButton::getText)
                .collect(Collectors.toList());
    }

    public void setDrivesEnabled(boolean enabled) {
        for (JCheckBox cb : driveCheckBoxes) {
            cb.setEnabled(enabled);
        }
    }

    public void setSelectedDrives(List<String> drives) {
        if (drives == null) {
            return;
        }

        List<String> normalized = drives.stream().map(String::trim).toList();
        for (JCheckBox cb : driveCheckBoxes) {
            cb.setSelected(normalized.contains(cb.getText()));
        }

        this.parent.getTopPanel().updateFolderFieldState();
    }

}
