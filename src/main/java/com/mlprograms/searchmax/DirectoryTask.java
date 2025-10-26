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
     * Erstellt eine neue DirectoryTask mit den angegebenen Parametern.
     *
     * @param directoryPath     Das zu durchsuchende Verzeichnis
     * @param result            Sammlung für gefundene Ergebnisse
     * @param matchCount        Zähler für Treffer
     * @param query             Suchbegriff für Dateinamen
     * @param startTimeNano     Startzeitpunkt der Suche (nanoTime)
     * @param emitter           Optionaler Consumer für gefundene Ergebnisse
     * @param cancelled         Abbruch-Flag
     * @param caseSensitive     Groß-/Kleinschreibung bei Suche beachten
     * @param allowedExtensions Liste erlaubter Dateiendungen
     * @param deniedExtensions  Liste ausgeschlossener Dateiendungen
     * @param includeFilters    Liste von Include-Filtern
     * @param includeCaseMap    Case-Sensitivity-Map für Include-Filter
     * @param excludeFilters    Liste von Exclude-Filtern
     * @param excludeCaseMap    Case-Sensitivity-Map für Exclude-Filter
     */
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
        return new DirectoryTask(subDir, result, matchCount, query, startTimeNano, emitter, cancelled, caseSensitive, allowedExtensions, deniedExtensions, includeFilters, includeCaseMap, excludeFilters, excludeCaseMap);
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
        return matchesFilters(fileName, includeFilters, includeCaseMap);
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
        return matchesFilters(fileName, excludeFilters, excludeCaseMap);
    }

    /**
     * Prüft, ob der Dateiname mit einem der Filter übereinstimmt.
     *
     * @param fileName Dateiname
     * @param filters  Liste der Filter
     * @param caseMap  Map für Case-Sensitivity je Filter
     * @return true, wenn mindestens ein Filter passt, sonst false
     */
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

    /**
     * Prüft, ob der Dateiname eine erlaubte Endung hat und keine ausgeschlossene.
     *
     * @param fileName Dateiname
     * @return true, wenn erlaubt, sonst false
     */
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
     * Formatiert das Ergebnis für eine gefundene Datei inkl. Zeitstempel.
     *
     * @param filePath Pfad zur Datei
     * @return Formatierter String für das Ergebnis
     */
    private String formatFileResult(final Path filePath) {
        final long elapsedNanos = System.nanoTime() - startTimeNano;
        final long centis = elapsedNanos / 10_000_000L;
        final long whole = centis / 100L;
        final int cents = (int) (centis % 100L);

        final String pathString = filePath.toAbsolutePath().toString();
        return String.format("[%d.%02d s] %s", whole, cents, pathString);
    }

    /**
     * Prüft, ob der Quellstring das Ziel enthält, optional ohne Beachtung der Groß-/Kleinschreibung.
     *
     * @param source Quellstring
     * @param target Zielstring
     * @return true, wenn enthalten, sonst false
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
