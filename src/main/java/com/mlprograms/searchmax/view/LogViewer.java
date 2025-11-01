package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.view.logging.InMemoryLogAppender;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

public final class LogViewer extends JFrame {

    private static final String WINDOW_TITLE = "Logs";
    private static final Font LOG_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final int WINDOW_WIDTH = 700;
    private static final int WINDOW_HEIGHT = 400;
    private static final String CLEAR_BUTTON_TEXT = "Clear";

    private final JTextArea logTextArea = new JTextArea();
    private final InMemoryLogAppender logAppender;
    private final Consumer<String> logLineListener = this::handleNewLogLine;

    public LogViewer(final InMemoryLogAppender logAppender) {
        super(WINDOW_TITLE);
        this.logAppender = logAppender;
        initializeUserInterface();
        loadExistingLogContent();
        registerLogListener();
    }

    private void initializeUserInterface() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        configureLogTextArea();
        setupMainLayout();
        setupWindowListeners();
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);
    }

    private void configureLogTextArea() {
        logTextArea.setEditable(false);
        logTextArea.setFont(LOG_FONT);
    }

    private void setupMainLayout() {
        final JScrollPane scrollPane = createScrollPane();
        final JPanel controlPanel = createControlPanel();

        final Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(controlPanel, BorderLayout.NORTH);
        contentPane.add(scrollPane, BorderLayout.CENTER);
    }

    private JScrollPane createScrollPane() {
        return new JScrollPane(logTextArea);
    }

    private JPanel createControlPanel() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        final JButton clearLogsButton = createClearLogsButton();
        panel.add(clearLogsButton);
        return panel;
    }

    private JButton createClearLogsButton() {
        final JButton button = new JButton(CLEAR_BUTTON_TEXT);
        button.addActionListener(actionEvent -> clearLogContent());
        return button;
    }

    private void setupWindowListeners() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(final WindowEvent windowEvent) {
                unregisterLogListener();
            }
        });
    }

    private void registerLogListener() {
        logAppender.addListener(logLineListener);
    }

    private void unregisterLogListener() {
        logAppender.removeListener(logLineListener);
    }

    private void loadExistingLogContent() {
        final StringBuilder logContentBuilder = new StringBuilder();

        for (final String logLine : logAppender.getAllLogMessages()) {
            logContentBuilder.append(logLine);
        }

        setLogTextContent(logContentBuilder.toString());
        scrollToBottom();
    }

    private void setLogTextContent(final String text) {
        logTextArea.setText(text);
    }

    private void scrollToBottom() {
        logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
    }

    private void handleNewLogLine(final String logLine) {
        SwingUtilities.invokeLater(() -> {
            appendLogLine(logLine);
            scrollToBottom();
        });
    }

    private void appendLogLine(final String logLine) {
        logTextArea.append(logLine);
    }

    private void clearLogContent() {
        logAppender.clearLogBuffer();
        SwingUtilities.invokeLater(() -> {
            setLogTextContent("");
        });
    }

    public void displayLogViewer() {
        setVisible(true);
    }

    public void closeLogViewer() {
        unregisterLogListener();
        dispose();
    }

    public boolean isLogViewerVisible() {
        return isVisible();
    }

    public void bringToFront() {
        toFront();
        requestFocus();
    }

}