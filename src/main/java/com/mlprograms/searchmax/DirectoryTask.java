package com.mlprograms.searchmax;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@Getter
@RequiredArgsConstructor
public final class DirectoryTask extends RecursiveAction {

    private static final Set<String> SYSTEM_DIR_NAMES = new HashSet<>(Arrays.asList("system volume information", "$recycle.bin", "found.000", "recycler"));

    private static final int CHUNK_SIZE = 64;

    private final Path directoryPath;
    private final Collection<String> result;
    private final AtomicInteger matchCount;
    private final String query;
    private final int queryLength;
    private final long startTimeNano;
    private final Consumer<String> emitter;
    private final AtomicBoolean cancelled;
    private final boolean caseSensitive;
    private final List<String> allowedExtensions;
    private final List<String> deniedExtensions;
    private final List<String> includeFilters;
    private final Map<String, Boolean> includeCaseMap;
    private final List<String> excludeFilters;
    private final Map<String, Boolean> excludeCaseMap;

    public DirectoryTask(final Path directoryPath, final Collection<String> result, final AtomicInteger matchCount, final String query, final long startTimeNano, final Consumer<String> emitter, final AtomicBoolean cancelled, final boolean caseSensitive, final List<String> allowedExtensions, final List<String> deniedExtensions, final List<String> includeFilters, final Map<String, Boolean> includeCaseMap, final List<String> excludeFilters, final Map<String, Boolean> excludeCaseMap) {
        this.directoryPath = directoryPath;
        this.result = (result == null) ? new ConcurrentLinkedQueue<>() : result;
        this.matchCount = matchCount;
        this.query = (query == null) ? "" : query;
        this.queryLength = this.query.length();
        this.startTimeNano = startTimeNano;
        this.emitter = emitter;
        this.cancelled = (cancelled == null) ? new AtomicBoolean(false) : cancelled;
        this.caseSensitive = caseSensitive;

        this.allowedExtensions = (allowedExtensions == null || allowedExtensions.isEmpty()) ? null : new ArrayList<>(allowedExtensions);
        this.deniedExtensions = (deniedExtensions == null || deniedExtensions.isEmpty()) ? null : new ArrayList<>(deniedExtensions);
        this.includeFilters = (includeFilters == null || includeFilters.isEmpty()) ? null : new ArrayList<>(includeFilters);
        this.includeCaseMap = (includeCaseMap == null || includeCaseMap.isEmpty()) ? null : new HashMap<>(includeCaseMap);
        this.excludeFilters = (excludeFilters == null || excludeFilters.isEmpty()) ? null : new ArrayList<>(excludeFilters);
        this.excludeCaseMap = (excludeCaseMap == null || excludeCaseMap.isEmpty()) ? null : new HashMap<>(excludeCaseMap);
    }

