package com.mlprograms.searchmax.view;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;

@Getter
public final class BottomPanel extends JPanel {

    private final JLabel statusLabel = new JLabel("Bereit");
    private final JLabel idLabel = new JLabel("-");

    public BottomPanel() {
        super(new BorderLayout());
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT));
        info.add(new JLabel("Status: "));
        info.add(statusLabel);
        info.add(Box.createHorizontalStrut(16));
        info.add(new JLabel("Id: "));
        info.add(idLabel);
        add(info, BorderLayout.WEST);
    }

}
