package com.mlprograms.searchmax.view;

import com.mlprograms.searchmax.controller.SearchController;
import com.mlprograms.searchmax.model.SearchModel;

import javax.swing.*;
import java.awt.*;
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

    // Timer und Zähler für die Punkt-Animation ("Suche läuft...")
    private javax.swing.Timer dotTimer;
    private int dotCount = 0;

    private JPanel drivePanel;
    private JCheckBox[] driveCheckBoxes;

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

        // Panel für Laufwerksauswahl
        File[] roots = File.listRoots();
        drivePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        drivePanel.setBorder(BorderFactory.createTitledBorder("Laufwerke durchsuchen"));
        driveCheckBoxes = new JCheckBox[roots.length];
        for (int i = 0; i < roots.length; i++) {
            driveCheckBoxes[i] = new JCheckBox(roots[i].getPath());
            driveCheckBoxes[i].setSelected(false); // Standard: kein Laufwerk ausgewählt
            driveCheckBoxes[i].addActionListener(e -> updateFolderFieldState());
            drivePanel.add(driveCheckBoxes[i]);
        }

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        top.add(new JLabel("Ordner"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        top.add(folderField, c);
        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        top.add(browseButton, c);
        c.gridx = 0; c.gridy = 1; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL;
        top.add(drivePanel, c);
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 2; top.add(new JLabel("Suchtext"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; top.add(queryField, c);
        c.gridx = 2; c.fill = GridBagConstraints.NONE; JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT)); btnPanel.add(searchButton); btnPanel.add(cancelButton); top.add(btnPanel, c);

        JPanel center = new JPanel(new BorderLayout());
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        center.add(new JScrollPane(resultList), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT));
        info.add(new JLabel("Status: "));
        info.add(statusLabel);
        // Hinweis: die PreferredSize für statusLabel wird nur beim Start der Punkt-Animation gesetzt,
        // damit beim Animieren der Punkte der nachfolgende Text nicht verschoben wird.
        // Wenn ein anderer (längerer/andere) Status gesetzt wird, wird die PreferredSize wieder entfernt
        // und das Layout verhält sich normal.
        info.add(Box.createHorizontalStrut(16));
        info.add(new JLabel("Id: "));
        info.add(idLabel);
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
        updateFolderFieldState();
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
                            String newStatus = (String) evt.getNewValue();
                            boolean running = "Suche läuft...".equals(newStatus);
                            // Wenn Suche läuft, Animation starten; sonst Animation stoppen und echten Status setzen
                            if (running) {
                                // Basistext ohne Punkte setzen und Animation starten
                                statusLabel.setText("Suche läuft");
                                startDotAnimation();
                            } else {
                                stopDotAnimation();
                                statusLabel.setText(newStatus);
                            }
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

    // Startet die Punkt-Animation (0..3 Punkte, zyklisch)
    private void startDotAnimation() {
        if (dotTimer == null) {
            dotTimer = new javax.swing.Timer(500, e -> {
                dotCount = (dotCount + 1) % 4; // 0..3
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotCount; i++) dots.append('.');
                statusLabel.setText("Suche läuft" + dots.toString());
            });
            dotTimer.setInitialDelay(0);
        }
        // Reserviere die Breite für den Text "Suche läuft...", damit beim Animieren die Breite konstant bleibt
        FontMetrics fm = statusLabel.getFontMetrics(statusLabel.getFont());
        int prefW = fm.stringWidth("Suche läuft...");
        int prefH = fm.getHeight();
        statusLabel.setPreferredSize(new Dimension(prefW, prefH));
        statusLabel.revalidate();
        if (statusLabel.getParent() != null) {
            statusLabel.getParent().revalidate();
            statusLabel.getParent().repaint();
        }
        dotCount = 0;
        dotTimer.start();
    }

    // Stoppt die Punkt-Animation und setzt den Zähler zurück
    private void stopDotAnimation() {
        if (dotTimer != null && dotTimer.isRunning()) {
            dotTimer.stop();
        }
        dotCount = 0;
        // Entferne die feste PreferredSize wieder, damit andere Statustexte das Layout normal beeinflussen
        statusLabel.setPreferredSize(null);
        statusLabel.revalidate();
        if (statusLabel.getParent() != null) {
            statusLabel.getParent().revalidate();
            statusLabel.getParent().repaint();
        }
    }

    private void updateButtons(boolean running) {
        searchButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        folderField.setEnabled(!running);
        queryField.setEnabled(!running);
        browseButton.setEnabled(!running);
    }

    private void updateFolderFieldState() {
        boolean anyDriveSelected = false;
        if (driveCheckBoxes != null) {
            for (JCheckBox cb : driveCheckBoxes) {
                if (cb.isSelected()) {
                    anyDriveSelected = true;
                    break;
                }
            }
        }
        folderField.setEnabled(!anyDriveSelected);
        browseButton.setEnabled(!anyDriveSelected);
        // Der Text im Feld bleibt erhalten, wird aber gesperrt, wenn ein Laufwerk ausgewählt ist
    }

    private void onBrowse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Ordner auswählen");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        // Falls bereits ein Pfad im Textfeld steht, starte dort
        String currentPath = folderField.getText();
        if (currentPath != null && !currentPath.isBlank()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
                chooser.setCurrentDirectory(currentDir);
            }
        }

        int result = chooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            if (selectedFolder != null && selectedFolder.isDirectory()) {
                folderField.setText(selectedFolder.getAbsolutePath());
            }
        }
    }

    private java.util.List<String> getSelectedDrives() {
        java.util.List<String> drives = new java.util.ArrayList<>();
        if (driveCheckBoxes != null) {
            for (JCheckBox cb : driveCheckBoxes) {
                if (cb.isSelected()) {
                    drives.add(cb.getText());
                }
            }
        }
        return drives;
    }

    private void onSearch() {
        java.util.List<String> selectedDrives = getSelectedDrives();
        String folder = folderField.getText();
        String q = queryField.getText();
        if (!selectedDrives.isEmpty()) {
            if (q == null || q.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Bitte einen Suchtext angeben.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
                return;
            }
            controller.startSearch("", q.trim(), selectedDrives);
            return;
        }
        // Kein Laufwerk ausgewählt, Ordner muss angegeben werden
        if (folder == null || folder.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte einen Startordner angeben oder ein Laufwerk auswählen.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (q == null || q.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte einen Suchtext angeben.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
            return;
        }
        controller.startSearch(folder.trim(), q.trim(), selectedDrives);
    }

    private void onCancel() {
        controller.cancelSearch();
    }

}
