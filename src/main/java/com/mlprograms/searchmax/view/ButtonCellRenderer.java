package com.mlprograms.searchmax.view;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public final class ButtonCellRenderer extends JButton implements TableCellRenderer {

    public ButtonCellRenderer() {
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                   final boolean isSelected, final boolean hasFocus,
                                                   final int row, final int column) {
        setText(value == null ? GuiConstants.COLUMN_REMOVE : value.toString());
        setFont(table.getFont());
        configureAppearanceBasedOnSelection(table, isSelected);
        return this;
    }

    private void configureAppearanceBasedOnSelection(final JTable table, final boolean isSelected) {
        if (isSelected) {
            setForeground(table.getSelectionForeground());
            setBackground(table.getSelectionBackground());
        } else {
            setForeground(table.getForeground());
            setBackground(table.getBackground());
        }
    }

}