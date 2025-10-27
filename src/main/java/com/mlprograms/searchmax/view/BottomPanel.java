package com.mlprograms.searchmax.view;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;

@Getter
public final class BottomPanel extends JPanel {

    private final JLabel statusLabel = new JLabel(GuiConstants.STATUS_READY);
    private final JProgressBar progressBar = new JProgressBar();
    private final JCheckBox performanceModeCheck = new JCheckBox(GuiConstants.PERFORMANCE_MODE);
    private final JButton logsButton = new JButton(GuiConstants.BUTTON_LOGS);
    private final JButton settingsButton = new JButton("Settings");

    public BottomPanel(SearchView parent) {
        super(new BorderLayout());
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT));
        info.add(new JLabel(GuiConstants.STATUS_LABEL_PREFIX));
        info.add(statusLabel);
        info.add(Box.createHorizontalStrut(16));
        add(info, BorderLayout.WEST);

        progressBar.setVisible(false);
        progressBar.setIndeterminate(false);
        progressBar.setPreferredSize(new Dimension(220, 18));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(performanceModeCheck);
        right.add(logsButton);
        right.add(settingsButton);
        right.add(progressBar);
        add(right, BorderLayout.EAST);

        // Log-Button öffnet Log-Viewer über die Parent-View
        logsButton.addActionListener(e -> {
            try {
                parent.onShowLogs();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Fehler beim Öffnen der Logs: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Settings Button öffnet Einstellungen-Dialog
        settingsButton.addActionListener(e -> {
            try {
                parent.onShowSettings();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Fehler beim Öffnen der Einstellungen: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

}
