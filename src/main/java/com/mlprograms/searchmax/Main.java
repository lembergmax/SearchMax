package com.mlprograms.searchmax;

import com.mlprograms.searchmax.controller.SearchController;
import com.mlprograms.searchmax.model.SearchModel;
import com.mlprograms.searchmax.service.SearchService;
import com.mlprograms.searchmax.view.SearchView;

import javax.swing.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (final Exception exception) {
                log.warn("Fehler beim Setzen des LookAndFeels", exception);
            }

            final SearchService searchService = new SearchService();
            final SearchModel searchModel = new SearchModel();
            final SearchController searchController = new SearchController(searchService, searchModel);
            final SearchView searchView = new SearchView(searchController, searchModel);
            searchView.setVisible(true);
        });
    }

}