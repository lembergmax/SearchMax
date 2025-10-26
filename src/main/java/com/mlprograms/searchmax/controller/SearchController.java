package com.mlprograms.searchmax.controller;

import com.mlprograms.searchmax.model.SearchModel;
import com.mlprograms.searchmax.service.SearchEventListener;
import com.mlprograms.searchmax.service.SearchService;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;

public class SearchController implements SearchEventListener {

    private final SearchService service;
    private final SearchModel model;

    public SearchController(SearchService service, SearchModel model) {
        this.service = service;
        this.model = model;
    }

    public void startSearch(final String folder, final String query, final List<String> drives, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final Map<String, Boolean> includesCase, final List<String> excludes, final Map<String, Boolean> excludesCase, final boolean includeAllMode) {
        model.clearResults();
        model.setStatus("Suche läuft...");

        if (includeAllMode) {
            service.search(folder, query, drives, this, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode);
        } else {
            service.search(folder, query, drives, this, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase);
        }
    }

    public boolean cancelSearch() {
        boolean ok = service.cancel();
        if (!ok) {
            model.setStatus("Keine laufende Suche");
            return false;
        }

        model.setStatus(ok ? "Suche abgebrochen" : "Abbruch fehlgeschlagen");

        return ok;
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
