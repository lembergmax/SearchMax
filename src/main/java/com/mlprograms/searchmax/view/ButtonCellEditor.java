package com.mlprograms.searchmax.view;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.IntConsumer;

public class ButtonCellEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
    private final JButton button = new JButton("Entfernen");
    private final IntConsumer removeAtConsumer;
    private int editingRow = -1;

    public ButtonCellEditor(IntConsumer removeAtConsumer) {
        this.removeAtConsumer = removeAtConsumer;
        button.addActionListener(this);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        this.editingRow = row;
        return button;
    }

    @Override
    public Object getCellEditorValue() {
        return button.getText();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (removeAtConsumer != null && editingRow >= 0) {
            removeAtConsumer.accept(editingRow);
        }
        fireEditingStopped();
        editingRow = -1;
    }
}
