package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.ExtractionMode;

import javax.swing.*;
import java.awt.*;

public class ExtractionSettingsDialog extends JDialog {

    private ExtractionMode selected = null;

    public ExtractionSettingsDialog(Window owner, ExtractionMode current) {
        super(owner, "Extraction settings", ModalityType.APPLICATION_MODAL);
        initUI(current);
    }

    private void initUI(ExtractionMode current) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel radios = new JPanel(new GridLayout(0, 1, 4, 4));
        JRadioButton poiOnly = new JRadioButton("POI only (fast for Office)");
        JRadioButton tikaOnly = new JRadioButton("Tika only (broad fallback)");
        JRadioButton poiThenTika = new JRadioButton("POI then Tika (recommended)");

        ButtonGroup group = new ButtonGroup();
        group.add(poiOnly);
        group.add(tikaOnly);
        group.add(poiThenTika);

        radios.add(poiThenTika);
        radios.add(poiOnly);
        radios.add(tikaOnly);

        switch (current) {
            case POI_ONLY -> poiOnly.setSelected(true);
            case TIKA_ONLY -> tikaOnly.setSelected(true);
            case POI_THEN_TIKA -> poiThenTika.setSelected(true);
            default -> poiThenTika.setSelected(true);
        }

        panel.add(radios, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        buttons.add(cancel);
        buttons.add(ok);
        panel.add(buttons, BorderLayout.SOUTH);

        ok.addActionListener(e -> {
            if (poiOnly.isSelected()) selected = ExtractionMode.POI_ONLY;
            else if (tikaOnly.isSelected()) selected = ExtractionMode.TIKA_ONLY;
            else selected = ExtractionMode.POI_THEN_TIKA;
            setVisible(false);
        });

        cancel.addActionListener(e -> {
            selected = null;
            setVisible(false);
        });

        setContentPane(panel);
        pack();
        setLocationRelativeTo(getOwner());
    }

    public ExtractionMode getSelected() {
        return selected;
    }
}

