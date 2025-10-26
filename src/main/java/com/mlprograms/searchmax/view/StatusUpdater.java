package com.mlprograms.searchmax.view;

import javax.swing.*;
import java.awt.*;

public final class StatusUpdater {
    private static final String SEARCH_RUNNING_TEXT = "Suche läuft";
    private final SearchView parent;
    private Timer dotTimer;
    private int dotCount = 0;

    public StatusUpdater(SearchView parent) {
        this.parent = parent;
    }

    public void onModelChange(java.beans.PropertyChangeEvent evt) {
        switch (evt.getPropertyName()) {
            case "results" -> SwingUtilities.invokeLater(() -> {
                parent.getCenterPanel().getListModel().clear();
                Object newVal = evt.getNewValue();
                if (newVal instanceof java.util.List<?> list) {
                    for (Object o : list) {
                        if (o instanceof String s) parent.getCenterPanel().getListModel().addElement(s);
                    }
                }
            });
            case "status" -> SwingUtilities.invokeLater(() -> updateStatus((String) evt.getNewValue()));
        }
    }

    private void updateStatus(String newStatus) {
        boolean runningNow = newStatus != null && newStatus.startsWith(SEARCH_RUNNING_TEXT);
        if (runningNow) {
            startDotAnimation();
        } else {
            stopDotAnimation();
        }
        parent.getBottomPanel().getStatusLabel().setText(newStatus);
        parent.updateButtons(runningNow);
    }

    private void startDotAnimation() {
        if (dotTimer == null) {
            dotTimer = new Timer(500, e -> {
                dotCount = (dotCount + 1) % 4;
                parent.getBottomPanel().getStatusLabel().setText(SEARCH_RUNNING_TEXT + ".".repeat(dotCount));
            });
            dotTimer.setInitialDelay(0);
        }
        dotTimer.start();
    }

    private void stopDotAnimation() {
        if (dotTimer != null && dotTimer.isRunning()) dotTimer.stop();
        dotCount = 0;
    }

}
