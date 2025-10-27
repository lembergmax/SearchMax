package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.view.logging.InMemoryLogAppender;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/**
 * Einfacher Log-Viewer, der Logs aus dem InMemoryLogAppender anzeigt.
 */
public class LogViewer extends JFrame {

    private final JTextArea textArea = new JTextArea();
    private final InMemoryLogAppender appender;
    private final Consumer<String> listener = this::onNewLogLine;

    public LogViewer(InMemoryLogAppender appender) {
        super("Logs");
        this.appender = appender;
        initUI();
        loadExisting();
        appender.addListener(listener);
    }

    private void initUI() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(textArea);
        getContentPane().add(scroll, BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> {
            appender.clear();
            SwingUtilities.invokeLater(() -> textArea.setText(""));
        });
        top.add(clear);
        getContentPane().add(top, BorderLayout.NORTH);

        setSize(700, 400);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                appender.removeListener(listener);
            }
        });
    }

    private void loadExisting() {
        StringBuilder sb = new StringBuilder();
        for (String line : appender.getAll()) {
            sb.append(line);
        }
        textArea.setText(sb.toString());
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }

    private void onNewLogLine(String line) {
        // ensure executed on EDT
        SwingUtilities.invokeLater(() -> {
            textArea.append(line);
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

}
