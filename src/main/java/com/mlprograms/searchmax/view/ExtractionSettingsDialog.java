package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.ExtractionMode;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

@Getter
public final class ExtractionSettingsDialog extends JDialog {

    private static final int DIALOG_WIDTH = 480;
    private static final int DIALOG_HEIGHT = 320;
    private static final int VERTICAL_STRUT_SIZE = 4;
    private static final int SECTION_SPACING = 8;
    private static final int LAYOUT_GAP = 8;

    private ExtractionMode selectedExtractionMode = null;
    private final JPanel sectionsContainerPanel = new JPanel();
    private final ButtonGroup extractionModeButtonGroup = new ButtonGroup();

    public ExtractionSettingsDialog(final Window owner, final ExtractionMode currentExtractionMode) {
        super(owner, GuiConstants.TITLE_EXTRACTION_SETTINGS, ModalityType.APPLICATION_MODAL);
        initializeUserInterface(currentExtractionMode);
    }

    private void initializeUserInterface(final ExtractionMode currentExtractionMode) {
        final JPanel rootPanel = createRootPanel();
        initializeSectionsContainer();

        final JPanel extractionSettingsPanel = createExtractionSettingsPanel(currentExtractionMode);
        addSectionComponent(extractionSettingsPanel);

        rootPanel.add(new JScrollPane(sectionsContainerPanel), BorderLayout.CENTER);
        rootPanel.add(createButtonPanel(), BorderLayout.SOUTH);

        setContentPane(rootPanel);
        configureDialogSettings();
    }

    private JPanel createRootPanel() {
        final JPanel panel = new JPanel(new BorderLayout(LAYOUT_GAP, LAYOUT_GAP));
        panel.setBorder(BorderFactory.createEmptyBorder(LAYOUT_GAP, LAYOUT_GAP, LAYOUT_GAP, LAYOUT_GAP));
        return panel;
    }

    private void initializeSectionsContainer() {
        sectionsContainerPanel.setLayout(new BoxLayout(sectionsContainerPanel, BoxLayout.Y_AXIS));
    }

    private JPanel createExtractionSettingsPanel(final ExtractionMode currentExtractionMode) {
        final JPanel extractionPanel = new JPanel();
        extractionPanel.setLayout(new BoxLayout(extractionPanel, BoxLayout.Y_AXIS));
        extractionPanel.setBorder(createTitledBorder(GuiConstants.SECTION_EXTRACTION));

        final JRadioButton poiOnlyRadioButton = createRadioButton(GuiConstants.RADIO_POI_ONLY, ExtractionMode.POI_ONLY);
        final JRadioButton tikaOnlyRadioButton = createRadioButton(GuiConstants.RADIO_TIKA_ONLY, ExtractionMode.TIKA_ONLY);
        final JRadioButton poiThenTikaRadioButton = createRadioButton(GuiConstants.RADIO_POI_THEN_TIKA, ExtractionMode.POI_THEN_TIKA);

        addRadioButtonsToGroup(poiOnlyRadioButton, tikaOnlyRadioButton, poiThenTikaRadioButton);
        addRadioButtonsToPanel(extractionPanel, poiThenTikaRadioButton, poiOnlyRadioButton, tikaOnlyRadioButton);
        setInitialSelection(currentExtractionMode, poiOnlyRadioButton, tikaOnlyRadioButton, poiThenTikaRadioButton);

        return extractionPanel;
    }

    private TitledBorder createTitledBorder(final String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP
        );
    }

    private JRadioButton createRadioButton(final String text, final ExtractionMode extractionMode) {
        final JRadioButton radioButton = new JRadioButton(text);
        radioButton.setActionCommand(extractionMode.name());
        return radioButton;
    }

    private void addRadioButtonsToGroup(final JRadioButton... radioButtons) {
        for (final JRadioButton radioButton : radioButtons) {
            extractionModeButtonGroup.add(radioButton);
        }
    }

    private void addRadioButtonsToPanel(final JPanel panel, final JRadioButton... radioButtons) {
        for (int i = 0; i < radioButtons.length; i++) {
            panel.add(radioButtons[i]);
            if (i < radioButtons.length - 1) {
                panel.add(Box.createVerticalStrut(VERTICAL_STRUT_SIZE));
            }
        }
    }

    private void setInitialSelection(final ExtractionMode currentExtractionMode,
                                     final JRadioButton poiOnlyRadioButton,
                                     final JRadioButton tikaOnlyRadioButton,
                                     final JRadioButton poiThenTikaRadioButton) {
        switch (currentExtractionMode) {
            case POI_ONLY -> poiOnlyRadioButton.setSelected(true);
            case TIKA_ONLY -> tikaOnlyRadioButton.setSelected(true);
            case POI_THEN_TIKA -> poiThenTikaRadioButton.setSelected(true);
            default -> poiThenTikaRadioButton.setSelected(true);
        }
    }

    private JPanel createButtonPanel() {
        final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        final JButton cancelButton = new JButton(GuiConstants.BUTTON_CANCEL);
        final JButton confirmButton = new JButton(GuiConstants.BUTTON_OK);

        buttonPanel.add(cancelButton);
        buttonPanel.add(confirmButton);

        initializeButtonListeners(cancelButton, confirmButton);

        return buttonPanel;
    }

    private void initializeButtonListeners(final JButton cancelButton, final JButton confirmButton) {
        cancelButton.addActionListener(actionEvent -> {
            selectedExtractionMode = null;
            setVisible(false);
        });

        confirmButton.addActionListener(actionEvent -> {
            final String selectedActionCommand = extractionModeButtonGroup.getSelection().getActionCommand();
            selectedExtractionMode = ExtractionMode.valueOf(selectedActionCommand);
            setVisible(false);
        });
    }

    private void addSectionComponent(final JComponent component) {
        final JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(component, BorderLayout.CENTER);
        wrapperPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionsContainerPanel.add(wrapperPanel);
        sectionsContainerPanel.add(Box.createVerticalStrut(SECTION_SPACING));
    }

    private void configureDialogSettings() {
        setPreferredSize(new Dimension(DIALOG_WIDTH, DIALOG_HEIGHT));
        pack();
        setLocationRelativeTo(getOwner());
        setResizable(true);
    }

}