package com.mlprograms.searchmax;

import com.mlprograms.searchmax.controller.SearchController;
import com.mlprograms.searchmax.model.SearchModel;
import com.mlprograms.searchmax.service.SearchService;
import com.mlprograms.searchmax.view.SearchView;

import javax.swing.*;
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            SearchService service = new SearchService();
            SearchModel model = new SearchModel();
            SearchController controller = new SearchController(service, model);
            SearchView view = new SearchView(controller, model);
            view.setVisible(true);
        });
    }

}