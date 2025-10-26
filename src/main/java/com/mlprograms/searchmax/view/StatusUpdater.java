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
                searchView.getCenterPanel().getListModel().clear();

                final Object newValue = propertyChangeEvent.getNewValue();
                if (newValue instanceof java.util.List<?> list) {
                    for (final Object object : list) {
                        if (object instanceof String string) {
                            searchView.getCenterPanel().getListModel().addElement(string);
                        }
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
