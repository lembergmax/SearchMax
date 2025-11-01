package com.mlprograms.searchmax.view.panel;

import com.mlprograms.searchmax.view.GuiConstants;
import com.mlprograms.searchmax.view.SearchView;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;

@Getter
public final class BottomPanel extends JPanel {

    private static final int HORIZONTAL_STRUT_SIZE = 16;
    private static final int PROGRESS_BAR_WIDTH = 220;
    private static final int PROGRESS_BAR_HEIGHT = 18;
    private static final int LAYOUT_GAP = 8;

    private final JLabel statusLabel = new JLabel(GuiConstants.STATUS_READY);
    private final JProgressBar progressBar = new JProgressBar();
    private final JCheckBox performanceModeCheckbox = new JCheckBox(GuiConstants.PERFORMANCE_MODE);
    private final JButton showLogsButton = new JButton(GuiConstants.BUTTON_LOGS);
    private final JButton showSettingsButton = new JButton(GuiConstants.BUTTON_SETTINGS);

    public BottomPanel(final SearchView parentView) {
        super(new BorderLayout(LAYOUT_GAP, LAYOUT_GAP));
        initializeUserInterface();
        initializeEventListeners(parentView);
    }

    private void initializeUserInterface() {
        addLeftInformationPanel();
        addRightControlPanel();
        configureProgressBar();
    }

    private void addLeftInformationPanel() {
        final JPanel informationPanel = createInformationPanel();
        add(informationPanel, BorderLayout.WEST);
    }

    private JPanel createInformationPanel() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel(GuiConstants.STATUS_LABEL_PREFIX));
        panel.add(statusLabel);
        panel.add(Box.createHorizontalStrut(HORIZONTAL_STRUT_SIZE));
        return panel;
    }

    private void addRightControlPanel() {
        final JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.EAST);
    }

    private JPanel createControlPanel() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(performanceModeCheckbox);
        panel.add(showLogsButton);
        panel.add(showSettingsButton);
        panel.add(progressBar);
        return panel;
    }

    private void configureProgressBar() {
        progressBar.setVisible(false);
        progressBar.setIndeterminate(false);
        progressBar.setPreferredSize(new Dimension(PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT));
    }

    private void initializeEventListeners(final SearchView parentView) {
        initializeShowLogsButtonListener(parentView);
        initializeShowSettingsButtonListener(parentView);
    }

    private void initializeShowLogsButtonListener(final SearchView parentView) {
        showLogsButton.addActionListener(actionEvent -> {
            try {
                parentView.onShowLogs();
            } catch (final Exception exception) {
                showErrorMessage(
                        GuiConstants.MSG_ERROR_OPEN_LOGS_PREFIX + exception.getMessage(),
                        GuiConstants.MSG_ERROR_TITLE
                );
            }
        });
    }

    private void initializeShowSettingsButtonListener(final SearchView parentView) {
        showSettingsButton.addActionListener(actionEvent -> {
            try {
                parentView.onShowSettings();
            } catch (final Exception exception) {
                showErrorMessage(
                        GuiConstants.MSG_ERROR_OPEN_SETTINGS_PREFIX + exception.getMessage(),
                        GuiConstants.MSG_ERROR_TITLE
                );
            }
        });
    }

    private void showErrorMessage(final String message, final String title) {
        JOptionPane.showMessageDialog(
                this,
                message,
                title,
                JOptionPane.ERROR_MESSAGE
        );
    }

    public void updateStatus(final String statusMessage) {
        statusLabel.setText(statusMessage);
    }

}