package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.controller.SearchController;
import com.mlprograms.searchmax.model.SearchModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

public class SearchView extends JFrame {

    private final SearchController controller;
    private final SearchModel model;

    private final JTextField folderField = new JTextField(30);
    private final JButton browseButton = new JButton("Durchsuchen...");
    private final JTextField queryField = new JTextField(20);
    private final JButton searchButton = new JButton("Suche");
    private final JButton cancelButton = new JButton("Abbrechen");
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> resultList = new JList<>(listModel);
    private final JLabel statusLabel = new JLabel("Bereit");
    private final JLabel idLabel = new JLabel("-");

    public SearchView(SearchController controller, SearchModel model) {
        super("SearchMax - Desktop");
        this.controller = controller;
        this.model = model;
        initUI();
        bindModel();
    }

    private void initUI() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        top.add(new JLabel("Ordner"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        top.add(folderField, c);
        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        top.add(browseButton, c);

        c.gridx = 0; c.gridy = 1; top.add(new JLabel("Suchtext"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; top.add(queryField, c);
        c.gridx = 2; c.fill = GridBagConstraints.NONE; JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT)); btnPanel.add(searchButton); btnPanel.add(cancelButton); top.add(btnPanel, c);

        JPanel center = new JPanel(new BorderLayout());
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        center.add(new JScrollPane(resultList), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT));
        info.add(new JLabel("Status: ")); info.add(statusLabel); info.add(Box.createHorizontalStrut(16)); info.add(new JLabel("Id: ")); info.add(idLabel);
        bottom.add(info, BorderLayout.WEST);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(center, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);

        browseButton.addActionListener(e -> onBrowse());
        searchButton.addActionListener(e -> onSearch());
        cancelButton.addActionListener(e -> onCancel());

        resultList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int idx = resultList.locationToIndex(evt.getPoint());
                    if (idx >= 0) {
                        String val = listModel.getElementAt(idx);
                        int pos = val.indexOf(']');
                        if (pos >= 0 && pos + 2 < val.length()) {
                            String path = val.substring(pos + 2);
                            try {
                                Desktop.getDesktop().open(new File(path));
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(SearchView.this, "Datei kann nicht geöffnet werden: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                }
            }
        });

        updateButtons(false);
    }

    private void bindModel() {
        model.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                switch (evt.getPropertyName()) {
                    case SearchModel.PROP_RESULTS:
                        java.util.List<String> newList = model.getResults();
                        SwingUtilities.invokeLater(() -> {
                            listModel.clear();
                            for (String s : newList) {
                                listModel.addElement(s);
                            }
                        });
                        break;
                    case SearchModel.PROP_STATUS:
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText((String) evt.getNewValue());
                            boolean running = "Suche läuft...".equals(evt.getNewValue());
                            updateButtons(running);
                        });
                        break;
                    case SearchModel.PROP_ID:
                        SwingUtilities.invokeLater(() -> idLabel.setText(evt.getNewValue() == null ? "-" : evt.getNewValue().toString()));
                        break;
                }
            }
        });
    }

    private void updateButtons(boolean running) {
        searchButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        folderField.setEnabled(!running);
        queryField.setEnabled(!running);
        browseButton.setEnabled(!running);
    }

    private void onBrowse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int ret = chooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            folderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onSearch() {
        String folder = folderField.getText();
        String q = queryField.getText();
        if (folder == null || folder.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte einen Startordner angeben.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (q == null || q.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte einen Suchtext angeben.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
            return;
        }
        controller.startSearch(folder.trim(), q.trim());
    }

    private void onCancel() {
        controller.cancelSearch();
    }

}

