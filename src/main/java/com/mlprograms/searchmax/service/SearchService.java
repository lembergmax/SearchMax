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
import java.util.function.Consumer;

@Slf4j
public final class SearchService {

    private final ForkJoinPool pool;
    private final ConcurrentMap<String, SearchHandle> searches = new ConcurrentHashMap<>();

    public SearchService() {
        this.pool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public void search(final String folderPath, final String queryText, final List<String> drives, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase) {
        search(folderPath, queryText, drives, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, false);
    }

    public void search(final String folderPath, final String queryText, final List<String> drives, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener darf nicht null sein");
        }

        if (drives != null && !drives.isEmpty()) {
            handleSearchSelectedDrives(drives, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode);
            return;
        }

        if (folderPath != null) {
            final String trimmed = folderPath.trim();
            if (trimmed.length() == 1 && Character.isLetter(trimmed.charAt(0))) {
                handleSearchSelectedDrives(trimmed, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode);
                return;
            }
        }

        if ("*".equals(folderPath)) {
            handleSearchAllDrives(queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode);
            return;
        }

        if (isDriveList(folderPath)) {
            handleSearchSelectedDrives(folderPath, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode);
            return;
        }

        startSearch(folderPath, queryText, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode);
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
                startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode);
            } else {
                handle.getRemainingTasks().decrementAndGet();
            }
        }

        checkComplete(handle, listener);
    }

    private void handleSearchSelectedDrives(final String folderPathList, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode) {
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
                startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode);
            } else {
                handle.getRemainingTasks().decrementAndGet();
            }
        }

        checkComplete(handle, listener);
    }

    private void handleSearchSelectedDrives(final List<String> drives, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode) {
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
                startSearchTask(searchId, rootPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode);
            } else {
                handle.getRemainingTasks().decrementAndGet();
            }
        }

        checkComplete(handle, listener);
    }

    private void startSearch(final String folderPath, final String queryText, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode) {
        if (folderPath == null || queryText == null || (queryText.isEmpty() && ((extensionsAllow == null || extensionsAllow.isEmpty()) && (includes == null || includes.isEmpty())))) {
            listener.onError("Ungültige Anfrage");
            return;
        }

        final Path startPath = Paths.get(folderPath);
        if (!Files.exists(startPath)) {
            listener.onError("Startpfad existiert nicht");
            return;
        }

        final String searchId = UUID.randomUUID().toString();

        final long startNano = System.nanoTime();
        final SearchHandle handle = createSearchHandle(startNano, 1);
        searches.put(searchId, handle);

        startSearchTask(searchId, startPath, queryText, handle, listener, caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode);
    }

    private SearchHandle createSearchHandle(long startNano, int remainingTasks) {
        return new SearchHandle(null, startNano, new AtomicInteger(remainingTasks), new AtomicInteger(0), new ConcurrentLinkedQueue<>());
    }

    private void startSearchTask(final String id, final Path startPath, final String queryText, final SearchHandle handle, final SearchEventListener listener, final boolean caseSensitive, final List<String> extensionsAllow, final List<String> extensionsDeny, final List<String> includes, final java.util.Map<String, Boolean> includesCase, final List<String> excludes, final java.util.Map<String, Boolean> excludesCase, final boolean includeAllMode) {
        final Consumer<String> onMatch = s -> safeSendMatch(listener, s);

        ForkJoinTask<?> submitted = pool.submit(() -> {
            try {
                pool.invoke(new DirectoryTask(startPath, handle.getResults(), handle.getMatchCount(), queryText, handle.getStartNano(), onMatch, handle.getCancelled(), caseSensitive, extensionsAllow, extensionsDeny, includes, includesCase, excludes, excludesCase, includeAllMode));
            } catch (Throwable t) {
                log.error("Suche fehlgeschlagen (id=" + id + ")", t);
                safeSendError(listener, "Suche fehlgeschlagen: " + t.getMessage());
            } finally {
                completeHandle(handle, listener);
            }
        });

        handle.setTask(submitted);
        handle.addTask(submitted);
    }

    private void completeHandle(final SearchHandle handle, final SearchEventListener listener) {
        try {
            if (handle == null) {
                return;
            }

            final int left = handle.getRemainingTasks().decrementAndGet();
            if (left <= 0) {
                final int total = handle.getMatchCount().get();
                listener.onEnd(String.format("%d Treffer", total));
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
            listener.onEnd(String.format("%d Treffer", total));
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
