package com.mlprograms.searchmax.service;

import com.mlprograms.searchmax.DirectoryTask;
import com.mlprograms.searchmax.SearchHandle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SearchService {

    private static final Logger LOGGER = Logger.getLogger(SearchService.class.getName());

    private final ForkJoinPool pool;
    private final ConcurrentMap<String, SearchHandle> searches = new ConcurrentHashMap<>();

    public SearchService() {
        this.pool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public void search(final String folderPath, final String queryText, final List<String> drives, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensions, final List<String> includes, final List<String> excludes) {
        if (listener == null) {
            throw new IllegalArgumentException("listener darf nicht null sein");
        }
        if (drives != null && !drives.isEmpty()) {
            handleSearchSelectedDrives(drives, queryText, listener, caseSensitive, extensions, includes, excludes);
            return;
        }

        if (folderPath != null) {
            final String trimmed = folderPath.trim();
            if (trimmed.length() == 1 && Character.isLetter(trimmed.charAt(0))) {
                handleSearchSelectedDrives(trimmed, queryText, listener, caseSensitive, extensions, includes, excludes);
                return;
            }
        }

        if ("*".equals(folderPath)) {
            handleSearchAllDrives(queryText, listener, caseSensitive, extensions, includes, excludes);
            return;
        }

        if (isDriveList(folderPath)) {
            handleSearchSelectedDrives(folderPath, queryText, listener, caseSensitive, extensions, includes, excludes);
            return;
        }

        startSearch(folderPath, queryText, listener, caseSensitive, extensions, includes, excludes);
    }

    public boolean cancel(String searchId) {
        if (searchId == null) {
            return false;
        }
        final SearchHandle handle = searches.remove(searchId);
        if (handle == null) {
            return false;
        }
        try {
            // Markiere das Handle als abgebrochen, damit laufende Tasks das erkennen
            try {
                handle.getCancelled().set(true);
            } catch (Exception ignore) {
                // Ignoriere
            }
            // Versuche alle registrierten Tasks abzubrechen
            final Collection<ForkJoinTask<?>> tasks = handle.getTasks();
            for (ForkJoinTask<?> t : tasks) {
                if (t != null) {
                    t.cancel(true);
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Abbrechen der Suche: " + searchId, e);
            return false;
        }
    }

    private boolean isDriveList(final String folderPath) {
        if (folderPath == null || !folderPath.contains(",")) {
            return false;
        }

        final String[] tokens = folderPath.split(",");
        for (String raw : tokens) {
            if (raw == null) {
                return false;
            }
            final String t = raw.trim();
            if (t.isEmpty()) {
                return false;
            }

            if (t.length() == 1 && Character.isLetter(t.charAt(0))) {
                continue;
            }

            if ((t.length() == 2 && Character.isLetter(t.charAt(0)) && t.charAt(1) == ':') || (t.length() == 3 && Character.isLetter(t.charAt(0)) && t.charAt(1) == ':' && (t.charAt(2) == '\\' || t.charAt(2) == '/'))) {
                continue;
            }

            return false;
        }

        return true;
    }

    private void handleSearchAllDrives(final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensions, final List<String> includes, final List<String> excludes) {
        final File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) {
            listener.onError("Keine Laufwerke gefunden");
            return;
        }

        final long startNano = System.nanoTime();
        final String searchId = UUID.randomUUID().toString();
        final SearchHandle sharedHandle = createSearchHandle(startNano, roots.length);

        searches.put(searchId, sharedHandle);
        listener.onId(searchId);

        for (final File root : roots) {
            final Path rootPath = root.toPath();
            if (Files.exists(rootPath)) {
                startSearchTask(searchId, rootPath, queryText, sharedHandle, listener, caseSensitive, extensions, includes, excludes);
            } else {
                sharedHandle.getRemainingTasks().decrementAndGet();
            }
        }

        if (sharedHandle.getRemainingTasks().get() <= 0) {
            final AtomicInteger matches = sharedHandle.getMatchCount();
            final int total = (matches == null) ? 0 : matches.get();
            listener.onEnd(String.format("%d Treffer", total));
            searches.entrySet().removeIf(e -> e.getValue() == sharedHandle);
        }
    }

    private void handleSearchSelectedDrives(final String folderPathList, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensions, final List<String> includes, final List<String> excludes) {
        final String[] tokens = folderPathList.split(",");
        if (tokens.length == 0) {
            listener.onError("Keine Laufwerke angegeben");
            return;
        }

        final long startNano = System.nanoTime();
        final String searchId = UUID.randomUUID().toString();
        final SearchHandle sharedHandle = createSearchHandle(startNano, tokens.length);

        searches.put(searchId, sharedHandle);
        listener.onId(searchId);

        for (String raw : tokens) {
            final String t = raw == null ? "" : raw.trim();
            if (t.isEmpty()) {
                sharedHandle.getRemainingTasks().decrementAndGet();
                continue;
            }

            String normalized = t;
            if (t.length() == 1 && Character.isLetter(t.charAt(0))) {
                normalized = t + ":\\";
            } else if (t.length() == 2 && Character.isLetter(t.charAt(0)) && t.charAt(1) == ':') {
                normalized = t + "\\";
            }

            final Path rootPath = Paths.get(normalized);
            if (Files.exists(rootPath)) {
                startSearchTask(searchId, rootPath, queryText, sharedHandle, listener, caseSensitive, extensions, includes, excludes);
            } else {
                sharedHandle.getRemainingTasks().decrementAndGet();
            }
        }

        if (sharedHandle.getRemainingTasks().get() <= 0) {
            final AtomicInteger matches = sharedHandle.getMatchCount();
            final int total = (matches == null) ? 0 : matches.get();
            listener.onEnd(String.format("%d Treffer", total));
            searches.entrySet().removeIf(e -> e.getValue() == sharedHandle);
        }
    }

    // Neue Methode für die Suche in ausgewählten Laufwerken
    private void handleSearchSelectedDrives(List<String> drives, String queryText, SearchEventListener listener, boolean caseSensitive, final List<String> extensions, final List<String> includes, final List<String> excludes) {
        if (drives == null || drives.isEmpty()) {
            listener.onError("Keine Laufwerke angegeben");
            return;
        }

        final long startNano = System.nanoTime();
        final String searchId = UUID.randomUUID().toString();
        final SearchHandle sharedHandle = createSearchHandle(startNano, drives.size());

        searches.put(searchId, sharedHandle);
        listener.onId(searchId);

        for (String raw : drives) {
            final String t = raw == null ? "" : raw.trim();
            if (t.isEmpty()) {
                sharedHandle.getRemainingTasks().decrementAndGet();
                continue;
            }

            String normalized = t;
            if (t.length() == 1 && Character.isLetter(t.charAt(0))) {
                normalized = t + ":\\";
            } else if (t.length() == 2 && Character.isLetter(t.charAt(0)) && t.charAt(1) == ':') {
                normalized = t + "\\";
            }

            final Path rootPath = Paths.get(normalized);
            if (Files.exists(rootPath)) {
                startSearchTask(searchId, rootPath, queryText, sharedHandle, listener, caseSensitive, extensions, includes, excludes);
            } else {
                sharedHandle.getRemainingTasks().decrementAndGet();
            }
        }

        if (sharedHandle.getRemainingTasks().get() <= 0) {
            final AtomicInteger matches = sharedHandle.getMatchCount();
            final int total = (matches == null) ? 0 : matches.get();
            listener.onEnd(String.format("%d Treffer", total));
            searches.entrySet().removeIf(e -> e.getValue() == sharedHandle);
        }
    }

    private void startSearch(final String folderPath, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensions, final List<String> includes, final List<String> excludes) {
        if (folderPath == null || queryText == null || (queryText.isEmpty() && ( (extensions == null || extensions.isEmpty()) && (includes == null || includes.isEmpty()) ))) {
            listener.onError("Ungültige Anfrage");
            return;
        }

        final Path startPath = Paths.get(folderPath);
        if (!Files.exists(startPath)) {
            listener.onError("Startpfad existiert nicht");
            return;
        }

        final String searchId = UUID.randomUUID().toString();
        listener.onId(searchId);

        final long startNano = System.nanoTime();
        final SearchHandle handle = createSearchHandle(startNano, 1);
        searches.put(searchId, handle);

        startSearchTask(searchId, startPath, queryText, handle, listener, caseSensitive, extensions, includes, excludes);
    }

    private SearchHandle createSearchHandle(long startNano, int remainingTasks) {
        return new SearchHandle(null, startNano, new AtomicInteger(remainingTasks), new AtomicInteger(0), new ConcurrentLinkedQueue<>());
    }

    private void startSearchTask(final String id, final Path startPath, final String queryText, final SearchHandle searchHandle, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensions, final List<String> includes, final List<String> excludes) {
        final Collection<String> results = (searchHandle.getResults() != null) ? searchHandle.getResults() : new ConcurrentLinkedQueue<>();
        final AtomicInteger matchCount = (searchHandle.getMatchCount() != null) ? searchHandle.getMatchCount() : new AtomicInteger(0);

        final Consumer<String> onMatch = s -> safeSendMatch(listener, s);

        ForkJoinTask<?> submitted = pool.submit(() -> {
            try {
                pool.invoke(new DirectoryTask(startPath, results, matchCount, queryText, searchHandle.getStartNano(), onMatch, searchHandle.getCancelled(), caseSensitive, extensions, includes, excludes));
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Suche fehlgeschlagen (id=" + id + ")", t);
                safeSendError(listener, "Suche fehlgeschlagen: " + t.getMessage());
            } finally {
                completeHandle(searchHandle, listener);
            }
        });

        searchHandle.setTask(submitted);
        // Falls das handle mehrere Tasks verwalten soll (z.B. für mehrere Startpfade), sicherstellen, dass das Task
        // zur Liste hinzugefügt wird. setTask fügt bereits hinzu; falls zusätzliche Tasks entstehen, kann addTask verwendet werden.
        searchHandle.addTask(submitted);
    }

    private void completeHandle(final SearchHandle handle, final SearchEventListener listener) {
        try {
            if (handle != null) {
                final AtomicInteger remaining = handle.getRemainingTasks();
                if (remaining == null) {
                    listener.onEnd("0 Treffer");
                    return;
                }

                int left = remaining.decrementAndGet();
                if (left <= 0) {
                    final AtomicInteger matches = handle.getMatchCount();
                    final int total = (matches == null) ? 0 : matches.get();
                    listener.onEnd(String.format("%d Treffer", total));
                    searches.entrySet().removeIf(e -> e.getValue() == handle);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Abschließen des Handles", e);
        }
    }

    private void safeSendMatch(final SearchEventListener listener, final String data) {
        try {
            listener.onMatch(data);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Fehler beim Senden von Match", e);
        }
    }

    private void safeSendError(final SearchEventListener listener, final String message) {
        try {
            listener.onError(message);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Fehler beim Senden von Error", e);
        }
    }

}
