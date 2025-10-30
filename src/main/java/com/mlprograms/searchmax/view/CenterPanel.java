package com.mlprograms.searchmax.view;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

@Getter
public final class CenterPanel extends JPanel {

    private static final Font RESULTS_LIST_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final String OPEN_ACTION_KEY = "open";

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> resultList = new JList<>(listModel);
    private final SearchView parentView;

    public CenterPanel(final SearchView parentView) {
        super(new BorderLayout());
        this.parentView = parentView;
        initializeUserInterface();
    }

    private void initializeUserInterface() {
        configureResultList();
        add(new JScrollPane(resultList), BorderLayout.CENTER);
        initializeMouseListeners();
        initializeKeyboardActions();
    }

    private void configureResultList() {
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFont(RESULTS_LIST_FONT);
    }

    private void initializeMouseListeners() {
        resultList.addMouseListener(new ResultListMouseAdapter());
    }

    private void initializeKeyboardActions() {
        resultList.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("ENTER"), OPEN_ACTION_KEY);

        resultList.getActionMap().put(OPEN_ACTION_KEY, new OpenSelectedFileAction());
    }

    private void openSelectedFile() {
        final String selectedValue = resultList.getSelectedValue();
        if (selectedValue == null || selectedValue.isBlank()) {
            return;
        }

        final String filePath = extractFilePathFromResult(selectedValue);
        try {
            openFile(filePath);
        } catch (final Exception exception) {
            showErrorMessage(GuiConstants.MSG_CANNOT_OPEN_FILE_PREFIX + exception.getMessage());
        }
    }

    private String extractFilePathFromResult(final String resultEntry) {
        final int separatorIndex = resultEntry.indexOf("] ");
        if (separatorIndex >= 0 && separatorIndex + 2 < resultEntry.length()) {
            return resultEntry.substring(separatorIndex + 2);
        }
        return resultEntry;
    }

    private void openFile(final String filePath) throws Exception {
        final File file = new File(filePath);

        if (!file.exists()) {
            showErrorMessage(GuiConstants.MSG_FILE_NOT_FOUND_PREFIX + filePath);
            return;
        }

        if (isDesktopOpenSupported()) {
            Desktop.getDesktop().open(file);
        } else {
            openFileInExplorer(file);
        }
    }

    private boolean isDesktopOpenSupported() {
        return Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
    }

    private void openFileInExplorer(final File file) throws Exception {
        Runtime.getRuntime().exec(new String[]{"explorer.exe", "/select,", file.getAbsolutePath()});
    }

    private void showErrorMessage(final String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                GuiConstants.MSG_ERROR_TITLE_GERMAN,
                JOptionPane.ERROR_MESSAGE
        );
    }

    public void clearResults() {
        listModel.clear();
    }

    public void addResult(final String result) {
        if (result != null && !result.trim().isEmpty()) {
            listModel.addElement(result);
        }
    }

    public boolean hasResults() {
        return !listModel.isEmpty();
    }

    public int getResultCount() {
        return listModel.size();
    }

    // Inner classes for event handling
    private final class ResultListMouseAdapter extends MouseAdapter {
        @Override
        public void mousePressed(final MouseEvent mouseEvent) {
            final int clickedIndex = resultList.locationToIndex(mouseEvent.getPoint());
            if (clickedIndex >= 0) {
                resultList.setSelectedIndex(clickedIndex);
            }
        }

        @Override
        public void mouseClicked(final MouseEvent mouseEvent) {
            if (mouseEvent.getClickCount() == 2 && !resultList.isSelectionEmpty()) {
                openSelectedFile();
            }
        }
    }

    private final class OpenSelectedFileAction extends AbstractAction {
        @Override
        public void actionPerformed(final java.awt.event.ActionEvent actionEvent) {
            if (!resultList.isSelectionEmpty()) {
                openSelectedFile();
            }
        }
    }

}