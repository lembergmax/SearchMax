package com.mlprograms.searchmax;

import com.mlprograms.searchmax.model.TimeRangeTableModel;
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
import java.nio.file.attribute.BasicFileAttributes;

@Slf4j
@Getter
@RequiredArgsConstructor
public final class DirectoryTask extends RecursiveAction {

    private static class FilterEntity {

        String pattern;
        boolean caseSensitive;
        String patternKey;

    }

    private static final Set<String> SYSTEM_DIR_NAMES = new HashSet<>(Arrays.asList("system volume information", "$recycle.bin", "found.000", "recycler"));
    private static final int CHUNK_SIZE = 64;
    private final Path directoryPath;
    private final Collection<String> result;
    private final AtomicInteger matchCount;
    private final String query;
    private final int queryLength;
    private final long startTimeNano;
    private final AtomicBoolean cancelled;
    private final Consumer<String> emitter;
    private final boolean caseSensitive;
    private final List<String> allowedFileExtensions;
    private final List<String> deniedFileExtensions;
    private final List<String> includeFilters;
    private final Map<String, Boolean> includeCaseMap;
    private final List<String> excludeFilters;
    private final Map<String, Boolean> excludeCaseMap;
    private final boolean includeAllMode;

    private final List<String> contentIncludeFilters;
    private final Map<String, Boolean> contentIncludeCaseMap;
    private final List<String> contentExcludeFilters;
    private final Map<String, Boolean> contentExcludeCaseMap;
    private final boolean contentIncludeAllMode;
    private final ExtractionMode extractionMode;
    private final List<TimeRangeTableModel.Entry> timeIncludeRanges;
    private final List<TimeRangeTableModel.Entry> timeExcludeRanges;
    private final boolean timeIncludeAllMode;
    private final AtomicInteger remainingTasks;

    public DirectoryTask(
            final Path directoryPath,
            final Collection<String> result,
            final AtomicInteger matchCount,
            final AtomicInteger remainingTasks,
            final String query,
            final long startTimeNano,
            final Consumer<String> emitter,
            final AtomicBoolean cancelled,
            final boolean caseSensitive,
            final List<String> allowedFileExtensions,
            final List<String> deniedFileExtensions,
            final List<String> includeFilters,
            final Map<String, Boolean> includeCaseMap,
            final List<String> excludeFilters,
            final Map<String, Boolean> excludeCaseMap,
            final boolean includeAllMode,
            final List<String> contentIncludeFilters,
            final Map<String, Boolean> contentIncludeCaseMap,
            final List<String> contentExcludeFilters,
            final Map<String, Boolean> contentExcludeCaseMap,
            final boolean contentIncludeAllMode,
            final List<TimeRangeTableModel.Entry> timeIncludeRanges,
            final List<TimeRangeTableModel.Entry> timeExcludeRanges,
            final boolean timeIncludeAllMode,
            final ExtractionMode extractionMode
    ) {
        this.directoryPath = directoryPath;
        this.result = (result == null) ? new ConcurrentLinkedQueue<>() : result;
        this.matchCount = matchCount;
        this.query = (query == null) ? "" : query;
        this.queryLength = this.query.length();
        this.startTimeNano = startTimeNano;
        this.emitter = emitter;
        this.cancelled = (cancelled == null) ? new AtomicBoolean(false) : cancelled;
        this.caseSensitive = caseSensitive;

        this.allowedFileExtensions = (allowedFileExtensions == null || allowedFileExtensions.isEmpty()) ? null : new ArrayList<>(allowedFileExtensions);
        this.deniedFileExtensions = (deniedFileExtensions == null || deniedFileExtensions.isEmpty()) ? null : new ArrayList<>(deniedFileExtensions);
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
        this.remainingTasks = remainingTasks;
    }

    @Override
    protected void compute() {
        try {
            if (isCancelledOrInvalid()) {
                return;
            }

            final List<DirectoryTask> subtasks = new ArrayList<>(8);
            try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directoryPath)) {
                for (final Path fileOrFolderPath : directoryStream) {
                    if (isCancelledOrInvalid()) {
                        return;
                    }

                    processFilePath(fileOrFolderPath, subtasks);
                }

            } catch (final IOException ioException) {
                log.debug("Kann Verzeichnis nicht lesen: {} - {}", directoryPath, ioException.getMessage());
            }

