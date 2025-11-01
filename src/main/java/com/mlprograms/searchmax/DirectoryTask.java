package com.mlprograms.searchmax;

import com.mlprograms.searchmax.model.TimeRangeTableModel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
@Getter
@RequiredArgsConstructor
public final class DirectoryTask extends RecursiveAction {

    private record FilterEntity(String pattern, boolean caseSensitive, String patternKey) {
    }

    private static final Set<String> SYSTEM_DIRECTORY_NAMES = Set.of(
            "system volume information", "$recycle.bin", "found.000", "recycler"
    );
    private static final int DIRECTORY_CHUNK_SIZE = 64;
    private static final int TEXT_BUFFER_SIZE = 8 * 1024;
    private static final long NANOSECONDS_PER_CENTISECOND = 10_000_000L;

    private final Path directoryPath;
    private final Collection<String> searchResults;
    private final AtomicInteger matchCount;
    private final String searchQuery;
    private final int searchQueryLength;
    private final long searchStartTimeNano;
    private final AtomicBoolean searchCancelled;
    private final Consumer<String> resultEmitter;
    private final boolean caseSensitiveSearch;
    private final List<String> allowedFileExtensions;
    private final List<String> deniedFileExtensions;
    private final List<String> filenameIncludeFilters;
    private final Map<String, Boolean> filenameIncludeCaseMap;
    private final List<String> filenameExcludeFilters;
    private final Map<String, Boolean> filenameExcludeCaseMap;
    private final boolean filenameIncludeAllMode;
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
            final Collection<String> searchResults,
            final AtomicInteger matchCount,
            final AtomicInteger remainingTasks,
            final String searchQuery,
            final long searchStartTimeNano,
            final Consumer<String> resultEmitter,
            final AtomicBoolean searchCancelled,
            final boolean caseSensitiveSearch,
            final List<String> allowedFileExtensions,
            final List<String> deniedFileExtensions,
            final List<String> filenameIncludeFilters,
            final Map<String, Boolean> filenameIncludeCaseMap,
            final List<String> filenameExcludeFilters,
            final Map<String, Boolean> filenameExcludeCaseMap,
            final boolean filenameIncludeAllMode,
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
        this.searchResults = Optional.ofNullable(searchResults).orElseGet(ConcurrentLinkedQueue::new);
        this.matchCount = matchCount;
        this.searchQuery = Optional.ofNullable(searchQuery).orElse("");
        this.searchQueryLength = this.searchQuery.length();
        this.searchStartTimeNano = searchStartTimeNano;
        this.resultEmitter = resultEmitter;
        this.searchCancelled = Optional.ofNullable(searchCancelled).orElseGet(() -> new AtomicBoolean(false));
        this.caseSensitiveSearch = caseSensitiveSearch;
        this.allowedFileExtensions = copyListIfNotEmpty(allowedFileExtensions);
        this.deniedFileExtensions = copyListIfNotEmpty(deniedFileExtensions);
        this.filenameIncludeFilters = copyListIfNotEmpty(filenameIncludeFilters);
        this.filenameIncludeCaseMap = copyMapIfNotEmpty(filenameIncludeCaseMap);
        this.filenameExcludeFilters = copyListIfNotEmpty(filenameExcludeFilters);
        this.filenameExcludeCaseMap = copyMapIfNotEmpty(filenameExcludeCaseMap);
        this.filenameIncludeAllMode = filenameIncludeAllMode;
        this.contentIncludeFilters = copyListIfNotEmpty(contentIncludeFilters);
        this.contentIncludeCaseMap = copyMapIfNotEmpty(contentIncludeCaseMap);
        this.contentExcludeFilters = copyListIfNotEmpty(contentExcludeFilters);
        this.contentExcludeCaseMap = copyMapIfNotEmpty(contentExcludeCaseMap);
        this.contentIncludeAllMode = contentIncludeAllMode;
        this.timeIncludeRanges = copyListIfNotEmpty(timeIncludeRanges);
        this.timeExcludeRanges = copyListIfNotEmpty(timeExcludeRanges);
        this.timeIncludeAllMode = timeIncludeAllMode;
        this.extractionMode = Optional.ofNullable(extractionMode).orElse(ExtractionMode.POI_THEN_TIKA);
        this.remainingTasks = remainingTasks;
    }

    @Override
    protected void compute() {
        try {
            if (isSearchCancelledOrInvalidDirectory()) {
                return;
            }

            final List<DirectoryTask> subtasks = new ArrayList<>(DIRECTORY_CHUNK_SIZE);
            processDirectoryContents(subtasks);

            if (!subtasks.isEmpty()) {
                invokeAll(subtasks);
            }
        } finally {
            decrementRemainingTasks();
        }
    }

    private void processDirectoryContents(final List<DirectoryTask> subtasks) {
        try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directoryPath)) {
            for (final Path fileOrFolderPath : directoryStream) {
                if (isSearchCancelledOrInvalidDirectory()) {
                    return;
                }
                processFileSystemEntry(fileOrFolderPath, subtasks);
            }
        } catch (final IOException ioException) {
            log.debug("Cannot read directory: {} - {}", directoryPath, ioException.getMessage());
        }
    }

    private void processFileSystemEntry(final Path fileOrFolderPath, final List<DirectoryTask> subtasks) {
        try {
            if (isSystemDirectory(fileOrFolderPath)) {
                return;
            }

            if (Files.isRegularFile(fileOrFolderPath, LinkOption.NOFOLLOW_LINKS)) {
                processFile(fileOrFolderPath);
            } else {
                addSubtaskForDirectory(fileOrFolderPath, subtasks);
            }
        } catch (final SecurityException securityException) {
            log.debug("Access denied for entry: {}", fileOrFolderPath, securityException);
        }
    }

    private void addSubtaskForDirectory(final Path subdirectoryPath, final List<DirectoryTask> subtasks) {
        subtasks.add(createSubtask(subdirectoryPath));
        if (subtasks.size() >= DIRECTORY_CHUNK_SIZE) {
            invokeAll(new ArrayList<>(subtasks));
            subtasks.clear();
        }
    }

    private void processFile(final Path filePath) {
        if (isSearchCancelledOrInvalidDirectory()) {
            return;
        }

        final String fileName = filePath.getFileName().toString();

        if (!passesAllFileFilters(fileName, filePath)) {
            return;
        }

        addFileToResults(filePath);
    }

    private boolean passesAllFileFilters(final String fileName, final Path filePath) {
        return hasSufficientLength(fileName) &&
                matchesSearchQuery(fileName) &&
                matchesFilenameIncludeFilters(fileName) &&
                !matchesFilenameExcludeFilters(fileName) &&
                matchesFileExtensionFilters(fileName) &&
                matchesContentFilters(filePath) &&
                matchesTimeFilters(filePath);
    }

    private boolean hasSufficientLength(final String fileName) {
        return searchQueryLength == 0 || fileName.length() >= searchQueryLength;
    }

    private boolean matchesSearchQuery(final String fileName) {
        if (searchQuery.isEmpty()) {
            return true;
        }

        if (caseSensitiveSearch) {
            return fileName.contains(searchQuery);
        }

        return fileName.toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT));
    }

    private boolean matchesFilenameIncludeFilters(final String fileName) {
        if (filenameIncludeFilters == null || filenameIncludeFilters.isEmpty()) {
            return true;
        }

        final String filenameWithoutExtension = removeFileExtension(fileName);
        return matchesFilters(filenameWithoutExtension, filenameIncludeFilters, filenameIncludeCaseMap, filenameIncludeAllMode);
    }

    private boolean matchesFilenameExcludeFilters(final String fileName) {
        if (filenameExcludeFilters == null || filenameExcludeFilters.isEmpty()) {
            return false;
        }

        final String filenameWithoutExtension = removeFileExtension(fileName);
        return matchesFilters(filenameWithoutExtension, filenameExcludeFilters, filenameExcludeCaseMap, false);
    }

    private String removeFileExtension(final String fileName) {
        if (fileName == null) {
            return "";
        }

        final int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex <= 0) {
            return fileName;
        }

        return fileName.substring(0, lastDotIndex);
    }

    private boolean matchesFilters(final String textToCheck, final List<String> filters,
                                   final Map<String, Boolean> caseSensitivityMap, final boolean requireAllMatches) {
        if (filters == null || filters.isEmpty()) {
            return false;
        }

        if (requireAllMatches) {
            return filters.stream()
                    .filter(Objects::nonNull)
                    .filter(filter -> !filter.isEmpty())
                    .allMatch(filter -> containsText(textToCheck, filter, caseSensitivityMap));
        } else {
            return filters.stream()
                    .filter(Objects::nonNull)
                    .filter(filter -> !filter.isEmpty())
                    .anyMatch(filter -> containsText(textToCheck, filter, caseSensitivityMap));
        }
    }

    private boolean containsText(final String text, final String searchText, final Map<String, Boolean> caseSensitivityMap) {
        final boolean caseSensitive = caseSensitivityMap != null && Boolean.TRUE.equals(caseSensitivityMap.get(searchText));

        if (caseSensitive) {
            return text.contains(searchText);
        } else {
            return text.toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT));
        }
    }

    private boolean matchesFileExtensionFilters(final String fileName) {
        final String fileNameLowercase = fileName.toLowerCase(Locale.ROOT);

        if (deniedFileExtensions != null) {
            final boolean hasDeniedExtension = deniedFileExtensions.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(fileNameLowercase::endsWith);
            if (hasDeniedExtension) {
                return false;
            }
        }

        if (allowedFileExtensions != null) {
            return allowedFileExtensions.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(fileNameLowercase::endsWith);
        }

        return true;
    }

    private boolean matchesContentFilters(final Path filePath) {
        if (hasNoContentFilters()) {
            return true;
        }

        try {
            if (hasContentExcludeFilters() && matchesFileContent(filePath, contentExcludeFilters, contentExcludeCaseMap, false)) {
                return false;
            }

            if (hasContentIncludeFilters()) {
                return matchesFileContent(filePath, contentIncludeFilters, contentIncludeCaseMap, contentIncludeAllMode);
            }

            return true;
        } catch (final Exception exception) {
            log.debug("Content filter check failed for {}: {}", filePath, exception.getMessage());
            return true;
        }
    }

    private boolean hasNoContentFilters() {
        return (contentIncludeFilters == null || contentIncludeFilters.isEmpty()) &&
                (contentExcludeFilters == null || contentExcludeFilters.isEmpty());
    }

    private boolean hasContentIncludeFilters() {
        return contentIncludeFilters != null && !contentIncludeFilters.isEmpty();
    }

    private boolean hasContentExcludeFilters() {
        return contentExcludeFilters != null && !contentExcludeFilters.isEmpty();
    }

    private boolean matchesFileContent(final Path filePath, final List<String> filters,
                                       final Map<String, Boolean> caseMap, final boolean requireAll) {
        final String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);

        if (fileName.endsWith(".pdf")) {
            return matchesPdfContent(filePath, filters, caseMap, requireAll);
        }

        if (isOfficeDocument(fileName)) {
            return searchOfficeDocumentContent(filePath, filters, caseMap, requireAll);
        }

        return searchTextFileContent(filePath, filters, caseMap, requireAll);
    }

    private boolean isOfficeDocument(final String fileNameLowercase) {
        return fileNameLowercase.endsWith(".doc") || fileNameLowercase.endsWith(".docx") ||
                fileNameLowercase.endsWith(".xls") || fileNameLowercase.endsWith(".xlsx") ||
                fileNameLowercase.endsWith(".ppt") || fileNameLowercase.endsWith(".pptx") ||
                fileNameLowercase.endsWith(".odt") || fileNameLowercase.endsWith(".ods") ||
                fileNameLowercase.endsWith(".odp");
    }

    private boolean searchTextFileContent(final Path filePath, final List<String> filters,
                                          final Map<String, Boolean> caseMap, final boolean requireAll) {
        final List<FilterEntity> filterEntities = buildFilterEntities(filters, caseMap);
        if (filterEntities.isEmpty()) {
            return false;
        }

        final int maximumPatternLength = filterEntities.stream()
                .mapToInt(filterEntity -> filterEntity.patternKey.length())
                .max()
                .orElse(0);

        final boolean[] matchedFilters = new boolean[filterEntities.size()];

        try (final Reader fileReader = Files.newBufferedReader(filePath)) {
            return searchTextContent(fileReader, filterEntities, matchedFilters, maximumPatternLength, requireAll);
        } catch (final Exception exception) {
            log.debug("Text file content search failed for {}: {}", filePath, exception.getMessage());
            return false;
        }
    }

    private boolean searchTextContent(final Reader reader, final List<FilterEntity> filterEntities,
                                      final boolean[] matchedFilters, final int maximumPatternLength,
                                      final boolean requireAll) throws IOException {
        final char[] buffer = new char[TEXT_BUFFER_SIZE];
        final StringBuilder slidingWindow = new StringBuilder(Math.max(TEXT_BUFFER_SIZE, maximumPatternLength * 2));
        int bytesRead;

        while ((bytesRead = reader.read(buffer)) != -1) {
            if (isSearchCancelledOrInvalidDirectory()) {
                return false;
            }

            slidingWindow.append(buffer, 0, bytesRead);
            updateMatchedFilters(slidingWindow, filterEntities, matchedFilters);

            if (!requireAll && anyFilterMatched(matchedFilters)) {
                return true;
            }

            trimSlidingWindow(slidingWindow, maximumPatternLength);
        }

        return requireAll ? allFiltersMatched(matchedFilters) : anyFilterMatched(matchedFilters);
    }

    @SneakyThrows
    private boolean matchesPdfContent(final Path filePath, final List<String> filters,
                                      final Map<String, Boolean> caseMap, final boolean requireAll) {
        final List<FilterEntity> filterEntities = buildFilterEntities(filters, caseMap);
        if (filterEntities.isEmpty()) {
            return false;
        }

        final boolean[] matchedFilters = new boolean[filterEntities.size()];
        final PDFTextStripper textStripper = new PDFTextStripper();

        final Logger pdfRootLogger = Logger.getLogger("org.apache.pdfbox");
        final Logger fontLogger = Logger.getLogger("org.apache.pdfbox.pdmodel.font.FileSystemFontProvider");
        final Logger parserLogger = Logger.getLogger("org.apache.pdfbox.pdfparser.BaseParser");

        final Level originalRootLevel = pdfRootLogger.getLevel();
        final Level originalFontLevel = fontLogger.getLevel();
        final Level originalParserLevel = parserLogger.getLevel();

        try {
            setPdfLoggingLevels(Level.SEVERE, pdfRootLogger, fontLogger, parserLogger);

            try (final PDDocument document = Loader.loadPDF(filePath.toFile())) {
                final int totalPages = document.getNumberOfPages();
                for (int currentPage = 1; currentPage <= totalPages; currentPage++) {
                    if (isSearchCancelledOrInvalidDirectory()) {
                        return false;
                    }

                    textStripper.setStartPage(currentPage);
                    textStripper.setEndPage(currentPage);
                    final String pageText = textStripper.getText(document);

                    if (pageText != null && !pageText.isEmpty()) {
                        final StringBuilder textWindow = new StringBuilder(pageText);
                        updateMatchedFilters(textWindow, filterEntities, matchedFilters);

                        if (!requireAll && anyFilterMatched(matchedFilters)) {
                            return true;
                        }
                    }
                }

                return requireAll ? allFiltersMatched(matchedFilters) : anyFilterMatched(matchedFilters);
            }
        } catch (final Exception exception) {
            log.debug("PDF content extraction failed for {}: {}", filePath, exception.getMessage());
            return false;
        } finally {
            restorePdfLoggingLevels(pdfRootLogger, fontLogger, parserLogger,
                    originalRootLevel, originalFontLevel, originalParserLevel);
        }
    }

    private void setPdfLoggingLevels(final Level level, final Logger... loggers) {
        for (final Logger logger : loggers) {
            logger.setLevel(level);
        }
    }

    private void restorePdfLoggingLevels(final Logger pdfRootLogger, final Logger fontLogger,
                                         final Logger parserLogger, final Level originalRootLevel,
                                         final Level originalFontLevel, final Level originalParserLevel) {
        pdfRootLogger.setLevel(originalRootLevel);
        fontLogger.setLevel(originalFontLevel);
        parserLogger.setLevel(originalParserLevel);
    }

    private boolean searchOfficeDocumentContent(final Path filePath, final List<String> filters,
                                                final Map<String, Boolean> caseMap, final boolean requireAll) {
        final List<FilterEntity> filterEntities = buildFilterEntities(filters, caseMap);
        if (filterEntities.isEmpty()) {
            return false;
        }

        return switch (extractionMode) {
            case TIKA_ONLY -> extractWithTikaAndSearch(filePath, filterEntities, requireAll);
            case POI_ONLY -> extractWithPoiAndSearch(filePath, filterEntities, requireAll);
            default -> extractWithPoiThenTika(filePath, filterEntities, requireAll);
        };
    }

    private boolean extractWithTikaAndSearch(final Path filePath, final List<FilterEntity> filterEntities,
                                             final boolean requireAll) {
        try {
            final String extractedText = extractTextWithTika(filePath);
            return searchInExtractedText(extractedText, filterEntities, requireAll);
        } catch (final Exception exception) {
            log.debug("Tika extraction failed for {}: {}", filePath, exception.getMessage());
            return false;
        }
    }

    private boolean extractWithPoiAndSearch(final Path filePath, final List<FilterEntity> filterEntities,
                                            final boolean requireAll) {
        try (final POITextExtractor textExtractor = ExtractorFactory.createExtractor(filePath.toFile())) {
            final String extractedText = textExtractor.getText();
            return searchInExtractedText(extractedText, filterEntities, requireAll);
        } catch (final Exception exception) {
            log.debug("POI extraction failed for {}: {}", filePath, exception.getMessage());
            return false;
        }
    }

    private boolean extractWithPoiThenTika(final Path filePath, final List<FilterEntity> filterEntities,
                                           final boolean requireAll) {
        try (final POITextExtractor textExtractor = ExtractorFactory.createExtractor(filePath.toFile())) {
            final String extractedText = textExtractor.getText();
            if (extractedText != null && !extractedText.isEmpty()) {
                if (searchInExtractedText(extractedText, filterEntities, requireAll)) {
                    return true;
                }
            }
        } catch (final Exception exception) {
            log.debug("POI extraction failed for {}: {}", filePath, exception.getMessage());
        }

        return extractWithTikaAndSearch(filePath, filterEntities, requireAll);
    }

    private String extractTextWithTika(final Path filePath) throws Exception {
        final org.apache.tika.Tika tika = new org.apache.tika.Tika();
        return tika.parseToString(filePath.toFile());
    }

    private boolean searchInExtractedText(final String textContent, final List<FilterEntity> filterEntities,
                                          final boolean requireAll) {
        if (textContent == null || textContent.isEmpty()) {
            return false;
        }

        final boolean[] matchedFilters = new boolean[filterEntities.size()];
        final int maximumPatternLength = filterEntities.stream()
                .mapToInt(filterEntity -> filterEntity.patternKey.length())
                .max()
                .orElse(0);
        final int windowSize = Math.max(TEXT_BUFFER_SIZE, maximumPatternLength * 2);

        int currentPosition = 0;
        while (currentPosition < textContent.length()) {
            if (isSearchCancelledOrInvalidDirectory()) {
                return false;
            }

            final int endPosition = Math.min(textContent.length(), currentPosition + windowSize);
            final String textWindow = textContent.substring(currentPosition, endPosition);
            final StringBuilder stringBuilder = new StringBuilder(textWindow);

            updateMatchedFilters(stringBuilder, filterEntities, matchedFilters);

            if (!requireAll && anyFilterMatched(matchedFilters)) {
                return true;
            }

            currentPosition += Math.max(1, windowSize - maximumPatternLength);
        }

        return requireAll ? allFiltersMatched(matchedFilters) : anyFilterMatched(matchedFilters);
    }

    private boolean matchesTimeFilters(final Path filePath) {
        if (hasNoTimeFilters()) {
            return true;
        }

        try {
            final long lastModifiedTimeMillis = Files.getLastModifiedTime(filePath).toMillis();
            final Long creationTimeMillis = getFileCreationTime(filePath);

            if (matchesTimeExcludeFilters(lastModifiedTimeMillis, creationTimeMillis)) {
                return false;
            }

            return matchesTimeIncludeFilters(lastModifiedTimeMillis, creationTimeMillis);
        } catch (final Exception exception) {
            log.debug("Time filter check failed for {}: {}", filePath, exception.getMessage());
            return true;
        }
    }

    private boolean hasNoTimeFilters() {
        return (timeIncludeRanges == null || timeIncludeRanges.isEmpty()) &&
                (timeExcludeRanges == null || timeExcludeRanges.isEmpty());
    }

    private Long getFileCreationTime(final Path filePath) {
        try {
            return Files.readAttributes(filePath, BasicFileAttributes.class).creationTime().toMillis();
        } catch (final Exception exception) {
            return null;
        }
    }

    private boolean matchesTimeExcludeFilters(final long lastModifiedTimeMillis, final Long creationTimeMillis) {
        if (timeExcludeRanges == null || timeExcludeRanges.isEmpty()) {
            return false;
        }

        logTimeFilterDebug("Exclude", lastModifiedTimeMillis, creationTimeMillis, timeExcludeRanges.size());

        return timeExcludeRanges.stream()
                .filter(Objects::nonNull)
                .filter(TimeRangeTableModel.Entry::isEnabled)
                .anyMatch(entry -> matchesTimeRange(entry, lastModifiedTimeMillis, creationTimeMillis));
    }

    private boolean matchesTimeIncludeFilters(final long lastModifiedTimeMillis, final Long creationTimeMillis) {
        if (timeIncludeRanges == null || timeIncludeRanges.isEmpty()) {
            return true;
        }

        logTimeFilterDebug("Include", lastModifiedTimeMillis, creationTimeMillis, timeIncludeRanges.size());

        if (timeIncludeAllMode) {
            return timeIncludeRanges.stream()
                    .filter(Objects::nonNull)
                    .filter(TimeRangeTableModel.Entry::isEnabled)
                    .allMatch(entry -> matchesTimeRange(entry, lastModifiedTimeMillis, creationTimeMillis));
        } else {
            return timeIncludeRanges.stream()
                    .filter(Objects::nonNull)
                    .filter(TimeRangeTableModel.Entry::isEnabled)
                    .anyMatch(entry -> matchesTimeRange(entry, lastModifiedTimeMillis, creationTimeMillis));
        }
    }

    private boolean matchesTimeRange(final TimeRangeTableModel.Entry timeRange,
                                     final long lastModifiedTimeMillis, final Long creationTimeMillis) {
        final boolean lastModifiedMatches = matchesSingleTimeRange(timeRange, lastModifiedTimeMillis);
        final boolean creationTimeMatches = creationTimeMillis != null && matchesSingleTimeRange(timeRange, creationTimeMillis);

        log.debug("Time range match - mode: {}, start: {}, end: {} -> lastModified: {}, creation: {}",
                timeRange.getMode(), timeRange.getStart(), timeRange.getEnd(),
                lastModifiedMatches, creationTimeMatches);

        return lastModifiedMatches || creationTimeMatches;
    }

    private boolean matchesSingleTimeRange(final TimeRangeTableModel.Entry timeRange, final long timestamp) {
        if (timeRange == null || timeRange.getStart() == null || timeRange.getEnd() == null) {
            return false;
        }

        final ZoneId systemZone = ZoneId.systemDefault();
        final TimeRangeTableModel.Mode rangeMode = Optional.ofNullable(timeRange.getMode())
                .orElse(TimeRangeTableModel.Mode.DATETIME);

        return switch (rangeMode) {
            case DATE -> matchesDateRange(timeRange, timestamp, systemZone);
            case TIME -> matchesTimeRange(timeRange, timestamp, systemZone);
            default -> matchesDateTimeRange(timeRange, timestamp);
        };
    }

    private boolean matchesDateRange(final TimeRangeTableModel.Entry timeRange, final long timestamp, final ZoneId zone) {
        final LocalDate fileDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
        final LocalDate startDate = timeRange.getStart().toInstant().atZone(zone).toLocalDate();
        final LocalDate endDate = timeRange.getEnd().toInstant().atZone(zone).toLocalDate();
        return !fileDate.isBefore(startDate) && !fileDate.isAfter(endDate);
    }

    private boolean matchesTimeRange(final TimeRangeTableModel.Entry timeRange, final long timestamp, final ZoneId zone) {
        final LocalTime fileTime = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime();
        final LocalTime startTime = timeRange.getStart().toInstant().atZone(zone).toLocalTime();
        final LocalTime endTime = timeRange.getEnd().toInstant().atZone(zone).toLocalTime();

        if (!startTime.isAfter(endTime)) {
            return !fileTime.isBefore(startTime) && !fileTime.isAfter(endTime);
        } else {
            return !fileTime.isBefore(startTime) || !fileTime.isAfter(endTime);
        }
    }

    private boolean matchesDateTimeRange(final TimeRangeTableModel.Entry timeRange, final long timestamp) {
        final long startTime = timeRange.getStart().getTime();
        final long endTime = timeRange.getEnd().getTime();
        return timestamp >= startTime && timestamp <= endTime;
    }

    private void logTimeFilterDebug(final String filterType, final long lastModifiedTimeMillis,
                                    final Long creationTimeMillis, final int rangeCount) {
        if (log.isDebugEnabled()) {
            log.debug("Time filter check - type: {}, lastModified: {}, creation: {}, ranges: {}",
                    filterType, lastModifiedTimeMillis, creationTimeMillis, rangeCount);
        }
    }

    private void addFileToResults(final Path filePath) {
        final String formattedResult = formatFileResult(filePath);
        searchResults.add(formattedResult);

        if (matchCount != null) {
            matchCount.incrementAndGet();
        }

        if (resultEmitter != null) {
            try {
                resultEmitter.accept(formattedResult);
            } catch (final Exception exception) {
                log.debug("Result emitter failed for file {}: {}", filePath, exception.getMessage());
            }
        }
    }

    private String formatFileResult(final Path filePath) {
        final long elapsedNanosSinceStart = System.nanoTime() - searchStartTimeNano;
        final long elapsedCentiseconds = elapsedNanosSinceStart / NANOSECONDS_PER_CENTISECOND;
        final long seconds = elapsedCentiseconds / 100L;
        final int centiseconds = (int) (elapsedCentiseconds % 100L);

        final String absolutePath = filePath.toAbsolutePath().toString();
        return String.format("[%d.%02ds] %s", seconds, centiseconds, absolutePath);
    }

    private boolean isSystemDirectory(final Path path) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        final Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }

        final String directoryName = fileName.toString().toLowerCase(Locale.ROOT);
        return SYSTEM_DIRECTORY_NAMES.contains(directoryName) || directoryName.startsWith("windows");
    }

    private boolean isSearchCancelledOrInvalidDirectory() {
        return Thread.currentThread().isInterrupted() ||
                (searchCancelled != null && searchCancelled.get()) ||
                !Files.isDirectory(directoryPath);
    }

    private DirectoryTask createSubtask(final Path subdirectory) {
        return new DirectoryTask(
                subdirectory,
                searchResults,
                matchCount,
                null,
                searchQuery,
                searchStartTimeNano,
                resultEmitter,
                searchCancelled,
                caseSensitiveSearch,
                allowedFileExtensions,
                deniedFileExtensions,
                filenameIncludeFilters,
                filenameIncludeCaseMap,
                filenameExcludeFilters,
                filenameExcludeCaseMap,
                filenameIncludeAllMode,
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

    private void decrementRemainingTasks() {
        if (remainingTasks != null) {
            remainingTasks.decrementAndGet();
        }
    }

    private static <T> List<T> copyListIfNotEmpty(final List<T> originalList) {
        return (originalList == null || originalList.isEmpty()) ? null : new ArrayList<>(originalList);
    }

    private static <K, V> Map<K, V> copyMapIfNotEmpty(final Map<K, V> originalMap) {
        return (originalMap == null || originalMap.isEmpty()) ? null : new HashMap<>(originalMap);
    }

    private List<FilterEntity> buildFilterEntities(final List<String> filters, final Map<String, Boolean> caseSensitivityMap) {
        if (filters == null || filters.isEmpty()) {
            return Collections.emptyList();
        }

        final List<FilterEntity> filterEntities = new ArrayList<>();
        for (final String filter : filters) {
            if (filter == null || filter.trim().isEmpty()) {
                continue;
            }

            final String trimmedFilter = filter.trim();
            final boolean caseSensitive = caseSensitivityMap != null && Boolean.TRUE.equals(caseSensitivityMap.get(trimmedFilter));
            final String patternKey = caseSensitive ? trimmedFilter : trimmedFilter.toLowerCase(Locale.ROOT);

            filterEntities.add(new FilterEntity(trimmedFilter, caseSensitive, patternKey));
        }
        return filterEntities;
    }

    private void updateMatchedFilters(final StringBuilder textWindow, final List<FilterEntity> filterEntities,
                                      final boolean[] matchedFilters) {
        String lowercaseWindow = null;
        String normalWindow = null;

        for (int i = 0; i < filterEntities.size(); i++) {
            if (matchedFilters[i]) {
                continue;
            }

            final FilterEntity filterEntity = filterEntities.get(i);
            if (filterEntity.caseSensitive) {
                if (normalWindow == null) {
                    normalWindow = textWindow.toString();
                }
                if (normalWindow.contains(filterEntity.patternKey)) {
                    matchedFilters[i] = true;
                }
            } else {
                if (lowercaseWindow == null) {
                    lowercaseWindow = textWindow.toString().toLowerCase(Locale.ROOT);
                }
                if (lowercaseWindow.contains(filterEntity.patternKey)) {
                    matchedFilters[i] = true;
                }
            }
        }
    }

    private void trimSlidingWindow(final StringBuilder slidingWindow, final int maximumPatternLength) {
        if (slidingWindow.length() > maximumPatternLength) {
            slidingWindow.delete(0, slidingWindow.length() - maximumPatternLength);
        }
    }

    private boolean anyFilterMatched(final boolean[] matchedFilters) {
        for (final boolean matched : matchedFilters) {
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private boolean allFiltersMatched(final boolean[] matchedFilters) {
        for (final boolean matched : matchedFilters) {
            if (!matched) {
                return false;
            }
        }
        return true;
    }

}