    @Override
    protected void compute() {
        if (isCancelledOrInvalid()) {
            return;
        }

        final List<DirectoryTask> subtasks = new ArrayList<>(8);
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
            for (final Path entry : stream) {
                if (isCancelledOrInvalid()) {
                    return;
                }

                try {
                    if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                        if (isSystemDirectory(entry)) {
                            continue;
                        }

                        subtasks.add(createSubtask(entry));
                        if (subtasks.size() >= CHUNK_SIZE) {
                            invokeAll(new ArrayList<>(subtasks));
                            subtasks.clear();
                        }

                    } else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                        processFile(entry);
                    }

                } catch (final SecurityException securityException) {
                    log.debug("Zugriff verweigert für Eintrag: {}", entry, securityException);
                }
            }

        } catch (final IOException ioException) {
            log.debug("Kann Verzeichnis nicht lesen: {} - {}", directoryPath, ioException.getMessage());
        }

        if (!subtasks.isEmpty()) {
            invokeAll(subtasks);
        }
    }

    private boolean isCancelledOrInvalid() {
        return Thread.currentThread().isInterrupted() || (cancelled != null && cancelled.get()) || directoryPath == null || !Files.isDirectory(directoryPath);
    }

    private DirectoryTask createSubtask(Path subDir) {
        return new DirectoryTask(subDir, result, matchCount, query, startTimeNano, emitter, cancelled, caseSensitive, allowedExtensions, deniedExtensions, includeFilters, includeCaseMap, excludeFilters, excludeCaseMap);
    }

    private boolean isSystemDirectory(final Path path) {
        final Path namePath = path.getFileName();
        if (namePath == null) {
            return false;
        }

        final String name = namePath.toString().toLowerCase(Locale.ROOT);
        return SYSTEM_DIR_NAMES.contains(name) || name.startsWith("windows");
    }

    private void processFile(final Path filePath) {
        if (isCancelledOrInvalid()) {
            return;
        }

        final String fileName = filePath.getFileName().toString();
        if (queryLength > 0 && fileName.length() < queryLength) {
            return;
        }

        if (!matchesQuery(fileName)) {
            return;
        }
        if (!matchesIncludeFilters(fileName)) {
            return;
        }
        if (matchesExcludeFilters(fileName)) {
            return;
        }
        if (!matchesExtensions(fileName)) {
            return;
        }

        final String formatted = formatFileResult(filePath);
        result.add(formatted);

        if (matchCount != null) {
            matchCount.incrementAndGet();
        }

        if (emitter != null) {
            try {
                emitter.accept(formatted);
            } catch (final Exception exception) {
                log.debug("Emitter-Consumer warf Ausnahme für Datei {}: {}", filePath, exception.getMessage());
            }
        }
    }

    private boolean matchesQuery(final String fileName) {
        return queryLength == 0 || containsIgnoreCase(fileName, query);
    }

    private boolean matchesIncludeFilters(final String fileName) {
        if (includeFilters == null || includeFilters.isEmpty()) {
            return true;
        }
        return matchesFilters(fileName, includeFilters, includeCaseMap);
    }

    private boolean matchesExcludeFilters(final String fileName) {
        if (excludeFilters == null || excludeFilters.isEmpty()) {
            return false;
        }
        return matchesFilters(fileName, excludeFilters, excludeCaseMap);
    }

    private static boolean matchesFilters(final String fileName, final List<String> filters, final Map<String, Boolean> caseMap) {
        for (String filter : filters) {
            if (filter == null || filter.isEmpty()) {
                continue;
            }

            boolean caseSensitiveFilter = caseMap != null && Boolean.TRUE.equals(caseMap.get(filter));
            if ((caseSensitiveFilter && fileName.contains(filter)) || (!caseSensitiveFilter && fileName.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)))) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesExtensions(final String fileName) {
        final String lower = fileName.toLowerCase(Locale.ROOT);

        if (deniedExtensions != null) {
            for (String extension : deniedExtensions) {
                if (extension != null && !extension.isEmpty() && lower.endsWith(extension)) {
                    return false;
                }
            }
        }

        if (allowedExtensions != null) {
            for (String extension : allowedExtensions) {
                if (extension != null && !extension.isEmpty() && lower.endsWith(extension)) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    private String formatFileResult(final Path filePath) {
        final long elapsedNanos = System.nanoTime() - startTimeNano;
        final long centis = elapsedNanos / 10_000_000L;
        final long whole = centis / 100L;
        final int cents = (int) (centis % 100L);

        final String pathString = filePath.toAbsolutePath().toString();
        return String.format("[%d.%02d s] %s", whole, cents, pathString);
    }

    private boolean containsIgnoreCase(final String source, final String target) {
        if (target == null || target.isEmpty()) {
            return true;
        }
        if (source == null || source.length() < target.length()) {
            return false;
        }

        final boolean ignoreCase = !caseSensitive;
        final int targetLength = target.length();
        final int max = source.length() - targetLength;

        for (int i = 0; i <= max; i++) {
            if (source.regionMatches(ignoreCase, i, target, 0, targetLength)) {
                return true;
            }
        }

        return false;
    }

}
