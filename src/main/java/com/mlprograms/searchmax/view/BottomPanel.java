package com.mlprograms.searchmax.view;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;

@Getter
public final class BottomPanel extends JPanel {

    private final JLabel statusLabel = new JLabel("Bereit");
    private final JProgressBar progressBar = new JProgressBar();
    private final JCheckBox performanceModeCheck = new JCheckBox("Leistungsmodus");

    public BottomPanel() {
        super(new BorderLayout());
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT));
        info.add(new JLabel("Status: "));
        info.add(statusLabel);
        info.add(Box.createHorizontalStrut(16));
        add(info, BorderLayout.WEST);

        progressBar.setVisible(false);
        progressBar.setIndeterminate(false);
        progressBar.setPreferredSize(new Dimension(220, 18));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(performanceModeCheck);
        right.add(progressBar);
        add(right, BorderLayout.EAST);
    }

}
