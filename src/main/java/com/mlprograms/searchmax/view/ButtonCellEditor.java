package com.mlprograms.searchmax.view;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ButtonCellEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
    private final JButton button = new JButton("Entfernen");
    private final Runnable removeAction;

    public ButtonCellEditor(Runnable removeAction) {
        this.removeAction = removeAction;
        button.addActionListener(this);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        return button;
    }

    @Override
    public Object getCellEditorValue() {
        return button.getText();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (removeAction != null) {
            removeAction.run();
        }
        fireEditingStopped();
    }
}