            if (!subtasks.isEmpty()) {
                invokeAll(subtasks);
            }
        } finally {
            if (this.remainingTasks != null) {
                this.remainingTasks.decrementAndGet();
            }
        }
    }

    private void processFilePath(Path fileOrFolderPath, List<DirectoryTask> subtasks) {
        try {
            if (isSystemDirectory(fileOrFolderPath)) {
                return;
            }

            if (Files.isRegularFile(fileOrFolderPath, LinkOption.NOFOLLOW_LINKS)) {
                processFile(fileOrFolderPath);
                return;
            }

            subtasks.add(createSubtask(fileOrFolderPath));
            if (subtasks.size() >= CHUNK_SIZE) {
                invokeAll(new ArrayList<>(subtasks));
                subtasks.clear();
            }
        } catch (final SecurityException securityException) {
            log.debug("Zugriff verweigert für Eintrag: {}", fileOrFolderPath, securityException);
        }
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

    private boolean isSystemDirectory(final Path path) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        final Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }

        final String name = fileName.toString().toLowerCase(Locale.ROOT);
        return SYSTEM_DIR_NAMES.contains(name) || name.startsWith("windows");
    }

    private boolean isCancelledOrInvalid() {
        boolean isDirectory = Files.isDirectory(directoryPath);
        boolean isThreadInterrupted = Thread.currentThread().isInterrupted();
        boolean isTaskCancelled = cancelled != null && cancelled.get();
        return isThreadInterrupted || isTaskCancelled || !isDirectory;
    }

    private boolean matchesQuery(final String fileName) {
        if (query == null || query.isEmpty()) {
            return true;
        }

        if (fileName == null) {
            return false;
        }

        if (caseSensitive) {
            return fileName.contains(query);
        }

        return fileName.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private boolean matchesIncludeFilters(final String fileName) {
        if (includeFilters == null || includeFilters.isEmpty()) {
            return true;
        }

        final String base = stripExtension(fileName);
        return matchesFilters(base, includeFilters, includeCaseMap, includeAllMode);
    }

    private boolean matchesExcludeFilters(final String fileName) {
        if (excludeFilters == null || excludeFilters.isEmpty()) {
            return false;
        }

        final String base = stripExtension(fileName);
        return matchesFilters(base, excludeFilters, excludeCaseMap, false);
    }

    private String stripExtension(final String fileName) {
        if (fileName == null) {
            return "";
        }

        int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot <= 0) {
            return fileName;
        }

        return fileName.substring(0, lastIndexOfDot);
    }

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
            for (final String filter : filters) {
                if (filter == null || filter.isEmpty()) {
                    continue;
                }

                boolean caseSensitiveFilter = caseMap != null && Boolean.TRUE.equals(caseMap.get(filter));
                if (caseSensitiveFilter) {
                    return nameToCheck.contains(filter);
                } else {
                    return nameToCheck.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
                }
            }

            return false;
        }
    }

    private DirectoryTask createSubtask(final Path subDir) {
        return new DirectoryTask(
                subDir,
                result,
                matchCount,
                null,
                query,
                startTimeNano,
                emitter,
                cancelled,
                caseSensitive,
                allowedFileExtensions,
                deniedFileExtensions,
                includeFilters,
                includeCaseMap,
                excludeFilters,
                excludeCaseMap,
                includeAllMode,
                contentIncludeFilters,
                contentIncludeCaseMap,
                contentExcludeFilters,
                contentExcludeCaseMap,
                contentIncludeAllMode,
                timeIncludeRanges,
                timeExcludeRanges,
                timeIncludeAllMode,
                extractionMode
        );
    }

    private boolean matchesExtensions(final String fileName) {
        final String lowerInLowercase = fileName.toLowerCase(Locale.ROOT);

        if (deniedFileExtensions != null) {
            for (final String deniedFileExtension : deniedFileExtensions) {
                if (deniedFileExtension != null && !deniedFileExtension.isEmpty() && lowerInLowercase.endsWith(deniedFileExtension)) {
                    return false;
                }
            }
        }

        if (allowedFileExtensions != null) {
            for (final String allowedFileExtension : allowedFileExtensions) {
                if (allowedFileExtension != null && !allowedFileExtension.isEmpty() && lowerInLowercase.endsWith(allowedFileExtension)) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }

    private String formatFileResult(final Path filePath) {
        final long elapsedNanosSinceStart = System.nanoTime() - startTimeNano;
        final long elapsedCentiseconds = elapsedNanosSinceStart / 10_000_000L;
        final long seconds = elapsedCentiseconds / 100L;
        final int centiseconds = (int) (elapsedCentiseconds % 100L);

        final String absolutePath = filePath.toAbsolutePath().toString();
        return String.format("[%d.%02ds] %s", seconds, centiseconds, absolutePath);
    }

// =============================================================
// NOT REFACTORED
// =============================================================

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

    private String extractTextWithTika(final Path filePath) throws Exception {
        org.apache.tika.Tika tika = new org.apache.tika.Tika();
        return tika.parseToString(filePath.toFile());
    }

    private boolean matchesTimeFilters(final Path filePath) {
        if ((timeIncludeRanges == null || timeIncludeRanges.isEmpty()) && (timeExcludeRanges == null || timeExcludeRanges.isEmpty())) {
            return true;
        }

        try {
            final long lastModifiedMillis = Files.getLastModifiedTime(filePath).toMillis();
            Long creationMillis;
            try {
                creationMillis = Files.readAttributes(filePath, BasicFileAttributes.class).creationTime().toMillis();
            } catch (Exception ex) {
                creationMillis = null;
            }

            final java.time.ZoneId zone = java.time.ZoneId.systemDefault();

            final java.util.function.BiPredicate<com.mlprograms.searchmax.model.TimeRangeTableModel.Entry, Long> entryMatchesTimestamp = (r, ts) -> {
                if (r == null || r.start == null || r.end == null || ts == null) return false;
                switch (r.mode == null ? com.mlprograms.searchmax.model.TimeRangeTableModel.Mode.DATETIME : r.mode) {
                    case DATE: {
                        java.time.LocalDate fileDate = java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalDate();
                        java.time.LocalDate startDate = r.start.toInstant().atZone(zone).toLocalDate();
                        java.time.LocalDate endDate = r.end.toInstant().atZone(zone).toLocalDate();
                        return !(fileDate.isBefore(startDate) || fileDate.isAfter(endDate));
                    }
                    case TIME: {
                        java.time.LocalTime fileTime = java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalTime();
                        java.time.LocalTime startTime = r.start.toInstant().atZone(zone).toLocalTime();
                        java.time.LocalTime endTime = r.end.toInstant().atZone(zone).toLocalTime();
                        if (!startTime.isAfter(endTime)) {
                            return !fileTime.isBefore(startTime) && !fileTime.isAfter(endTime);
                        } else {
                            return !fileTime.isBefore(startTime) || !fileTime.isAfter(endTime);
                        }
                    }
                    case DATETIME:
                    default: {
                        long s = r.start.getTime();
                        long e = r.end.getTime();
                        return ts >= s && ts <= e;
                    }
                }
            };

            // Excludes: if any exclude entry matches any relevant timestamp -> exclude
            if (timeExcludeRanges != null && !timeExcludeRanges.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("matchesTimeFilters - file={}, lastModified={}, creation={} , excludeEntries={}", filePath, lastModifiedMillis, creationMillis, timeExcludeRanges.size());
                }
                for (com.mlprograms.searchmax.model.TimeRangeTableModel.Entry r : timeExcludeRanges) {
                    if (r == null || !r.enabled) continue;
                    boolean lmMatch = entryMatchesTimestamp.test(r, lastModifiedMillis);
                    boolean crMatch = creationMillis != null && entryMatchesTimestamp.test(r, creationMillis);
                    if (log.isDebugEnabled()) {
                        log.debug("matchesTimeFilters - exclude entry mode={} start={} end={} -> lmMatch={} crMatch={}", r.mode, r.start, r.end, lmMatch, crMatch);
                    }
                    if (lmMatch || crMatch) return false;
                }
            }

            // If no includes configured, accept the file
            if (timeIncludeRanges == null || timeIncludeRanges.isEmpty()) {
                return true;
            }

            if (timeIncludeAllMode) {
                if (log.isDebugEnabled()) {
                    log.debug("matchesTimeFilters - ANY/ALL mode=ALL, file={}, lastModified={}, creation={}, includeEntries={}", filePath, lastModifiedMillis, creationMillis, timeIncludeRanges.size());
                }
                for (com.mlprograms.searchmax.model.TimeRangeTableModel.Entry r : timeIncludeRanges) {
                    if (r == null || !r.enabled) continue;
                    boolean lmMatch = entryMatchesTimestamp.test(r, lastModifiedMillis);
                    boolean crMatch = creationMillis != null && entryMatchesTimestamp.test(r, creationMillis);
                    if (log.isDebugEnabled()) {
                        log.debug("matchesTimeFilters - include entry mode={} start={} end={} -> lmMatch={} crMatch={}", r.mode, r.start, r.end, lmMatch, crMatch);
                    }
                    boolean matched = lmMatch || crMatch;
                    if (!matched) return false;
                }
                return true;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("matchesTimeFilters - ANY/ALL mode=ANY, file={}, lastModified={}, creation={}, includeEntries={}", filePath, lastModifiedMillis, creationMillis, timeIncludeRanges.size());
                }
                for (com.mlprograms.searchmax.model.TimeRangeTableModel.Entry r : timeIncludeRanges) {
                    if (r == null || !r.enabled) continue;
                    boolean lmMatch = entryMatchesTimestamp.test(r, lastModifiedMillis);
                    boolean crMatch = creationMillis != null && entryMatchesTimestamp.test(r, creationMillis);
                    if (log.isDebugEnabled()) {
                        log.debug("matchesTimeFilters - include entry mode={} start={} end={} -> lmMatch={} crMatch={}", r.mode, r.start, r.end, lmMatch, crMatch);
                    }
                    if (lmMatch || crMatch) return true;
                }
                return false;
            }
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("matchesTimeFilters - filesystem error for {}: {}", filePath, e.getMessage());
            return true; // don't block search on filesystem errors
        }
    }

    // Konsolidierte Hilfsmethoden (einmalig vorhanden):
    private List<FilterEntity> buildFilterEntities(final List<String> filters, final Map<String, Boolean> caseMap) {
        List<FilterEntity> filterList = new ArrayList<>();
        if (filters == null || filters.isEmpty()) return filterList;
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

}
