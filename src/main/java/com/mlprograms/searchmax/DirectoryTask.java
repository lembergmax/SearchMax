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

    public DirectoryTask(Path directoryPath, Collection<String> result, AtomicInteger matchCount, String query, long startNano, Consumer<String> emitter) {
        this.directoryPath = directoryPath;
        this.result = result == null ? new ConcurrentLinkedQueue<>() : result;
        this.matchCount = matchCount;
        this.query = (query == null) ? "" : query;
        this.queryLen = this.query.length();
        this.startNano = startNano;
        this.emitter = emitter;
        this.cancelled = new AtomicBoolean(false);
    }

    // Neuer Konstruktor, der ein externes Abbruch-Flag verwendet
    public DirectoryTask(Path directoryPath, Collection<String> result, AtomicInteger matchCount, String query, long startNano, Consumer<String> emitter, AtomicBoolean cancelled) {
        this.directoryPath = directoryPath;
        this.result = result == null ? new ConcurrentLinkedQueue<>() : result;
        this.matchCount = matchCount;
        this.query = (query == null) ? "" : query;
        this.queryLen = this.query.length();
        this.startNano = startNano;
        this.emitter = emitter;
        this.cancelled = cancelled == null ? new AtomicBoolean(false) : cancelled;
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
                        subtasks.add(new DirectoryTask(entry, result, matchCount, query, startNano, emitter, cancelled));
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
            return;
        }
        if (fileName.length() < queryLen) {
            return;
        }

        if (containsIgnoreCase(fileName, query)) {
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
        for (int i = 0; i <= max; i++) {
            if (src.regionMatches(true, i, target, 0, tl)) {
                return true;
            }
        }
        return false;
    }

}
