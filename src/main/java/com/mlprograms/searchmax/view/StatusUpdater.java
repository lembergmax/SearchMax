package com.mlprograms.searchmax.view;

import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.beans.PropertyChangeEvent;

/**
 * Aktualisiert den Statusbereich der Benutzeroberfläche basierend auf Änderungen im Modell.
 * Zeigt eine animierte Statusmeldung an, wenn eine Suche läuft.
 */
@RequiredArgsConstructor
public final class StatusUpdater {

    /**
     * Text, der angezeigt wird, wenn die Suche läuft.
     */
    private static final String SEARCH_RUNNING_TEXT = "Suche läuft";
    /**
     * Referenz auf die zu aktualisierende Suchansicht.
     */
    private final SearchView searchView;
    /**
     * Timer für die animierten Punkte im Statuslabel.
     */
    private Timer dotTimer;
    /**
     * Zählt die Anzahl der Punkte für die Animation.
     */
    private int dotCount = 0;

    /**
     * Wird aufgerufen, wenn sich das Modell ändert.
     * Aktualisiert die Ergebnisliste oder den Statusbereich je nach Property.
     *
     * @param propertyChangeEvent das Ereignis, das die Änderung beschreibt
     */
    public void onModelChange(final PropertyChangeEvent propertyChangeEvent) {
        switch (propertyChangeEvent.getPropertyName()) {
            case "results" -> SwingUtilities.invokeLater(() -> {
                final Object oldValue = propertyChangeEvent.getOldValue();
                final Object newValue = propertyChangeEvent.getNewValue();
                javax.swing.DefaultListModel<String> model = searchView.getCenterPanel().getListModel();

                if (oldValue instanceof java.util.List<?> oldList && newValue instanceof java.util.List<?> newList) {
                    final int oldSize = oldList.size();
                    final int newSize = newList.size();
                    if (newSize < oldSize) {
                        model.clear();
                        for (Object object : newList) {
                            if (object instanceof String string) model.addElement(string);
                        }
                    } else {
                        for (int i = oldSize; i < newSize; i++) {
                            Object obj = newList.get(i);
                            if (obj instanceof String s) model.addElement(s);
                        }
                    }
                } else if (newValue instanceof java.util.List<?> list) {
                    model.clear();
                    for (Object object : list) {
                        if (object instanceof String string) model.addElement(string);
                    }
                }
            });
            case "resultAdded" -> SwingUtilities.invokeLater(() -> {
                final Object nv = propertyChangeEvent.getNewValue();
                if (nv instanceof String s) {
                    searchView.getCenterPanel().getListModel().addElement(s);
                }
            });
            case "resultsBatch" -> SwingUtilities.invokeLater(() -> {
                final Object nv = propertyChangeEvent.getNewValue();
                if (nv instanceof java.util.List<?> list) {
                    final DefaultListModel<String> model = searchView.getCenterPanel().getListModel();
                    for (Object o : list) {
                        if (o instanceof String s) model.addElement(s);
                    }
                }
            });
            case "status" -> SwingUtilities.invokeLater(() -> updateStatus((String) propertyChangeEvent.getNewValue()));
        }
    }

    /**
     * Aktualisiert den Statusbereich und steuert die Animation.
     *
     * @param newStatus der neue Status-Text
     */
    private void updateStatus(final String newStatus) {
        final boolean runningNow = newStatus != null && newStatus.startsWith(SEARCH_RUNNING_TEXT);
        if (runningNow) {
            startDotAnimation();
        } else {
            stopDotAnimation();
        }

        searchView.getBottomPanel().getStatusLabel().setText(newStatus);
        searchView.updateButtons(runningNow);
    }

    /**
     * Startet die Animation der Punkte im Statuslabel.
     */
    private void startDotAnimation() {
        if (dotTimer == null) {
            dotTimer = new Timer(250, e -> {
                dotCount = (dotCount + 1) % 4;
                searchView.getBottomPanel().getStatusLabel().setText(SEARCH_RUNNING_TEXT + ".".repeat(dotCount));
            });

            dotTimer.setInitialDelay(0);
        }

        dotTimer.start();
    }

    /**
     * Stoppt die Animation der Punkte und setzt den Zähler zurück.
     */
    private void stopDotAnimation() {
        if (dotTimer != null && dotTimer.isRunning()) {
            dotTimer.stop();
        }

        dotCount = 0;
    }

}
