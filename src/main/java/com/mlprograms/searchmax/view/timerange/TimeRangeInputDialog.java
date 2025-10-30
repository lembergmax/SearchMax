package com.mlprograms.searchmax.view.timerange;

import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.TimePicker;
import com.mlprograms.searchmax.model.TimeRangeTableModel;
import com.mlprograms.searchmax.view.GuiConstants;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

@Getter
public class TimeRangeInputDialog extends JDialog {

    private static final int DATE_PICKER_WIDTH = 280;
    private static final int TIME_PICKER_WIDTH = 140;
    private static final int PICKER_HEIGHT = 28;
    private static final int DIALOG_EXTRA_WIDTH = 240;
    private static final int DIALOG_EXTRA_HEIGHT = 50;

    private TimeRangeInputResult result = null;

    public TimeRangeInputDialog(final Frame owner) {
        super(owner, GuiConstants.INPUT_ADD_TIME_TITLE, true);
        initializeDialog();
    }

    private void initializeDialog() {
        final JPanel contentPanel = createContentPanel();
        final JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        final JOptionPane optionPane = new JOptionPane(scrollPane, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);

        setContentPane(optionPane);
        configureDialog();
        setupResultHandler(optionPane);
    }

    private JPanel createContentPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        // Mode selection
        final JLabel modeLabel = new JLabel(GuiConstants.LABEL_MODE);
        final String[] modes = {GuiConstants.MODE_TIME, GuiConstants.MODE_DATE, GuiConstants.MODE_DATETIME};
        final JComboBox<String> modeComboBox = new JComboBox<>(modes);

        // Date and time pickers
        final DatePicker startDatePicker = createDatePicker();
        final DatePicker endDatePicker = createDatePicker();
        final TimePicker startTimePicker = createTimePicker();
        final TimePicker endTimePicker = createTimePicker();

        // Set default values
        setDefaultDateTimeValues(startDatePicker, endDatePicker, startTimePicker, endTimePicker);

        // Create combined panels
        final JPanel startPanel = createDateTimePanel(startDatePicker, startTimePicker);
        final JPanel endPanel = createDateTimePanel(endDatePicker, endTimePicker);

        // Layout components
        // Mode label (no horizontal expansion)
        constraints.weightx = 0.0;
        addComponentToPanel(panel, modeLabel, constraints, 0, 0, 1, 1);
        // Mode combo (expand)
        constraints.weightx = 1.0;
        addComponentToPanel(panel, modeComboBox, constraints, 1, 0, 2, 1);
        // From label (no expansion)
        constraints.weightx = 0.0;
        addComponentToPanel(panel, new JLabel(GuiConstants.LABEL_FROM), constraints, 0, 1, 1, 1);
        // Start panel (expand)
        constraints.weightx = 1.0;
        addComponentToPanel(panel, startPanel, constraints, 1, 1, 2, 1);
        // To label (no expansion)
        constraints.weightx = 0.0;
        addComponentToPanel(panel, new JLabel(GuiConstants.LABEL_TO), constraints, 0, 2, 1, 1);
        // End panel (expand)
        constraints.weightx = 1.0;
        addComponentToPanel(panel, endPanel, constraints, 1, 2, 2, 1);

        // Setup mode change listener
        setupModeChangeListener(modeComboBox, startDatePicker, endDatePicker, startTimePicker, endTimePicker, panel);

