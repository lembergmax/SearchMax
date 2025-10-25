package com.mlprograms.searchmax.view;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;

@Getter
public final class CenterPanel extends JPanel {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> resultList = new JList<>(listModel);

    public CenterPanel() {
        super(new BorderLayout());
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(resultList), BorderLayout.CENTER);
    }

}
