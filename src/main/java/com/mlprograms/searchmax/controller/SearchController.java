package com.mlprograms.searchmax.controller;

import com.mlprograms.searchmax.model.SearchModel;
import com.mlprograms.searchmax.service.SearchEventListener;
import com.mlprograms.searchmax.service.SearchService;

import javax.swing.SwingUtilities;

public class SearchController implements SearchEventListener {

    private final SearchService service;
    private final SearchModel model;

    public SearchController(SearchService service, SearchModel model) {
        this.service = service;
        this.model = model;
    }

    public void startSearch(String folder, String query) {
        model.clearResults();
        model.setStatus("Suche läuft...");
        model.setId(null);
        service.search(folder, query, this);
    }

    public boolean cancelSearch() {
        String id = model.getId();
        if (id == null) {
            model.setStatus("Keine laufende Suche");
            return false;
        }
        boolean ok = service.cancel(id);
        model.setStatus(ok ? "Suche abgebrochen" : "Abbruch fehlgeschlagen");
        return ok;
    }

    @Override
    public void onId(String id) {
        SwingUtilities.invokeLater(() -> model.setId(id));
    }

    @Override
    public void onMatch(String match) {
        SwingUtilities.invokeLater(() -> {
            model.addResult(match);
            model.setStatus("Suche läuft...");
        });
    }

    @Override
    public void onEnd(String summary) {
        SwingUtilities.invokeLater(() -> model.setStatus(summary));
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> model.setStatus("Fehler: " + message));
    }

}
