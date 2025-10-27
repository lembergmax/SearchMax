package com.mlprograms.searchmax;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Durchsucht ein Verzeichnis rekursiv nach Dateien, die bestimmten Filterkriterien entsprechen.
 * Unterstützt parallele Verarbeitung durch Fork/Join-Framework.
 * <p>
 * Filterkriterien umfassen:
 * - Dateinamen-Query (mit/ohne Groß-/Kleinschreibung)
 * - erlaubte/ausgeschlossene Dateiendungen
 * - Include-/Exclude-Filter mit optionaler Case-Sensitivity
 * <p>
 * Ergebnisse werden in einer Collection gesammelt und optional an einen Consumer übergeben.
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public final class DirectoryTask extends RecursiveAction {

    /**
     * Namen von Systemverzeichnissen, die übersprungen werden sollen.
     */
    private static final Set<String> SYSTEM_DIR_NAMES = new HashSet<>(Arrays.asList("system volume information", "$recycle.bin", "found.000", "recycler"));

    /**
     * Maximale Anzahl an Subtasks, bevor invokeAll aufgerufen wird.
     */
    private static final int CHUNK_SIZE = 64;

    /**
     * Das zu durchsuchende Verzeichnis.
     */
    private final Path directoryPath;

    /**
     * Sammlung für gefundene Ergebnisse.
     */
    private final Collection<String> result;

    /**
     * Zähler für Treffer.
     */
    private final AtomicInteger matchCount;

    /**
     * Suchbegriff für Dateinamen.
     */
    private final String query;

    /**
     * Länge des Suchbegriffs.
     */
    private final int queryLength;

    /**
     * Startzeitpunkt der Suche (nanoTime).
     */
    private final long startTimeNano;

    /**
     * Optionaler Consumer für gefundene Ergebnisse.
     */
    private final Consumer<String> emitter;

    /**
     * Abbruch-Flag.
     */
    private final AtomicBoolean cancelled;

    /**
     * Groß-/Kleinschreibung bei Suche beachten.
     */
    private final boolean caseSensitive;

    /**
     * Liste erlaubter Dateiendungen.
     */
    private final List<String> allowedExtensions;

    /**
     * Liste ausgeschlossener Dateiendungen.
     */
    private final List<String> deniedExtensions;

    /**
     * Liste von Include-Filtern.
     */
    private final List<String> includeFilters;

    /**
     * Case-Sensitivity-Map für Include-Filter.
     */
    private final Map<String, Boolean> includeCaseMap;

    /**
     * Liste von Exclude-Filtern.
     */
    private final List<String> excludeFilters;

    /**
     * Case-Sensitivity-Map für Exclude-Filter.
     */
    private final Map<String, Boolean> excludeCaseMap;

    /**
     * Ob alle Include-Filter erfüllt sein müssen (true) oder ob nur einer passen darf (false).
     */
    private final boolean includeAllMode;

    private final List<String> contentIncludeFilters;
    private final Map<String, Boolean> contentIncludeCaseMap;
    private final List<String> contentExcludeFilters;
    private final Map<String, Boolean> contentExcludeCaseMap;
    private final boolean contentIncludeAllMode;

    public DirectoryTask(final Path directoryPath, final Collection<String> result, final AtomicInteger matchCount, final String query, final long startTimeNano, final Consumer<String> emitter, final AtomicBoolean cancelled, final boolean caseSensitive, final List<String> allowedExtensions, final List<String> deniedExtensions, final List<String> includeFilters, final Map<String, Boolean> includeCaseMap, final List<String> excludeFilters, final Map<String, Boolean> excludeCaseMap, final boolean includeAllMode, final List<String> contentIncludeFilters, final Map<String, Boolean> contentIncludeCaseMap, final List<String> contentExcludeFilters, final Map<String, Boolean> contentExcludeCaseMap, final boolean contentIncludeAllMode) {
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
        this.includeAllMode = includeAllMode;

        this.contentIncludeFilters = (contentIncludeFilters == null || contentIncludeFilters.isEmpty()) ? null : new ArrayList<>(contentIncludeFilters);
        this.contentIncludeCaseMap = (contentIncludeCaseMap == null || contentIncludeCaseMap.isEmpty()) ? null : new HashMap<>(contentIncludeCaseMap);
        this.contentExcludeFilters = (contentExcludeFilters == null || contentExcludeFilters.isEmpty()) ? null : new ArrayList<>(contentExcludeFilters);
        this.contentExcludeCaseMap = (contentExcludeCaseMap == null || contentExcludeCaseMap.isEmpty()) ? null : new HashMap<>(contentExcludeCaseMap);
        this.contentIncludeAllMode = contentIncludeAllMode;
    }

    /**
     * Startet die rekursive Verarbeitung des Verzeichnisses.
     * Erstellt Subtasks für Unterverzeichnisse und verarbeitet Dateien.
     */
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

    /**
     * Prüft, ob die Aufgabe abgebrochen wurde oder das Verzeichnis ungültig ist.
     *
     * @return true, wenn abgebrochen oder ungültig, sonst false
     */
    private boolean isCancelledOrInvalid() {
        return Thread.currentThread().isInterrupted() || (cancelled != null && cancelled.get()) || directoryPath == null || !Files.isDirectory(directoryPath);
    }

    /**
     * Erstellt einen neuen Subtask für ein Unterverzeichnis.
     *
     * @param subDir Das Unterverzeichnis
     * @return Neuer DirectoryTask für das Unterverzeichnis
     */
    private DirectoryTask createSubtask(Path subDir) {
        return new DirectoryTask(subDir, result, matchCount, query, startTimeNano, emitter, cancelled, caseSensitive, allowedExtensions, deniedExtensions, includeFilters, includeCaseMap, excludeFilters, excludeCaseMap, includeAllMode, contentIncludeFilters, contentIncludeCaseMap, contentExcludeFilters, contentExcludeCaseMap, contentIncludeAllMode);
    }

    /**
     * Prüft, ob ein Verzeichnis als Systemverzeichnis gilt und übersprungen werden soll.
     *
     * @param path Zu prüfender Pfad
     * @return true, wenn Systemverzeichnis, sonst false
     */
    private boolean isSystemDirectory(final Path path) {
        final Path namePath = path.getFileName();
        if (namePath == null) {
            return false;
        }

        final String name = namePath.toString().toLowerCase(Locale.ROOT);
        return SYSTEM_DIR_NAMES.contains(name) || name.startsWith("windows");
    }

    /**
     * Prüft und verarbeitet eine Datei, falls sie den Filterkriterien entspricht.
     *
     * @param filePath Pfad zur Datei
     */
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
        // Neue Prüfung: Inhalt-Filter
        if (!matchesContentFilters(filePath)) {
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

    /**
     * Formatiert das Suchergebnis einer Datei als String mit Zeitstempel.
     *
     * @param filePath Pfad zur gefundenen Datei
     * @return Formatierter String mit Zeitangabe und Dateipfad
     */
    private String formatFileResult(final Path filePath) {
        final long elapsedNanos = System.nanoTime() - startTimeNano;
        final long centis = elapsedNanos / 10_000_000L;
        final long whole = centis / 100L;
        final int cents = (int) (centis % 100L);

        final String pathString = filePath.toAbsolutePath().toString();
        return String.format("[%d.%02ds] %s", whole, cents, pathString);
    }

    private boolean matchesContentFilters(final Path filePath) {
        if ((contentIncludeFilters == null || contentIncludeFilters.isEmpty()) && (contentExcludeFilters == null || contentExcludeFilters.isEmpty())) {
            return true;
        }

        try {
            // TODO: irgendwie anders einlesen damit alle dateien eingelesen werden
            long size = Files.size(filePath);
            if (size > 50 * 1024 * 1024) {
                return true;
            }

            if (contentExcludeFilters != null && !contentExcludeFilters.isEmpty()) {
                if (matchesContentStreamFilters(filePath, contentExcludeFilters, contentExcludeCaseMap, false)) {
                    return false;
                }
            }

            if (contentIncludeFilters != null && !contentIncludeFilters.isEmpty()) {
                return matchesContentStreamFilters(filePath, contentIncludeFilters, contentIncludeCaseMap, contentIncludeAllMode);
            }

            return true;
        } catch (final Exception exception) {
            return true;
        }
    }

    private static class FilterEntity {
        String pattern;
        boolean caseSensitive;
        String patternKey;
    }

    private boolean matchesContentStreamFilters(final Path filePath, final List<String> filters, final Map<String, Boolean> caseMap, final boolean requireAll) {
        if (filters == null || filters.isEmpty()) {
            return false;
        }

        List<FilterEntity> filterList = new java.util.ArrayList<>();
        int maxLength = 0;
        for (String filter : filters) {
            if (filter == null) {
                continue;
            }

            String trimmedFilter = filter.trim();
            if (trimmedFilter.isEmpty()) {
                continue;
            }

            FilterEntity filterEntity = new FilterEntity();
            filterEntity.pattern = trimmedFilter;
            filterEntity.caseSensitive = caseMap != null && Boolean.TRUE.equals(caseMap.get(trimmedFilter));
            filterEntity.patternKey = filterEntity.caseSensitive ? trimmedFilter : trimmedFilter.toLowerCase(java.util.Locale.ROOT);
            filterList.add(filterEntity);

            if (filterEntity.patternKey.length() > maxLength) {
                maxLength = filterEntity.patternKey.length();
            }
        }

        if (filterList.isEmpty()) {
            return false;
        }

        boolean[] matched = new boolean[filterList.size()];
        try (final BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            String tail = "";

            while ((line = reader.readLine()) != null) {
                if (isCancelledOrInvalid()) {
                    return false;
                }

                String chunk = tail + "\n" + line;
                String lowerChunk = null;

                for (int i = 0; i < filterList.size(); i++) {
                    if (matched[i]) {
                        continue;
                    }

                    FilterEntity filterEntity = filterList.get(i);
                    boolean found;
                    if (filterEntity.caseSensitive) {
                        found = chunk.contains(filterEntity.patternKey);
                    } else {
                        if (lowerChunk == null) lowerChunk = chunk.toLowerCase(Locale.ROOT);
                        found = lowerChunk.contains(filterEntity.patternKey);
                    }
                    if (found) {
                        matched[i] = true;
                    }
                }

                if (!requireAll) {
                    for (boolean bool : matched) {
                        if (bool) return true;
                    }
                }

                if (chunk.length() > maxLength) {
                    tail = chunk.substring(chunk.length() - /*TODO:*/ Math.min(maxLength, maxLength));
                } else {
                    tail = chunk;
                }
            }

            if (requireAll) {
                for (boolean match : matched) {
                    if (!match) {
                        return false;
                    }
                }

                return true;
            } else {
                for (boolean match : matched) {
                    if (match) {
                        return true;
                    }
                }

                return false;
            }
        } catch (final Exception exception) {
            return false;
        }
    }

    /**
     * Prüft, ob der Dateiname die Query enthält.
     *
     * @param fileName Dateiname
     * @return true, wenn Query enthalten ist oder leer, sonst false
     */
    private boolean matchesQuery(final String fileName) {
        return queryLength == 0 || containsIgnoreCase(fileName, query);
    }

    /**
     * Prüft, ob der Dateiname einen der Include-Filter erfüllt.
     *
     * @param fileName Dateiname
     * @return true, wenn kein Filter gesetzt oder mindestens ein Filter passt
     */
    private boolean matchesIncludeFilters(final String fileName) {
        if (includeFilters == null || includeFilters.isEmpty()) {
            return true;
        }
        String base = stripExtension(fileName);
        return matchesFilters(base, includeFilters, includeCaseMap, includeAllMode);
    }

    /**
     * Prüft, ob der Dateiname einen der Exclude-Filter erfüllt.
     *
     * @param fileName Dateiname
     * @return true, wenn mindestens ein Exclude-Filter passt, sonst false
     */
    private boolean matchesExcludeFilters(final String fileName) {
        if (excludeFilters == null || excludeFilters.isEmpty()) {
            return false;
        }
        String base = stripExtension(fileName);
        return matchesFilters(base, excludeFilters, excludeCaseMap, false);
    }

    /**
     * Prüft, ob der Dateiname mit einem der Filter übereinstimmt.
     *
     * @param nameToCheck Zu prüfender Name (ohne Pfad)
     * @param filters     Liste der Filter
     * @param caseMap     Map für Case-Sensitivity je Filter
     * @param requireAll  Ob alle Filter erfüllt sein müssen
     * @return true, wenn mindestens ein Filter passt, sonst false
     */
    private boolean matchesFilters(final String nameToCheck, final List<String> filters, final Map<String, Boolean> caseMap, final boolean requireAll) {
        if (filters == null || filters.isEmpty()) {
            return false;
        }

        if (requireAll) {
            for (String filter : filters) {
                if (filter == null || filter.isEmpty()) {
                    continue;
                }

                boolean caseSensitiveFilter = caseMap != null && Boolean.TRUE.equals(caseMap.get(filter));
                boolean matched = caseSensitiveFilter ? nameToCheck.contains(filter) : nameToCheck.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
                if (!matched) {
                    return false;
                }
            }

            return true;
        } else {
            for (String filter : filters) {
                if (filter == null || filter.isEmpty()) {
                    continue;
                }

                boolean caseSensitiveFilter = caseMap != null && Boolean.TRUE.equals(caseMap.get(filter));
                if (caseSensitiveFilter) {
                    if (nameToCheck.contains(filter)) return true;
                } else {
                    if (nameToCheck.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) return true;
                }
            }
            return false;
        }
    }

    /**
     * Entfernt die Dateiendung aus dem Dateinamen, falls vorhanden.
     *
     * @param fileName Dateiname
     * @return Dateiname ohne Endung
     */
    private String stripExtension(String fileName) {
        if (fileName == null) return "";
        int index = fileName.lastIndexOf('.');
        if (index > 0) {
            return fileName.substring(0, index);
        }
        return fileName;
    }

    /**
     * Prüft, ob der Quell-String das Ziel-Substring enthält, unter Berücksichtigung der Groß-/Kleinschreibung
     * gemäß der Einstellung der Instanz (caseSensitive).
     *
     * @param source Der zu durchsuchende Quell-String
     * @param target Das zu suchende Substring
     * @return true, wenn das Ziel-Substring im Quell-String enthalten ist (ggf. ohne Beachtung der Groß-/Kleinschreibung), sonst false
     */
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
