package com.mlprograms.searchmax.service;

import com.mlprograms.searchmax.DirectoryTask;
import com.mlprograms.searchmax.SearchHandle;
import com.mlprograms.searchmax.ExtractionMode;
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
    private volatile ExtractionMode extractionMode = ExtractionMode.POI_THEN_TIKA;

    public SearchService() {
        this.pool = new ForkJoinPool(1);
    }

    public synchronized void setUseAllCores(boolean useAll) {
        // Begrenze die maximale Parallelität, um zu verhindern, dass auf Systemen mit
        // vielen Kernen zu viele Threads (und damit Stack/VM-Reservations) angelegt werden,
        // was zu nativen Speicher-Allokationsfehlern führen kann.
        final int available = Math.max(1, Runtime.getRuntime().availableProcessors());
        final int MAX_PARALLEL = 4; // konservativer Grenzwert für typische Desktop-Systeme
        int desired = useAll ? Math.max(1, Math.min(available, MAX_PARALLEL)) : available / 3;
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
        // Delegate to the full-version with time filters (pass nulls)
        search(folderPath, queryText, drives, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode, null, null, false);
    }

    public void search(final String folderPath, final String queryText, final List<String> drives, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeIncludes, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeExcludes, final boolean timeIncludeAllMode) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener darf nicht null sein");
        }

        if (drives != null && !drives.isEmpty()) {
            handleSearchSelectedDrives(drives, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode, timeIncludes, timeExcludes, timeIncludeAllMode);
            return;
        }

        if (folderPath != null) {
            final String trimmed = folderPath.trim();
            if (trimmed.length() == 1 && Character.isLetter(trimmed.charAt(0))) {
                handleSearchSelectedDrives(trimmed, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode, timeIncludes, timeExcludes, timeIncludeAllMode);
                return;
            }
        }

        if ("*".equals(folderPath)) {
            handleSearchAllDrivesInternal(queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode, timeIncludes, timeExcludes, timeIncludeAllMode);
            return;
        }

        if (isDriveList(folderPath)) {
            handleSearchSelectedDrives(folderPath, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode, timeIncludes, timeExcludes, timeIncludeAllMode);
            return;
        }

        startSearch(folderPath, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode, timeIncludes, timeExcludes, timeIncludeAllMode);
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

    private void handleSearchAllDrivesInternal(final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeIncludes, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeExcludes, final boolean timeIncludeAllMode) {
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
                startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode, timeIncludes, timeExcludes, timeIncludeAllMode);
            } else {
                handle.getRemainingTasks().decrementAndGet();
            }
        }

        checkComplete(handle, listener);
    }

    private void handleSearchSelectedDrives(final String folderPathList, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeIncludes, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeExcludes, final boolean timeIncludeAllMode) {
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
                startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode, timeIncludes, timeExcludes, timeIncludeAllMode);
            } else {
                handle.getRemainingTasks().decrementAndGet();
            }
        }

        checkComplete(handle, listener);
    }

    private void handleSearchSelectedDrives(final List<String> drives, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeIncludes, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeExcludes, final boolean timeIncludeAllMode) {
        if (drives == null || drives.isEmpty()) {
            listener.onError("Keine Laufwerke angegeben");
            return;
        }

        final long startNano = System.nanoTime();
        final String searchId = UUID.randomUUID().toString();
        final SearchHandle handle = createSearchHandle(startNano, drives.size());

        searches.put(searchId, handle);

        for (String drive : drives) {
            if (drive == null || drive.isEmpty()) {
                handle.getRemainingTasks().decrementAndGet();
                continue;
            }

            final Path rootPath = Paths.get(drive);
            if (Files.exists(rootPath)) {
                startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode, timeIncludes, timeExcludes, timeIncludeAllMode);
            } else {
                handle.getRemainingTasks().decrementAndGet();
            }
        }

        checkComplete(handle, listener);
    }

    private void startSearch(final String folderPath, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeIncludes, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeExcludes, final boolean timeIncludeAllMode) {
        final Path startPath = Paths.get(folderPath);
        if (!Files.exists(startPath)) {
            listener.onError("Der angegebene Ordner existiert nicht: " + folderPath);
            return;
        }

        final long startNano = System.nanoTime();
        final String searchId = UUID.randomUUID().toString();
        final SearchHandle handle = createSearchHandle(startNano, 1);

        searches.put(searchId, handle);

        startSearchTask(searchId, startPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode, contentIncludes, contentIncludesCase, contentExcludes, contentExcludesCase, contentIncludeAllMode, timeIncludes, timeExcludes, timeIncludeAllMode);

        checkComplete(handle, listener);
    }

    private void startSearchTask(final String searchId, final Path rootPath, final String queryText, final SearchHandle handle, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode, final List<String> contentIncludes, final java.util.Map<String, Boolean> contentIncludesCase, final List<String> contentExcludes, final java.util.Map<String, Boolean> contentExcludesCase, final boolean contentIncludeAllMode, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeIncludes, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeExcludes, final boolean timeIncludeAllMode) {
        // Create DirectoryTask with the correct argument order expected by DirectoryTask constructor
        final DirectoryTask task = new DirectoryTask(rootPath,
                handle.getResults(),
                handle.getMatchCount(),
                handle.getRemainingTasks(), // pass remainingTasks so root-tasks can decrement when finished
                queryText,
                handle.getStartNano(),
                (s) -> safeSendMatch(listener, s),
                handle.getCancelled(),
                caseSensitive,
                extensionsAllow,
                extensionsDeny,
                includes,
                includesCase,
                excludes,
                excludesCase,
                includeAllMode,
                contentIncludes,
                contentIncludesCase,
                contentExcludes,
                contentExcludesCase,
                contentIncludeAllMode,
                timeIncludes,
                timeExcludes,
                timeIncludeAllMode,
                extractionMode);
        // Register and submit
        handle.getTasks().add(task);
        pool.submit(task);
    }

    private SearchHandle createSearchHandle(long startNano, int remainingTasks) {
        return new SearchHandle(null, startNano, new AtomicInteger(remainingTasks), new AtomicInteger(0), new ConcurrentLinkedQueue<>(), remainingTasks);
    }

    private void checkComplete(final SearchHandle handle, final SearchEventListener listener) {
        pool.submit(() -> {
            try {
                while (handle.getRemainingTasks().get() > 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                final int total = handle.getMatchCount() == null ? 0 : handle.getMatchCount().get();
                listener.onEnd(String.format("%d files found", total));
                searches.entrySet().removeIf(e -> e.getValue() == handle);
            } catch (Exception e) {
                log.debug("checkComplete thread interrupted", e);
            }
        });
    }

    private void safeSendMatch(final SearchEventListener listener, final String data) {
        try {
            listener.onMatch(data);
        } catch (Exception e) {
            log.debug("Fehler beim Senden von Match", e);
        }
    }

    private String normalizeDrivePath(final String drivePath) {
        if (drivePath == null) {
            return null;
        }

        final String trimmed = drivePath.trim();
        if (trimmed.length() == 1 && Character.isLetter(trimmed.charAt(0))) {
            return trimmed + ":\\";
        }
        if (trimmed.length() == 2 && Character.isLetter(trimmed.charAt(0)) && trimmed.charAt(1) == ':') {
            return trimmed + "\\";
        }
        if (trimmed.length() == 3 && Character.isLetter(trimmed.charAt(0)) && trimmed.charAt(1) == ':' && (trimmed.charAt(2) == '\\' || trimmed.charAt(2) == '/')) {
            return trimmed;
        }

        return null;
    }
}
