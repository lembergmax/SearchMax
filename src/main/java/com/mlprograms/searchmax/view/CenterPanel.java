package com.mlprograms.searchmax.view;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

@Getter
public final class CenterPanel extends JPanel {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> resultList = new JList<>(listModel);

    private final SearchView parent;

    public CenterPanel(SearchView parent) {
        super(new BorderLayout());
        this.parent = parent;
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(resultList), BorderLayout.CENTER);

        // TODO: das angeklickte item in der liste von den gefundenen dateien wird nicht korrekt ausgewählt
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = resultList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    resultList.setSelectedIndex(index);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !resultList.isSelectionEmpty()) {
                    openSelected();
                }
            }
        });

        resultList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "open");
        resultList.getActionMap().put("open", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!resultList.isSelectionEmpty()) {
                    openSelected();
                }
            }
        });
    }

    private void openSelected() {
        final String value = resultList.getSelectedValue();
        if (value == null || value.isBlank()) return;

        // Format: "[time] absolutePath" -> Pfad nach "] "
        final int idx = value.indexOf("] ");
        final String path = idx >= 0 && idx + 2 < value.length() ? value.substring(idx + 2) : value;
        try {
            final File f = new File(path);
            if (f.exists()) {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(f);
                } else {
                    // Fallback: Im Explorer markieren (Windows)
                    Runtime.getRuntime().exec(new String[]{"explorer.exe", "/select,", f.getAbsolutePath()});
                }
            } else {
                JOptionPane.showMessageDialog(this, GuiConstants.MSG_FILE_NOT_FOUND_PREFIX + path, GuiConstants.MSG_ERROR_TITLE_GERMAN, JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_CANNOT_OPEN_FILE_PREFIX + ex.getMessage(), GuiConstants.MSG_ERROR_TITLE_GERMAN, JOptionPane.ERROR_MESSAGE);
        }
    }

}
