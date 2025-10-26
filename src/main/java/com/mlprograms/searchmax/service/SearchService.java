package com.mlprograms.searchmax.service;

import com.mlprograms.searchmax.DirectoryTask;
import com.mlprograms.searchmax.SearchHandle;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class SearchService {

    private volatile ForkJoinPool pool;
    private final ConcurrentMap<String, SearchHandle> searches = new ConcurrentHashMap<>();

    public SearchService() {
        this.pool = new ForkJoinPool(1);
    }

    public synchronized void setUseAllCores(boolean useAll) {
        int desired = useAll ? Math.max(1, Runtime.getRuntime().availableProcessors()) : 1;
        if (pool != null && pool.getParallelism() == desired) return;
        // replace pool for subsequent searches
        ForkJoinPool newPool = new ForkJoinPool(desired);
        ForkJoinPool old = this.pool;
        this.pool = newPool;
        try {
            if (old != null) old.shutdown();
        } catch (Exception e) {
            log.debug("Fehler beim Herunterfahren des alten Pools", e);
        }
    }

    public boolean isUsingAllCores() {
        return pool != null && pool.getParallelism() > 1;
    }

    public void search(final String folderPath, final String queryText, final List<String> drives, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener darf nicht null sein");
        }

        if (drives != null && !drives.isEmpty()) {
            handleSearchSelectedDrives(drives, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode);
            return;
        }

        if (folderPath != null) {
            final String trimmed = folderPath.trim();
            if (trimmed.length() == 1 && Character.isLetter(trimmed.charAt(0))) {
                handleSearchSelectedDrives(trimmed, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode);
                return;
            }
        }

        if ("*".equals(folderPath)) {
            handleSearchAllDrives(queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, null, null, null, null, false);
            return;
        }

        if (isDriveList(folderPath)) {
            handleSearchSelectedDrives(folderPath, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode);
            return;
        }

        startSearch(folderPath, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode);
    }

    public boolean cancel() {
        boolean any = false;
        try {
            for (final java.util.Map.Entry<String, SearchHandle> e : searches.entrySet()) {
                final SearchHandle handle = e.getValue();
                if (handle == null) continue;
                any = true;
                handle.getCancelled().set(true);
                for (final ForkJoinTask<?> forkJoinTask : handle.getTasks()) {
                    if (forkJoinTask != null) {
                        forkJoinTask.cancel(true);
                    }
                }
            }
            searches.clear();
            return any;
        } catch (Exception e) {
            log.warn("Fehler beim Abbrechen der Suchen", e);
            return any;
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

            final String trimmedRaw = raw.trim();
            if (trimmedRaw.isEmpty()) {
                return false;
            }
            if (trimmedRaw.length() == 1 && Character.isLetter(trimmedRaw.charAt(0))) {
                continue;
            }
            if ((trimmedRaw.length() == 2 && Character.isLetter(trimmedRaw.charAt(0)) && trimmedRaw.charAt(1) == ':') || (trimmedRaw.length() == 3 && Character.isLetter(trimmedRaw.charAt(0)) && trimmedRaw.charAt(1) == ':' && (trimmedRaw.charAt(2) == '\\' || trimmedRaw.charAt(2) == '/'))) {
                continue;
            }

            return false;
        }
        return true;
    }

    private void handleSearchAllDrives(final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode) {
        // Delegate to the overload that accepts content filters (pass nulls/defaults)
        final File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) {
            listener.onError("Keine Laufwerke gefunden");
            return;
        }

        final long startNano = System.nanoTime();
        final String searchId = UUID.randomUUID().toString();
        final SearchHandle handle = createSearchHandle(startNano, roots.length);

        searches.put(searchId, handle);

        for (File root : roots) {
            final Path rootPath = root.toPath();
            if (Files.exists(rootPath)) {
                startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, null, null, null, null, false);
            } else {
                handle.getRemainingTasks().decrementAndGet();
            }
        }

        checkComplete(handle, listener);
    }

    // Overload with content params
    private void handleSearchAllDrives(final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode) {
        final File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) {
            listener.onError("Keine Laufwerke gefunden");
            return;
        }

        final long startNano = System.nanoTime();
        final String searchId = UUID.randomUUID().toString();
        final SearchHandle handle = createSearchHandle(startNano, roots.length);

        searches.put(searchId, handle);

        for (File root : roots) {
            final Path rootPath = root.toPath();
            if (Files.exists(rootPath)) {
                startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode);
            } else {
                handle.getRemainingTasks().decrementAndGet();
            }
        }

        checkComplete(handle, listener);
    }

    // Overloads for selected drives with content params
    private void handleSearchSelectedDrives(final String folderPathList, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode) {
        final String[] tokens = folderPathList.split(",");
        if (tokens.length == 0) {
            listener.onError("Keine Laufwerke angegeben");
            return;
        }

        final long startNano = System.nanoTime();
        final String searchId = UUID.randomUUID().toString();
        final SearchHandle handle = createSearchHandle(startNano, tokens.length);

        searches.put(searchId, handle);

        for (String raw : tokens) {
            final String t = raw == null ? "" : raw.trim();
            if (t.isEmpty()) {
                handle.getRemainingTasks().decrementAndGet();
                continue;
            }

            String normalized = normalizeDrivePath(t);
            final Path rootPath = Paths.get(normalized);
            if (Files.exists(rootPath)) {
                startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode);
            } else {
                handle.getRemainingTasks().decrementAndGet();
            }
        }

        checkComplete(handle, listener);
    }

    private void handleSearchSelectedDrives(final List<String> drives, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode) {
        if (drives == null || drives.isEmpty()) {
            listener.onError("Keine Laufwerke angegeben");
            return;
        }

        final long startNano = System.nanoTime();
        final String searchId = UUID.randomUUID().toString();
        final SearchHandle handle = createSearchHandle(startNano, drives.size());

        searches.put(searchId, handle);

        for (String raw : drives) {
            final String t = raw == null ? "" : raw.trim();
            if (t.isEmpty()) {
                handle.getRemainingTasks().decrementAndGet();
                continue;
            }

            String normalized = normalizeDrivePath(t);
            final Path rootPath = Paths.get(normalized);
            if (Files.exists(rootPath)) {
                startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode);
            } else {
                handle.getRemainingTasks().decrementAndGet();
            }
        }

        checkComplete(handle, listener);
    }

    // Startet eine normale Suche in einem einzelnen Ordner
    private void startSearch(final String folderPath, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            listener.onError("Kein Pfad angegeben");
            return;
        }

        final Path rootPath = Paths.get(folderPath.trim());
        if (!Files.exists(rootPath)) {
            listener.onError("Pfad nicht gefunden: " + folderPath);
            return;
        }

        final long startNano = System.nanoTime();
        final String searchId = UUID.randomUUID().toString();
        final SearchHandle handle = createSearchHandle(startNano, 1);
        searches.put(searchId, handle);

        startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode);

        checkComplete(handle, listener);
    }

    // Erstellt und startet eine DirectoryTask für einen gegebenen rootPath. Registriert die ForkJoinTask im Handle
    // und sorgt dafür, dass beim Abschluss completeHandle(...) aufgerufen wird.
    private void startSearchTask(final String searchId, final Path rootPath, final String queryText, final SearchHandle handle, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode) {
        if (handle == null || rootPath == null) {
            return;
        }

        // Emitter, der Ergebnisse über safeSendMatch an den Listener weiterreicht
        java.util.function.Consumer<String> emitter = data -> safeSendMatch(listener, data);

        final DirectoryTask task = new DirectoryTask(rootPath, handle.getResults(), handle.getMatchCount(), queryText, handle.getStartNano(), emitter, handle.getCancelled(), caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode);

        // Submit in den konfigurierten ForkJoinPool
        final ForkJoinTask<?> fjt = pool.submit((ForkJoinTask<?>) task);
        handle.addTask(fjt);
        handle.setTask(fjt);

        // Startet einen kurzen Daemon-Thread, der auf das Ende der Task wartet und dann completeHandle aufruft.
        Thread waiter = new Thread(() -> {
            try {
                try {
                    fjt.join();
                } catch (Throwable ignore) {
                    // Ignoriere Join-Fehler, rufe trotzdem completeHandle auf
                }
            } finally {
                completeHandle(handle, listener);
            }
        }, "search-waiter");
        waiter.setDaemon(true);
        waiter.start();
    }

    private SearchHandle createSearchHandle(long startNano, int remainingTasks) {
        return new SearchHandle(null, startNano, new AtomicInteger(remainingTasks), new AtomicInteger(0), new ConcurrentLinkedQueue<>(), remainingTasks);
    }

    private void completeHandle(final SearchHandle handle, final SearchEventListener listener) {
        try {
            if (handle == null) {
                return;
            }

            final int left = handle.getRemainingTasks().decrementAndGet();
            if (left <= 0) {
                final int total = handle.getMatchCount().get();
                listener.onEnd(String.format("%d gefundene Dateien", total));
                searches.entrySet().removeIf(e -> e.getValue() == handle);
            }
        } catch (Exception e) {
            log.warn("Fehler beim Abschließen des Handles", e);
        }
    }

    private void safeSendMatch(final SearchEventListener listener, final String data) {
        try {
            listener.onMatch(data);
        } catch (Exception e) {
            log.info("Fehler beim Senden von Match", e);
        }
    }

    private void safeSendError(final SearchEventListener listener, final String message) {
        try {
            listener.onError(message);
        } catch (Exception e) {
            log.info("Fehler beim Senden von Error", e);
        }
    }

    private void checkComplete(SearchHandle handle, SearchEventListener listener) {
        if (handle.getRemainingTasks().get() <= 0) {
            final int total = handle.getMatchCount().get();
            listener.onEnd(String.format("%d gefundene Dateien", total));
            searches.entrySet().removeIf(e -> e.getValue() == handle);
        }
    }

    private String normalizeDrivePath(String raw) {
        if (raw.length() == 1 && Character.isLetter(raw.charAt(0))) {
            return raw + ":\\";
        }

        if (raw.length() == 2 && Character.isLetter(raw.charAt(0)) && raw.charAt(1) == ':') {
            return raw + "\\";
        }

        return raw;
    }

}
