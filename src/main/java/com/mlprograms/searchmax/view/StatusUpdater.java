package com.mlprograms.searchmax.view;

import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.util.List;

@RequiredArgsConstructor
public final class StatusUpdater {

    private static final String SEARCH_RUNNING_TEXT = GuiConstants.SEARCH_RUNNING_TEXT;
    private static final String RESULTS_PROPERTY = "results";
    private static final String RESULT_ADDED_PROPERTY = "resultAdded";
    private static final String RESULTS_BATCH_PROPERTY = "resultsBatch";
    private static final String STATUS_PROPERTY = "status";
    private static final int DOT_ANIMATION_DELAY_MS = 250;
    private static final int MAX_DOT_COUNT = 4;

    private final SearchView searchView;
    private Timer dotAnimationTimer;
    private int dotAnimationCount = 0;

    public void onModelChange(final PropertyChangeEvent propertyChangeEvent) {
        final String propertyName = propertyChangeEvent.getPropertyName();

        SwingUtilities.invokeLater(() -> {
            switch (propertyName) {
                case RESULTS_PROPERTY -> handleResultsUpdate(propertyChangeEvent);
                case RESULT_ADDED_PROPERTY -> handleSingleResultAdded(propertyChangeEvent);
                case RESULTS_BATCH_PROPERTY -> handleResultsBatchUpdate(propertyChangeEvent);
                case STATUS_PROPERTY -> handleStatusUpdate(propertyChangeEvent);
                default -> logUnknownProperty(propertyName);
            }
        });
    }

    private void handleResultsUpdate(final PropertyChangeEvent propertyChangeEvent) {
        final Object oldValue = propertyChangeEvent.getOldValue();
        final Object newValue = propertyChangeEvent.getNewValue();
        final DefaultListModel<String> listModel = searchView.getCenterPanel().getListModel();

        if (oldValue instanceof List<?> oldList && newValue instanceof List<?> newList) {
            updateListModelIncremental(listModel, oldList, newList);
        } else if (newValue instanceof List<?> newList) {
            replaceListModelContents(listModel, newList);
        }
    }

    private void updateListModelIncremental(final DefaultListModel<String> listModel,
                                            final List<?> oldList, final List<?> newList) {
        final int oldSize = oldList.size();
        final int newSize = newList.size();

        if (newSize < oldSize) {
            listModel.clear();
            addAllStringElementsToListModel(listModel, newList);
        } else {
            addNewStringElementsToListModel(listModel, oldSize, newList);
        }
    }

    private void replaceListModelContents(final DefaultListModel<String> listModel, final List<?> newList) {
        listModel.clear();
        addAllStringElementsToListModel(listModel, newList);
    }

    private void addAllStringElementsToListModel(final DefaultListModel<String> listModel, final List<?> sourceList) {
        for (final Object element : sourceList) {
            if (element instanceof String stringElement) {
                listModel.addElement(stringElement);
            }
        }
    }

    private void addNewStringElementsToListModel(final DefaultListModel<String> listModel,
                                                 final int startIndex, final List<?> sourceList) {
        for (int index = startIndex; index < sourceList.size(); index++) {
            final Object element = sourceList.get(index);
            if (element instanceof String stringElement) {
                listModel.addElement(stringElement);
            }
        }
    }

    private void handleSingleResultAdded(final PropertyChangeEvent propertyChangeEvent) {
        final Object newValue = propertyChangeEvent.getNewValue();
        if (newValue instanceof String resultString) {
            searchView.getCenterPanel().getListModel().addElement(resultString);
        }
    }

    private void handleResultsBatchUpdate(final PropertyChangeEvent propertyChangeEvent) {
        final Object newValue = propertyChangeEvent.getNewValue();
        if (newValue instanceof List<?> resultsList) {
            final DefaultListModel<String> listModel = searchView.getCenterPanel().getListModel();
            addAllStringElementsToListModel(listModel, resultsList);
        }
    }

    private void handleStatusUpdate(final PropertyChangeEvent propertyChangeEvent) {
        final String newStatus = (String) propertyChangeEvent.getNewValue();
        updateStatusDisplay(newStatus);
    }

    private void logUnknownProperty(final String propertyName) {
        System.out.println("Unbekannte Property geändert: " + propertyName);
    }

    private void updateStatusDisplay(final String newStatusText) {
        final boolean isSearchRunning = newStatusText != null && newStatusText.startsWith(SEARCH_RUNNING_TEXT);

        if (isSearchRunning) {
            startDotAnimation();
        } else {
            stopDotAnimation();
        }

        searchView.getBottomPanel().updateStatus(newStatusText);
        searchView.updateButtonStates(isSearchRunning);
    }

    private void startDotAnimation() {
        if (dotAnimationTimer == null) {
            dotAnimationTimer = createDotAnimationTimer();
        }

        if (!dotAnimationTimer.isRunning()) {
            dotAnimationTimer.start();
        }
    }

    private Timer createDotAnimationTimer() {
        final Timer timer = new Timer(DOT_ANIMATION_DELAY_MS, actionEvent -> {
            dotAnimationCount = (dotAnimationCount + 1) % MAX_DOT_COUNT;
            final String animatedStatusText = SEARCH_RUNNING_TEXT + ".".repeat(dotAnimationCount);
            searchView.getBottomPanel().updateStatus(animatedStatusText);
        });

        timer.setInitialDelay(0);
        return timer;
    }

    private void stopDotAnimation() {
        if (dotAnimationTimer != null && dotAnimationTimer.isRunning()) {
            dotAnimationTimer.stop();
        }

        dotAnimationCount = 0;
    }

}