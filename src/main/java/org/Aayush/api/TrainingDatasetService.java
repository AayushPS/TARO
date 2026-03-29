package org.Aayush.api;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory caller-scoped CSV intake store for training datasets.
 */
@Component
public final class TrainingDatasetService {
    private static final int SAMPLE_ROW_LIMIT = 5;

    private final Clock clock;
    private final LinkedHashMap<String, StoredDataset> datasetsById = new LinkedHashMap<>();

    public TrainingDatasetService(ObjectProvider<Clock> clockProvider) {
        Clock providedClock = clockProvider.getIfAvailable();
        this.clock = providedClock == null ? Clock.systemUTC() : providedClock;
    }

    public synchronized TrainingDatasetResponse uploadDataset(String callerId, MultipartFile file) {
        String normalizedCallerId = requireText(callerId, "callerId");
        MultipartFile nonNullFile = Objects.requireNonNull(file, "file");
        if (nonNullFile.isEmpty()) {
            throw TaroApiException.badRequest("csv file must not be empty");
        }

        String fileName = requireText(nonNullFile.getOriginalFilename(), "fileName");
        byte[] bytes;
        try {
            bytes = nonNullFile.getBytes();
        } catch (IOException exception) {
            throw TaroApiException.badRequest("failed to read csv upload");
        }

        List<List<String>> parsedRows = parseCsv(new String(bytes, StandardCharsets.UTF_8));
        if (parsedRows.isEmpty()) {
            throw TaroApiException.badRequest("csv file must include a header row");
        }

        List<String> headers = normalizeHeaderRow(parsedRows.getFirst());
        validateRowWidths(parsedRows, headers.size());
        if (parsedRows.size() < 2) {
            throw TaroApiException.badRequest("csv file must include at least one data row");
        }

        String datasetId = "dataset-" + UUID.randomUUID();
        Instant now = clock.instant();
        int rowCount = parsedRows.size() - 1;
        int columnCount = headers.size();
        List<List<String>> sampleRows = sampleRows(parsedRows.subList(1, parsedRows.size()), columnCount);
        StoredDataset dataset = new StoredDataset(
                datasetId,
                normalizedCallerId,
                fileName,
                now,
                rowCount,
                columnCount,
                headers,
                sampleRows,
                sha256(bytes)
        );
        datasetsById.put(datasetId, dataset);
        return dataset.toResponse();
    }

    public synchronized List<TrainingDatasetResponse> datasets(String callerId) {
        String normalizedCallerId = requireText(callerId, "callerId");
        List<TrainingDatasetResponse> responses = new ArrayList<>();
        for (StoredDataset dataset : datasetsById.values()) {
            if (normalizedCallerId.equals(dataset.callerId())) {
                responses.add(dataset.toResponse());
            }
        }
        return List.copyOf(responses.reversed());
    }

    synchronized StoredDataset requireDataset(String callerId, String datasetId) {
        String normalizedCallerId = requireText(callerId, "callerId");
        String normalizedDatasetId = requireText(datasetId, "datasetId");
        StoredDataset dataset = datasetsById.get(normalizedDatasetId);
        if (dataset == null || !normalizedCallerId.equals(dataset.callerId())) {
            throw TaroApiException.trainingDatasetNotFound(normalizedDatasetId);
        }
        return dataset;
    }

    synchronized void clear() {
        datasetsById.clear();
    }

    private static List<String> normalizeHeaderRow(List<String> rawHeaders) {
        ArrayList<String> headers = new ArrayList<>();
        for (String rawHeader : rawHeaders) {
            String header = requireText(rawHeader, "header");
            if (headers.contains(header)) {
                throw TaroApiException.badRequest("csv header contains duplicate column: " + header);
            }
            headers.add(header);
        }
        return List.copyOf(headers);
    }

    private static List<List<String>> sampleRows(List<List<String>> rows, int columnCount) {
        ArrayList<List<String>> sample = new ArrayList<>();
        for (int index = 0; index < rows.size() && index < SAMPLE_ROW_LIMIT; index++) {
            ArrayList<String> normalized = new ArrayList<>();
            List<String> row = rows.get(index);
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                normalized.add(columnIndex < row.size() ? row.get(columnIndex).trim() : "");
            }
            sample.add(List.copyOf(normalized));
        }
        return List.copyOf(sample);
    }

    private static List<List<String>> parseCsv(String text) {
        ArrayList<List<String>> rows = new ArrayList<>();
        ArrayList<String> currentRow = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '"') {
                if (inQuotes && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    currentCell.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (value == ',' && !inQuotes) {
                currentRow.add(currentCell.toString());
                currentCell.setLength(0);
                continue;
            }
            if (value == '\n' && !inQuotes) {
                currentRow.add(currentCell.toString());
                appendRow(rows, currentRow);
                currentRow.clear();
                currentCell.setLength(0);
                continue;
            }
            if (value == '\r' && !inQuotes) {
                continue;
            }
            currentCell.append(value);
        }

        if (currentCell.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentCell.toString());
            appendRow(rows, currentRow);
        }
        return List.copyOf(rows);
    }

    private static void appendRow(ArrayList<List<String>> rows, ArrayList<String> currentRow) {
        boolean blank = true;
        ArrayList<String> copy = new ArrayList<>(currentRow.size());
        for (String cell : currentRow) {
            String normalized = cell == null ? "" : cell.trim();
            copy.add(normalized);
            if (!normalized.isEmpty()) {
                blank = false;
            }
        }
        if (!blank) {
            rows.add(List.copyOf(copy));
        }
    }

    private static void validateRowWidths(List<List<String>> rows, int columnCount) {
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.size() != columnCount) {
                throw TaroApiException.badRequest(
                        "csv row " + (index + 1) + " has " + row.size() + " columns but expected " + columnCount
                );
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null) {
            throw TaroApiException.badRequest(fieldName + " must be non-blank");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw TaroApiException.badRequest(fieldName + " must be non-blank");
        }
        return trimmed;
    }

    record StoredDataset(
            String datasetId,
            String callerId,
            String fileName,
            Instant uploadedAt,
            int rowCount,
            int columnCount,
            List<String> headers,
            List<List<String>> sampleRows,
            String sha256
    ) {
        TrainingDatasetResponse toResponse() {
            return new TrainingDatasetResponse(
                    datasetId,
                    fileName,
                    uploadedAt,
                    rowCount,
                    columnCount,
                    headers,
                    sampleRows,
                    sha256
            );
        }
    }
}