        return panel;
    }

    private DatePicker createDatePicker() {
        final DatePicker datePicker = new DatePicker();
        datePicker.setPreferredSize(new Dimension(DATE_PICKER_WIDTH, PICKER_HEIGHT));
        return datePicker;
    }

    private TimePicker createTimePicker() {
        final TimePicker timePicker = new TimePicker();
        timePicker.setPreferredSize(new Dimension(TIME_PICKER_WIDTH, PICKER_HEIGHT));
        return timePicker;
    }

    private void setDefaultDateTimeValues(final DatePicker startDatePicker, final DatePicker endDatePicker,
                                          final TimePicker startTimePicker, final TimePicker endTimePicker) {
        final LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        startTimePicker.setTime(now);
        endTimePicker.setTime(now.plusHours(1));
        final LocalDate today = LocalDate.now();
        startDatePicker.setDate(today);
        endDatePicker.setDate(today);
    }

    private JPanel createDateTimePanel(final DatePicker datePicker, final TimePicker timePicker) {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.add(datePicker);
        panel.add(timePicker);
        return panel;
    }

    private void addComponentToPanel(final JPanel panel, final JComponent component,
                                     final GridBagConstraints constraints, final int x, final int y,
                                     final int width, final int height) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = width;
        constraints.gridheight = height;
        panel.add(component, constraints);
    }

    private void setupModeChangeListener(final JComboBox<String> modeComboBox,
                                         final DatePicker startDatePicker, final DatePicker endDatePicker,
                                         final TimePicker startTimePicker, final TimePicker endTimePicker,
                                         final JPanel contentPanel) {

        final Runnable updateVisibility = () -> {
            final String selectedMode = (String) modeComboBox.getSelectedItem();
            final boolean showDate = !GuiConstants.MODE_TIME.equals(selectedMode);
            final boolean showTime = !GuiConstants.MODE_DATE.equals(selectedMode);

            startDatePicker.setVisible(showDate);
            endDatePicker.setVisible(showDate);
            startTimePicker.setVisible(showTime);
            endTimePicker.setVisible(showTime);

            contentPanel.revalidate();
            contentPanel.repaint();
        };

        modeComboBox.addActionListener(actionEvent -> updateVisibility.run());
        updateVisibility.run();
    }

    private void configureDialog() {
        pack();
        setResizable(true);
        setLocationRelativeTo(getOwner());

        // Adjust size to ensure proper visibility
        final Dimension preferredSize = getPreferredSize();
        // Erhöhe die Mindestbreite und -höhe, damit die Date/Time-Picker genug Platz haben
        final int minWidth = Math.max(720, preferredSize.width + DIALOG_EXTRA_WIDTH);
        final int minHeight = Math.max(320, preferredSize.height + DIALOG_EXTRA_HEIGHT);
        setSize(minWidth, minHeight);
    }

    private void setupResultHandler(final JOptionPane optionPane) {
        optionPane.addPropertyChangeListener(propertyChangeEvent -> {
            if (JOptionPane.VALUE_PROPERTY.equals(propertyChangeEvent.getPropertyName())) {
                final Object value = optionPane.getValue();
                if (value instanceof Integer) {
                    final int resultValue = (Integer) value;
                    if (resultValue == JOptionPane.OK_OPTION) {
                        processInput(optionPane);
                    } else {
                        result = null;
                    }
                    setVisible(false);
                }
            }
        });
    }

    private void processInput(final JOptionPane optionPane) {
        @SuppressWarnings("unchecked") final JComboBox<String> modeComboBox = (JComboBox<String>) findComponentInPane(optionPane, JComboBox.class);
        final java.util.List<DatePicker> datePickers = findComponentsInPane(optionPane, DatePicker.class);
        final java.util.List<TimePicker> timePickers = findComponentsInPane(optionPane, TimePicker.class);

        final DatePicker startDatePicker = !datePickers.isEmpty() ? datePickers.get(0) : null;
        final DatePicker endDatePicker = datePickers.size() > 1 ? datePickers.get(1) : null;
        final TimePicker startTimePicker = !timePickers.isEmpty() ? timePickers.get(0) : null;
        final TimePicker endTimePicker = timePickers.size() > 1 ? timePickers.get(1) : null;

        if (modeComboBox != null && startDatePicker != null && endDatePicker != null &&
                startTimePicker != null && endTimePicker != null) {

            result = createTimeRangeResult(modeComboBox, startDatePicker, endDatePicker,
                    startTimePicker, endTimePicker);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T findComponentInPane(final Container container, final Class<T> componentClass) {
        for (final Component component : container.getComponents()) {
            if (componentClass.isInstance(component)) {
                return (T) component;
            }
            if (component instanceof Container) {
                final T found = findComponentInPane((Container) component, componentClass);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> java.util.List<T> findComponentsInPane(final Container container, final Class<T> componentClass) {
        final java.util.List<T> result = new java.util.ArrayList<>();
        for (final Component component : container.getComponents()) {
            if (componentClass.isInstance(component)) {
                result.add((T) component);
            }
            if (component instanceof Container) {
                result.addAll(findComponentsInPane((Container) component, componentClass));
            }
        }
        return result;
    }

    private TimeRangeInputResult createTimeRangeResult(final JComboBox<String> modeComboBox,
                                                       final DatePicker startDatePicker,
                                                       final DatePicker endDatePicker,
                                                       final TimePicker startTimePicker,
                                                       final TimePicker endTimePicker) {

        final String selectedMode = (String) modeComboBox.getSelectedItem();
        final TimeRangeTableModel.Mode mode = determineTimeRangeMode(selectedMode);

        final Date startDate = createDateFromPickers(startDatePicker, startTimePicker, mode, true);
        final Date endDate = createDateFromPickers(endDatePicker, endTimePicker, mode, false);

        if (startDate == null || endDate == null || startDate.after(endDate)) {
            JOptionPane.showMessageDialog(this, GuiConstants.MSG_INVALID_TIME_RANGE,
                    GuiConstants.MSG_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            return null;
        }

        return new TimeRangeInputResult(startDate, endDate, mode);
    }

    private TimeRangeTableModel.Mode determineTimeRangeMode(final String modeString) {
        if (GuiConstants.MODE_DATE.equals(modeString)) {
            return TimeRangeTableModel.Mode.DATE;
        } else if (GuiConstants.MODE_DATETIME.equals(modeString)) {
            return TimeRangeTableModel.Mode.DATETIME;
        } else {
            return TimeRangeTableModel.Mode.TIME;
        }
    }

    private Date createDateFromPickers(final DatePicker datePicker, final TimePicker timePicker,
                                       final TimeRangeTableModel.Mode mode, final boolean isStart) {
        final ZoneId zoneId = ZoneId.systemDefault();

        switch (mode) {
            case TIME:
                final LocalTime time = timePicker.getTime();
                if (time != null) {
                    final LocalDateTime dateTime = LocalDateTime.of(LocalDate.of(1970, 1, 1), time);
                    return Date.from(dateTime.atZone(zoneId).toInstant());
                }
                break;

            case DATE:
                final LocalDate date = datePicker.getDate();
                if (date != null) {
                    LocalDateTime dateTime = date.atStartOfDay();
                    if (!isStart) {
                        dateTime = date.atTime(23, 59, 59, 999_000_000);
                    }
                    return Date.from(dateTime.atZone(zoneId).toInstant());
                }
                break;

            case DATETIME:
                final LocalDate datePart = datePicker.getDate();
                final LocalTime timePart = timePicker.getTime();
                if (datePart != null && timePart != null) {
                    final LocalDateTime dateTime = LocalDateTime.of(datePart, timePart);
                    return Date.from(dateTime.atZone(zoneId).toInstant());
                }
                break;
        }

        return null;
    }

}