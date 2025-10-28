package com.mlprograms.searchmax;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;

import java.util.logging.Level;
import java.util.logging.Logger;

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
    // Extraction mode (POI only, Tika only, or POI then Tika fallback)
    private final ExtractionMode extractionMode;
    // Zeitfilter
    private final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeIncludeRanges;
    private final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeExcludeRanges;
    private final boolean timeIncludeAllMode;

    public DirectoryTask(final Path directoryPath, final Collection<String> result, final AtomicInteger matchCount, final String query, final long startTimeNano, final Consumer<String> emitter, final AtomicBoolean cancelled, final boolean caseSensitive, final List<String> allowedExtensions, final List<String> deniedExtensions, final List<String> includeFilters, final Map<String, Boolean> includeCaseMap, final List<String> excludeFilters, final Map<String, Boolean> excludeCaseMap, final boolean includeAllMode, final List<String> contentIncludeFilters, final Map<String, Boolean> contentIncludeCaseMap, final List<String> contentExcludeFilters, final Map<String, Boolean> contentExcludeCaseMap, final boolean contentIncludeAllMode, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeIncludeRanges, final java.util.List<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry> timeExcludeRanges, final boolean timeIncludeAllMode, final ExtractionMode extractionMode) {
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
        this.timeIncludeRanges = (timeIncludeRanges == null || timeIncludeRanges.isEmpty()) ? null : new java.util.ArrayList<>(timeIncludeRanges);
        this.timeExcludeRanges = (timeExcludeRanges == null || timeExcludeRanges.isEmpty()) ? null : new java.util.ArrayList<>(timeExcludeRanges);
        this.timeIncludeAllMode = timeIncludeAllMode;
        this.extractionMode = extractionMode == null ? ExtractionMode.POI_THEN_TIKA : extractionMode;
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
        return new DirectoryTask(subDir, result, matchCount, query, startTimeNano, emitter, cancelled, caseSensitive, allowedExtensions, deniedExtensions, includeFilters, includeCaseMap, excludeFilters, excludeCaseMap, includeAllMode, contentIncludeFilters, contentIncludeCaseMap, contentExcludeFilters, contentExcludeCaseMap, contentIncludeAllMode, timeIncludeRanges, timeExcludeRanges, timeIncludeAllMode, extractionMode);
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
        if (!matchesContentFilters(filePath)) {
            return;
        }
        if (!matchesTimeFilters(filePath)) {
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
     * Prüft, ob der Dateiname mit einer erlaubten oder ausgeschlossenen Dateiendung übereinstimmt.
     * <p>
     * - Gibt false zurück, wenn die Dateiendung in der Liste der ausgeschlossenen Endungen enthalten ist.
     * - Gibt true zurück, wenn die Dateiendung in der Liste der erlaubten Endungen enthalten ist.
     * - Gibt true zurück, wenn keine Listen gesetzt sind oder keine Einschränkung zutrifft.
     *
     * @param fileName Der zu prüfende Dateiname
     * @return true, wenn die Dateiendung erlaubt ist, sonst false
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

    /**
     * Prüft, ob der Inhalt einer Datei die definierten Content-Filter (Include/Exclude) erfüllt.
     * <p>
     * - Gibt true zurück, wenn keine Content-Filter gesetzt sind.
     * - Gibt false zurück, wenn ein Exclude-Filter zutrifft.
     * - Gibt true/false je nach Ergebnis der Include-Filter-Prüfung zurück.
     * - Bei Fehlern (z.B. Datei nicht lesbar) wird true zurückgegeben, um die Suche nicht zu blockieren.
     *
     * @param filePath Pfad zur zu prüfenden Datei
     * @return true, wenn die Datei die Content-Filter erfüllt oder keine Filter gesetzt sind, sonst false
     */
    private boolean matchesContentFilters(final Path filePath) {
        if ((contentIncludeFilters == null || contentIncludeFilters.isEmpty()) && (contentExcludeFilters == null || contentExcludeFilters.isEmpty())) {
            return true;
        }

        try {
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

    /**
     * Repräsentiert einen Filter mit zugehörigem Pattern, Case-Sensitivity und dem Schlüssel für den Vergleich.
     */
    private static class FilterEntity {
        String pattern;
        boolean caseSensitive;
        String patternKey;
    }

    /**
     * Prüft, ob der Inhalt der Datei die angegebenen Filter erfüllt.
     * Liest die Datei blockweise und sucht nach den Filter-Strings unter Berücksichtigung der Groß-/Kleinschreibung.
     *
     * @param filePath   Pfad zur zu prüfenden Datei
     * @param filters    Liste der Filter-Strings
     * @param caseMap    Map, die für jeden Filter angibt, ob Groß-/Kleinschreibung beachtet werden soll
     * @param requireAll true, wenn alle Filter erfüllt sein müssen; false, wenn einer genügt
     * @return true, wenn die Filterbedingungen erfüllt sind, sonst false
     */
    private boolean matchesContentStreamFilters(final Path filePath, final List<String> filters, final Map<String, Boolean> caseMap, final boolean requireAll) {
        // Spezialfall: PDF-Dateien mit PDFBox seitenweise extrahieren (speicherschonend)
        final String nameLower = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (nameLower.endsWith(".pdf")) {
            return matchesPdfContent(filePath, filters, caseMap, requireAll);
        }

        // Office- und OpenDocument-Formate: doc/docx/xls/xlsx/ppt/pptx/odt/ods/odp
        if (nameLower.endsWith(".doc") || nameLower.endsWith(".docx") || nameLower.endsWith(".xls") || nameLower.endsWith(".xlsx") || nameLower.endsWith(".ppt") || nameLower.endsWith(".pptx") || nameLower.endsWith(".odt") || nameLower.endsWith(".ods") || nameLower.endsWith(".odp")) {
            return searchOfficeFile(filePath, filters, caseMap, requireAll);
        }
        if (filters == null || filters.isEmpty()) {
            return false;
        }

        List<FilterEntity> filterList = buildFilterEntities(filters, caseMap);
        if (filterList.isEmpty()) {
            return false;
        }

        final int maxLength = filterList.stream().mapToInt(filterEntity -> filterEntity.patternKey.length()).max().orElse(0);
        final int bufferSize = 8 * 1024;
        boolean[] matched = new boolean[filterList.size()];

        try (final java.io.Reader reader = java.nio.file.Files.newBufferedReader(filePath)) {
            final char[] buffer = new char[bufferSize];
            int read;
            final StringBuilder window = new StringBuilder(Math.max(bufferSize, maxLength * 2));

            while ((read = reader.read(buffer)) != -1) {
                if (isCancelledOrInvalid()) {
                    return false;
                }

                window.append(buffer, 0, read);
                updateMatchedFilters(window, filterList, matched);

                if (!requireAll && anyMatched(matched)) {
                    return true;
                }

                trimWindow(window, maxLength);
            }

            return requireAll ? allMatched(matched) : anyMatched(matched);
        } catch (final Exception exception) {
            return false;
        }
    }

    /**
     * Extrahiert PDF-Text seitenweise und prüft die Filter auf jedem Seiten-Chunk.
     * Verwendet MemoryUsageSetting.setupTempFileOnly() um OutOfMemory zu vermeiden.
     */
    @SneakyThrows
    private boolean matchesPdfContent(final Path filePath, final List<String> filters, final Map<String, Boolean> caseMap, final boolean requireAll) {
        List<FilterEntity> filterList = buildFilterEntities(filters, caseMap);
        if (filterList.isEmpty()) {
            return false;
        }

        boolean[] matched = new boolean[filterList.size()];

        final PDFTextStripper stripper = new PDFTextStripper();

        final Logger root = Logger.getLogger("org.apache.pdfbox");
        final Logger fontLogger = Logger.getLogger("org.apache.pdfbox.pdmodel.font.FileSystemFontProvider");
        final Logger parserLogger = Logger.getLogger("org.apache.pdfbox.pdfparser.BaseParser");
        final Level prevRoot = root.getLevel();
        final Level prevFont = fontLogger.getLevel();
        final Level prevParser = parserLogger.getLevel();
        try {
            root.setLevel(Level.SEVERE);
            fontLogger.setLevel(Level.SEVERE);
            parserLogger.setLevel(Level.SEVERE);

            try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
                final int pages = doc.getNumberOfPages();
                for (int p = 1; p <= pages; p++) {
                    if (isCancelledOrInvalid()) {
                        return false;
                    }

                    stripper.setStartPage(p);
                    stripper.setEndPage(p);
                    String pageText = stripper.getText(doc);
                    if (pageText == null || pageText.isEmpty()) {
                        continue;
                    }

                    final StringBuilder window = new StringBuilder(pageText);
                    updateMatchedFilters(window, filterList, matched);

                    if (!requireAll && anyMatched(matched)) {
                        return true;
                    }
                }

                return requireAll ? allMatched(matched) : anyMatched(matched);
            }
        } catch (final Exception e) {
            log.debug("PDF parsing failed for {}: {}", filePath, e.getMessage());
            return false;
        } finally {
            root.setLevel(prevRoot);
            fontLogger.setLevel(prevFont);
            parserLogger.setLevel(prevParser);
        }
    }

    /**
     * Extrahiert Text mit Apache POI (ExtractorFactory) und prüft die Filter.
     * Die Methode ist robust gegenüber Extraktionsfehlern und gibt false zurück, wenn keine Textextraktion möglich ist.
     */
    private boolean searchOfficeFile(final Path filePath, final List<String> filters, final Map<String, Boolean> caseMap, final boolean requireAll) {
        if (filters == null || filters.isEmpty()) return false;
        List<FilterEntity> filterList = buildFilterEntities(filters, caseMap);
        if (filterList.isEmpty()) return false;

        // Decide behavior based on extractionMode
        switch (extractionMode) {
            case TIKA_ONLY: {
                try {
                    String text = extractTextWithTika(filePath);
                    return searchUsingTextString(text, filterList, requireAll);
                } catch (Exception e) {
                    log.debug("Tika extraction failed for {}: {}", filePath, e.getMessage());
                    return false;
                }
            }
            case POI_ONLY: {
                try (POITextExtractor extractor = ExtractorFactory.createExtractor(filePath.toFile())) {
                    String text = extractor.getText();
                    return searchUsingTextString(text, filterList, requireAll);
                } catch (Exception e) {
                    log.debug("POI extraction failed for {}: {}", filePath, e.getMessage());
                    return false;
                }
            }
            case POI_THEN_TIKA:
            default: {
                // Try POI first
                try (POITextExtractor extractor = ExtractorFactory.createExtractor(filePath.toFile())) {
                    String text = extractor.getText();
                    if (text != null && !text.isEmpty()) {
                        if (searchUsingTextString(text, filterList, requireAll)) return true;
                    }
                } catch (Exception e) {
                    log.debug("Office extraction with POI failed for {}: {}. Falling back to Apache Tika.", filePath, e.getMessage());
                }

                // Fallback to Tika
                try {
                    String text = extractTextWithTika(filePath);
                    return searchUsingTextString(text, filterList, requireAll);
                } catch (Exception ex) {
                    log.debug("Tika extraction failed for {}: {}", filePath, ex.getMessage());
                    return false;
                }
            }
        }
    }

    // Helper: search over a large text string using the existing windowed approach
    private boolean searchUsingTextString(final String text, final List<FilterEntity> filterList, final boolean requireAll) {
        if (text == null || text.isEmpty()) return false;
        boolean[] matched = new boolean[filterList.size()];
        final int maxPatternLen = filterList.stream().mapToInt(fe -> fe.patternKey.length()).max().orElse(0);
        final int windowSize = Math.max(8 * 1024, maxPatternLen * 2);

        int pos = 0;
        while (pos < text.length()) {
            if (isCancelledOrInvalid()) return false;
            int end = Math.min(text.length(), pos + windowSize);
            String window = text.substring(pos, end);
            StringBuilder sb = new StringBuilder(window);
            updateMatchedFilters(sb, filterList, matched);

            if (!requireAll && anyMatched(matched)) return true;
            // advance by windowSize - maxPatternLen to keep overlap for matches across boundaries
            pos += Math.max(1, windowSize - maxPatternLen);
        }

        return requireAll ? allMatched(matched) : anyMatched(matched);
    }

    private List<FilterEntity> buildFilterEntities(final List<String> filters, final Map<String, Boolean> caseMap) {
        List<FilterEntity> filterList = new ArrayList<>();
        for (String filter : filters) {
            if (filter == null) continue;
            String trimmedFilter = filter.trim();
            if (trimmedFilter.isEmpty()) continue;
            final FilterEntity filterEntity = new FilterEntity();
            filterEntity.pattern = trimmedFilter;
            filterEntity.caseSensitive = caseMap != null && Boolean.TRUE.equals(caseMap.get(trimmedFilter));
            filterEntity.patternKey = filterEntity.caseSensitive ? trimmedFilter : trimmedFilter.toLowerCase(Locale.ROOT);
            filterList.add(filterEntity);
        }
        return filterList;
    }

    private void updateMatchedFilters(final StringBuilder window, final List<FilterEntity> filterList, final boolean[] matched) {
        String lowerWindow = null;
        String windowStr = null;
        for (int i = 0; i < filterList.size(); i++) {
            if (matched[i]) continue;
            final FilterEntity fe = filterList.get(i);
            if (fe.caseSensitive) {
                if (windowStr == null) windowStr = window.toString();
                if (windowStr.contains(fe.patternKey)) matched[i] = true;
            } else {
                if (lowerWindow == null) lowerWindow = window.toString().toLowerCase(Locale.ROOT);
                if (lowerWindow.contains(fe.patternKey)) matched[i] = true;
            }
        }
    }

    private void trimWindow(final StringBuilder window, final int maxLength) {
        if (window.length() > maxLength) {
            window.delete(0, window.length() - maxLength);
        }
    }

    private boolean anyMatched(final boolean[] matched) {
        for (boolean m : matched) if (m) return true;
        return false;
    }

    private boolean allMatched(final boolean[] matched) {
        for (boolean m : matched) if (!m) return false;
        return true;
    }

    private boolean matchesQuery(final String fileName) {
        return queryLength == 0 || containsIgnoreCase(fileName, query);
    }

    private boolean matchesIncludeFilters(final String fileName) {
        if (includeFilters == null || includeFilters.isEmpty()) return true;
        String base = stripExtension(fileName);
        return matchesFilters(base, includeFilters, includeCaseMap, includeAllMode);
    }

    private boolean matchesExcludeFilters(final String fileName) {
        if (excludeFilters == null || excludeFilters.isEmpty()) return false;
        String base = stripExtension(fileName);
        return matchesFilters(base, excludeFilters, excludeCaseMap, false);
    }

    private boolean matchesFilters(final String nameToCheck, final List<String> filters, final Map<String, Boolean> caseMap, final boolean requireAll) {
        if (filters == null || filters.isEmpty()) return false;
        if (requireAll) {
            for (String filter : filters) {
                if (filter == null || filter.isEmpty()) continue;
                boolean caseSensitiveFilter = caseMap != null && Boolean.TRUE.equals(caseMap.get(filter));
                boolean matched = caseSensitiveFilter ? nameToCheck.contains(filter) : nameToCheck.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
                if (!matched) return false;
            }
            return true;
        } else {
            for (String filter : filters) {
                if (filter == null || filter.isEmpty()) continue;
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

    private String stripExtension(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        if (idx <= 0) return fileName;
        return fileName.substring(0, idx);
    }

    private boolean containsIgnoreCase(final String haystack, final String needle) {
        if (needle == null || needle.isEmpty()) return true;
        if (haystack == null) return false;
        if (caseSensitive) return haystack.contains(needle);
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    // Apache Tika-based extraction helper
    private String extractTextWithTika(final Path filePath) throws Exception {
        org.apache.tika.Tika tika = new org.apache.tika.Tika();
        return tika.parseToString(filePath.toFile());
    }

    /**
     * Prüft, ob eine Datei die konfigurierten Zeitfilter erfüllt.
     */
    private boolean matchesTimeFilters(final Path filePath) {
        if ((timeIncludeRanges == null || timeIncludeRanges.isEmpty()) && (timeExcludeRanges == null || timeExcludeRanges.isEmpty())) {
            return true;
        }

        try {
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();

            if (timeExcludeRanges != null && !timeExcludeRanges.isEmpty()) {
                for (com.mlprograms.searchmax.model.TimeRangeTableModel.Entry r : timeExcludeRanges) {
                    if (r == null || r.start == null || r.end == null) continue;
                    if (lastModified >= r.start.getTime() && lastModified <= r.end.getTime()) return false;
                }
            }

            if (timeIncludeRanges == null || timeIncludeRanges.isEmpty()) {
                return true;
            }

            if (timeIncludeAllMode) {
                for (com.mlprograms.searchmax.model.TimeRangeTableModel.Entry r : timeIncludeRanges) {
                    if (r == null || r.start == null || r.end == null) return false;
                    if (!(lastModified >= r.start.getTime() && lastModified <= r.end.getTime())) return false;
                }
                return true;
            } else {
                for (com.mlprograms.searchmax.model.TimeRangeTableModel.Entry r : timeIncludeRanges) {
                    if (r == null || r.start == null || r.end == null) continue;
                    if (lastModified >= r.start.getTime() && lastModified <= r.end.getTime()) return true;
                }
                return false;
            }
        } catch (Exception e) {
            return true; // don't block search on filesystem errors
        }
    }

}
