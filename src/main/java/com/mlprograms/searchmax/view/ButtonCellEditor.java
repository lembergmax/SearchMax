package com.mlprograms.searchmax.view;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.IntConsumer;

public final class ButtonCellEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {

    private final JButton button = new JButton(GuiConstants.COLUMN_REMOVE);
    private final IntConsumer removeActionConsumer;
    private int currentEditingRow = -1;

    public ButtonCellEditor(final IntConsumer removeActionConsumer) {
        this.removeActionConsumer = removeActionConsumer;
        initializeButton();
    }

    private void initializeButton() {
        button.addActionListener(this);
        button.setFocusPainted(false);
    }

    @Override
    public Component getTableCellEditorComponent(final JTable table, final Object value,
                                                 final boolean isSelected, final int row,
                                                 final int column) {
        this.currentEditingRow = row;
        final String buttonText = value == null ? GuiConstants.COLUMN_REMOVE : value.toString();
        button.setText(buttonText);
        return button;
    }

    @Override
    public Object getCellEditorValue() {
        return button.getText();
    }

    @Override
    public void actionPerformed(final ActionEvent actionEvent) {
        if (removeActionConsumer != null && currentEditingRow >= 0) {
            removeActionConsumer.accept(currentEditingRow);
        }
        fireEditingStopped();
        currentEditingRow = -1;
    }

    @Override
    public boolean stopCellEditing() {
        currentEditingRow = -1;
        return super.stopCellEditing();
    }

    @Override
    public void cancelCellEditing() {
        currentEditingRow = -1;
        super.cancelCellEditing();
    }

}