package com.mlprograms.searchmax.controller;

import com.mlprograms.searchmax.model.SearchModel;
import com.mlprograms.searchmax.service.SearchEventListener;
import com.mlprograms.searchmax.service.SearchService;

import javax.swing.SwingUtilities;
import com.mlprograms.searchmax.view.GuiConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class SearchController implements SearchEventListener {

    private final SearchService service;
    private final SearchModel model;

    private final ConcurrentLinkedQueue<String> pendingResults = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService flushScheduler;
    // Executor für das Starten von Suchvorgängen, damit die UI-Thread nicht blockiert wird
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "search-starter");
        t.setDaemon(true);
        return t;
    });
    private volatile int batchSize = 100;

    public SearchController(SearchService service, SearchModel model) {
        this.service = service;
        this.model = model;
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "search-flusher");
            t.setDaemon(true);
            return t;
        });
        this.flushScheduler.scheduleAtFixedRate(this::flushPending, 200, 200, TimeUnit.MILLISECONDS);
    }

    public void startSearch(final String folder, final String query, final List<String> drives, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final Map<String, Boolean> includesCase, final List<String> excludes, final Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode) {
        pendingResults.clear();
        model.clearResults();
        model.setStatus(GuiConstants.SEARCH_RUNNING_TEXT);

        // Starte die eigentliche Suche asynchron, sonst blockiert ggf. die EDT und die UI wird nicht aktualisiert
        searchExecutor.submit(() -> {
            try {
                service.search(folder, query, drives, this, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode);
            } catch (Exception ex) {
                // Fehler zurück an das UI-Model geben
                SwingUtilities.invokeLater(() -> model.setStatus("Fehler: " + ex.getMessage()));
            }
        });
    }

    public boolean cancelSearch() {
        boolean ok = service.cancel();
        if (!ok) {
            model.setStatus("Keine laufende Suche");
            return false;
        }

        model.setStatus("Suche abgebrochen");

        return ok;
    }

    @Override
    public void onMatch(String match) {
        if (match == null) return;
        pendingResults.add(match);
        if (pendingResults.size() >= batchSize) {
            flushPending();
        }
    }

    private void flushPending() {
        try {
            if (pendingResults.isEmpty()) return;
            int maxPerFlush = service.isUsingAllCores() ? 1000 : batchSize;
            List<String> batch = new ArrayList<>();
            for (int i = 0; i < maxPerFlush; i++) {
                String s = pendingResults.poll();
                if (s == null) break;
                batch.add(s);
            }
            if (batch.isEmpty()) return;
            SwingUtilities.invokeLater(() -> model.addResults(batch));
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onEnd(String summary) {
        // flush remaining results immediately
        flushAllAndStopIfNeeded();
        SwingUtilities.invokeLater(() -> model.setStatus(summary));
    }

    private void flushAllAndStopIfNeeded() {
        List<String> all = new ArrayList<>();
        String s;
        while ((s = pendingResults.poll()) != null) {
            all.add(s);
            if (all.size() >= batchSize) {
                List<String> part = new ArrayList<>(all);
                SwingUtilities.invokeLater(() -> model.addResultsBatch(part));
                all.clear();
            }
        }
        if (!all.isEmpty()) {
            List<String> part = new ArrayList<>(all);
            SwingUtilities.invokeLater(() -> model.addResultsBatch(part));
        }
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> model.setStatus("Fehler: " + message));
    }

    public void setUseAllCores(boolean useAll) {
        if (useAll) {
            batchSize = 500;
        } else {
            batchSize = 100;
        }
        service.setUseAllCores(useAll);
    }
}
