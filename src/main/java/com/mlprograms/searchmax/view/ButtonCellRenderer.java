package com.mlprograms.searchmax.view;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ButtonCellRenderer extends JButton implements TableCellRenderer {
    public ButtonCellRenderer() {
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setText(value == null ? "" : value.toString());
        setFont(table.getFont());
        return this;
    }
}

