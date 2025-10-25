package com.mlprograms.searchmax;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DirectoryTask extends RecursiveAction {

    private static final Logger LOGGER = Logger.getLogger(DirectoryTask.class.getName());

    private static final Set<String> SYSTEM_DIR_NAMES = Set.of("system volume information", "$recycle.bin", "found.000", "recycler");

    private static final int CHUNK_SIZE = 64;

    private final Path directoryPath;
    private final Collection<String> result;
    private final AtomicInteger matchCount;
    private final String query;
    private final int queryLen;
    private final long startNano;
    private final Consumer<String> emitter;
    private final AtomicBoolean cancelled;
    private final boolean caseSensitive;
    private final List<String> extensions; // allowed extensions (lowercase, with dot), null = no restriction
    private final List<String> includeFilters; // filename must contain at least one (lowercase), null = no restriction
    private final List<String> excludeFilters; // filename must not contain any of these (lowercase), null = no restriction

    public DirectoryTask(Path directoryPath, Collection<String> result, AtomicInteger matchCount, String query, long startNano, Consumer<String> emitter, boolean caseSensitive) {
        this(directoryPath, result, matchCount, query, startNano, emitter, new AtomicBoolean(false), caseSensitive, null, null, null);
    }

    // Neuer Konstruktor, der ein externes Abbruch-Flag verwendet
    public DirectoryTask(Path directoryPath, Collection<String> result, AtomicInteger matchCount, String query, long startNano, Consumer<String> emitter, AtomicBoolean cancelled, boolean caseSensitive) {
        this(directoryPath, result, matchCount, query, startNano, emitter, cancelled, caseSensitive, null, null, null);
    }

    // Vollständiger Konstruktor mit Extension-Filter
    public DirectoryTask(Path directoryPath, Collection<String> result, AtomicInteger matchCount, String query, long startNano, Consumer<String> emitter, AtomicBoolean cancelled, boolean caseSensitive, List<String> extensions, List<String> includeFilters, List<String> excludeFilters) {
        this.directoryPath = directoryPath;
        this.result = result == null ? new ConcurrentLinkedQueue<>() : result;
        this.matchCount = matchCount;
        this.query = (query == null) ? "" : query;
        this.queryLen = this.query.length();
        this.startNano = startNano;
        this.emitter = emitter;
        this.cancelled = cancelled == null ? new AtomicBoolean(false) : cancelled;
        this.caseSensitive = caseSensitive;
        this.extensions = (extensions == null || extensions.isEmpty()) ? null : new ArrayList<>(extensions);
        this.includeFilters = (includeFilters == null || includeFilters.isEmpty()) ? null : normalizeList(includeFilters);
        this.excludeFilters = (excludeFilters == null || excludeFilters.isEmpty()) ? null : normalizeList(excludeFilters);
    }

    private List<String> normalizeList(List<String> src) {
        List<String> out = new ArrayList<>();
        for (String s : src) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            if (!caseSensitive) t = t.toLowerCase(Locale.ROOT);
            out.add(t);
        }
        return out.isEmpty() ? null : out;
    }

    @Override
    protected void compute() {
        if (Thread.currentThread().isInterrupted() || (cancelled != null && cancelled.get()) || directoryPath == null || !Files.isDirectory(directoryPath)) {
            return;
        }

        List<DirectoryTask> subtasks = new ArrayList<>(8);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
            for (Path entry : stream) {
                if (Thread.currentThread().isInterrupted() || (cancelled != null && cancelled.get())) {
                    return;
                }

                try {
                    if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                        if (isSystemDirectory(entry)) {
                            continue;
                        }
                        subtasks.add(new DirectoryTask(entry, result, matchCount, query, startNano, emitter, cancelled, caseSensitive, extensions, includeFilters, excludeFilters));
                        if (subtasks.size() >= CHUNK_SIZE) {
                            invokeAll(new ArrayList<>(subtasks));
                            subtasks.clear();
                        }
                    } else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                        checkAndAddIfMatches(entry);
                    }
                } catch (SecurityException se) {
                    LOGGER.log(Level.FINE, "Zugriff verweigert für Eintrag: " + entry, se);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Kann Verzeichnis nicht lesen: " + directoryPath + " - " + e.getMessage());
        }

        if (!subtasks.isEmpty()) {
            invokeAll(subtasks);
        }
    }

    private boolean isSystemDirectory(final Path path) {
        final Path namePath = path.getFileName();
        if (namePath == null) {
            return false;
        }
        final String name = namePath.toString().toLowerCase(Locale.ROOT);
        return SYSTEM_DIR_NAMES.contains(name) || name.startsWith("windows");
    }

    private void checkAndAddIfMatches(final Path filePath) {
        if (Thread.currentThread().isInterrupted() || (cancelled != null && cancelled.get())) {
            return;
        }

        final String fileName = filePath.getFileName().toString();
        if (queryLen == 0) {
            // Wenn kein Suchtext angegeben ist, wir erlauben Matches nur basierend auf Extension
            // aber die Methode sollte weiterarbeiten, daher nicht returnen hier
        }
        if (fileName == null) {
            return;
        }
        if (fileName.length() < queryLen) {
            // falls Query länger als Dateiname ist, ist normal kein Match möglich
            // aber Dateiendungs-Filter kann trotzdem greifen -> wenn Query non-empty, skip
            if (queryLen > 0) return;
        }

        boolean textMatches = (queryLen == 0) ? true : containsIgnoreCase(fileName, query);

        if (!textMatches) {
            return;
        }

        // Prüfe includeFilters: falls gesetzt, Dateiname muss mindestens einen der Strings enthalten
        if (includeFilters != null && !includeFilters.isEmpty()) {
            boolean any = false;
            String cmp = caseSensitive ? fileName : fileName.toLowerCase(Locale.ROOT);
            for (String inc : includeFilters) {
                if (inc == null || inc.isEmpty()) continue;
                if (cmp.contains(caseSensitive ? inc : inc.toLowerCase(Locale.ROOT))) {
                    any = true;
                    break;
                }
            }
            if (!any) return;
        }

        // Prüfe excludeFilters: falls gesetzt, Dateiname darf keinen der Strings enthalten
        if (excludeFilters != null && !excludeFilters.isEmpty()) {
            String cmp = caseSensitive ? fileName : fileName.toLowerCase(Locale.ROOT);
            for (String exc : excludeFilters) {
                if (exc == null || exc.isEmpty()) continue;
                if (cmp.contains(caseSensitive ? exc : exc.toLowerCase(Locale.ROOT))) {
                    return;
                }
            }
        }

        // Prüfe Dateiendung falls Filter gesetzt ist
        if (extensions != null && !extensions.isEmpty()) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            boolean extOk = false;
            for (String ex : extensions) {
                if (ex == null || ex.isEmpty()) continue;
                if (lower.endsWith(ex)) {
                    extOk = true;
                    break;
                }
            }
            if (!extOk) {
                return;
            }
        }

        long elapsedNanos = System.nanoTime() - startNano;
        long centis = elapsedNanos / 10_000_000L;
        long whole = centis / 100L;
        int cents = (int) (centis % 100L);

        String pathStr = filePath.toAbsolutePath().toString();

        StringBuilder sb = new StringBuilder(32 + pathStr.length());
        sb.append('[').append(whole).append('.');
        if (cents < 10) {
            sb.append('0');
        }
        sb.append(cents).append(" s] ").append(pathStr);
        String formatted = sb.toString();

        result.add(formatted);
        if (matchCount != null) {
            matchCount.incrementAndGet();
        }
        if (emitter != null) {
            try {
                emitter.accept(formatted);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Emitter-Consumer warf eine Ausnahme für Datei " + filePath + ": " + e.getMessage());
            }
        }
    }

    private boolean containsIgnoreCase(final String src, final String target) {
        if (target == null || target.isEmpty()) {
            return true;
        }
        if (src == null || src.length() < target.length()) {
            return false;
        }

        final int sl = src.length();
        final int tl = target.length();
        final int max = sl - tl;
        final boolean ignoreCase = !caseSensitive;
        for (int i = 0; i <= max; i++) {
            if (src.regionMatches(ignoreCase, i, target, 0, tl)) {
                return true;
            }
        }
        return false;
    }

}
