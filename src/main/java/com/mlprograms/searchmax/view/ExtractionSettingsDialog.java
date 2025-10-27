package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.ExtractionMode;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class ExtractionSettingsDialog extends JDialog {

    private ExtractionMode selected = null;
    private final JPanel sectionsPanel = new JPanel();

    public ExtractionSettingsDialog(Window owner, ExtractionMode current) {
        super(owner, "Text extraction settings", ModalityType.APPLICATION_MODAL);
        initUI(current);
    }

    private void initUI(ExtractionMode current) {
        // Root panel uses BorderLayout
        JPanel root = new JPanel(new BorderLayout(8, 8));
        sectionsPanel.setLayout(new BoxLayout(sectionsPanel, BoxLayout.Y_AXIS));

        // --- Extraction section ---
        JPanel extractionPanel = new JPanel();
        extractionPanel.setLayout(new BoxLayout(extractionPanel, BoxLayout.Y_AXIS));
        extractionPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Extraction", TitledBorder.LEFT, TitledBorder.TOP));

        JRadioButton poiOnly = new JRadioButton("POI only (fast for Office)");
        JRadioButton tikaOnly = new JRadioButton("Tika only (broad fallback)");
        JRadioButton poiThenTika = new JRadioButton("POI then Tika (recommended)");

        ButtonGroup group = new ButtonGroup();
        group.add(poiOnly);
        group.add(tikaOnly);
        group.add(poiThenTika);

        extractionPanel.add(poiThenTika);
        extractionPanel.add(Box.createVerticalStrut(4));
        extractionPanel.add(poiOnly);
        extractionPanel.add(Box.createVerticalStrut(4));
        extractionPanel.add(tikaOnly);

        switch (current) {
            case POI_ONLY -> poiOnly.setSelected(true);
            case TIKA_ONLY -> tikaOnly.setSelected(true);
            case POI_THEN_TIKA -> poiThenTika.setSelected(true);
            default -> poiThenTika.setSelected(true);
        }

        addSectionComponent(extractionPanel);

        root.add(new JScrollPane(sectionsPanel), BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        buttons.add(cancel);
        buttons.add(ok);
        root.add(buttons, BorderLayout.SOUTH);

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

        setContentPane(root);
        setPreferredSize(new Dimension(480, 320));
        pack();
        setLocationRelativeTo(getOwner());
    }

    // Internal helper to add a full component as a section (no extra border)
    private void addSectionComponent(JComponent comp) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(comp, BorderLayout.CENTER);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionsPanel.add(wrapper);
        sectionsPanel.add(Box.createVerticalStrut(8));
    }

    // Public method to add a titled section so callers can extend the dialog
    public void addSection(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP));
        panel.add(content, BorderLayout.CENTER);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionsPanel.add(panel);
        sectionsPanel.add(Box.createVerticalStrut(8));
        // refresh dialog size if already visible
        if (isVisible()) {
            revalidate();
            repaint();
            pack();
        }
    }

    public ExtractionMode getSelected() {
        return selected;
    }
}
