package io.picota.backend.control.training;

import io.picota.backend.control.commands.UiCommandException;
import io.picota.backend.control.ui.schemas.DigitalSubject;
import io.picota.backend.control.ui.schemas.TimeBucket;
import io.picota.backend.control.ui.schemas.Variable;
import io.picota.backend.control.ui.schemas.VariableType;
import io.picota.backend.persistence.DatasetStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

public class TrainingDatasetPreparer {
	private final DatasetStorage datasetStorage;

	public TrainingDatasetPreparer(DatasetStorage datasetStorage) {
		this.datasetStorage = datasetStorage == null ? DatasetStorage.noOp() : datasetStorage;
	}

	public Path prepareSubjectTrainingDataset(
			String twinId,
			String twinVersion,
			DigitalSubject subject,
			String outputColumn,
			List<String> inputColumns,
			Path uploadedDatasetPath,
			TimeBucket timeBucket
	) {
		if (subject == null) {
			throw new UiCommandException(422, "PRECONDITION_FAILED", "Subject is required to prepare training dataset");
		}
		String target = trimToNull(outputColumn);
		if (target == null) {
			throw new UiCommandException(422, "PRECONDITION_FAILED", "Output variable is required to prepare training dataset");
		}
		if (uploadedDatasetPath == null) {
			throw new UiCommandException(422, "PRECONDITION_FAILED", "Uploaded dataset path is required");
		}
		List<String> orderedColumns = orderedDistinctColumns(inputColumns, target);
		ParsedDataset parsedDataset = parseUploadedDataset(uploadedDatasetPath, timeBucket);
		NavigableSet<Instant> candidateInstants = new TreeSet<>();
		Map<String, NavigableMap<Instant, String>> samplesByColumn = new LinkedHashMap<>();
		collectUploadedSamples(parsedDataset, samplesByColumn, candidateInstants);
		collectIngestedSamples(
				twinId,
				twinVersion,
				subject,
				normalizedSet(orderedColumns),
				samplesByColumn,
				candidateInstants
		);

		Duration tolerance = joinTolerance(timeBucket);
		List<JoinedRow> joinedRows = joinCompleteRows(candidateInstants, samplesByColumn, orderedColumns, tolerance);
		if (joinedRows.isEmpty()) {
			joinedRows = fallbackToUploadedRows(parsedDataset, orderedColumns);
		}
		if (joinedRows.isEmpty()) {
			throw new UiCommandException(
					422,
					"PRECONDITION_FAILED",
					"Unable to create complete training rows by joining uploaded and ingested data"
			);
		}
		return writePreparedCsv(uploadedDatasetPath, joinedRows, orderedColumns);
	}

	private static List<String> orderedDistinctColumns(List<String> inputColumns, String outputColumn) {
		LinkedHashMap<String, String> columns = new LinkedHashMap<>();
		if (inputColumns != null) {
			for (String column : inputColumns) {
				String normalized = normalizeKey(column);
				if (normalized == null) continue;
				columns.putIfAbsent(normalized, column.trim());
			}
		}
		String normalizedOutput = normalizeKey(outputColumn);
		if (normalizedOutput != null) {
			columns.putIfAbsent(normalizedOutput, outputColumn.trim());
		}
		return columns.entrySet().stream()
				.map(Map.Entry::getValue)
				.toList();
	}

	private static Set<String> normalizedSet(List<String> columns) {
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		if (columns == null) return normalized;
		for (String column : columns) {
			String key = normalizeKey(column);
			if (key != null) normalized.add(key);
		}
		return normalized;
	}

	private static ParsedDataset parseUploadedDataset(Path datasetPath, TimeBucket timeBucket) {
		List<String> lines;
		try {
			lines = Files.readAllLines(datasetPath, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Unable to read uploaded dataset file");
		}
		if (lines.size() < 2) {
			throw new UiCommandException(422, "PRECONDITION_FAILED", "Uploaded dataset is empty");
		}
		char delimiter = detectDelimiter(datasetPath, lines);
		List<String> headers = parseDelimitedLine(lines.get(0), delimiter);
		if (headers.isEmpty()) {
			throw new UiCommandException(422, "PRECONDITION_FAILED", "Uploaded dataset has no headers");
		}
		int instantIndex = resolveInstantColumnIndex(headers);
		List<DatasetRow> rows = instantIndex >= 0
				? parseRowsWithInstant(headers, instantIndex, lines, delimiter)
				: parseRowsWithSyntheticInstant(headers, lines, delimiter, timeBucket);
		if (rows.isEmpty()) {
			throw new UiCommandException(422, "PRECONDITION_FAILED", "Uploaded dataset has no usable data rows");
		}
		return new ParsedDataset(delimiter, headers, rows);
	}

	private static List<DatasetRow> parseRowsWithInstant(
			List<String> headers,
			int instantIndex,
			List<String> lines,
			char delimiter
	) {
		List<DatasetRow> rows = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line == null || line.isBlank()) continue;
			List<String> cells = parseDelimitedLine(line, delimiter);
			String rawInstant = instantIndex < cells.size() ? cells.get(instantIndex) : null;
			Instant instant = parseInstantLike(rawInstant);
			if (instant == null) continue;
			rows.add(new DatasetRow(instant, rowValues(headers, cells)));
		}
		return rows;
	}

	private static List<DatasetRow> parseRowsWithSyntheticInstant(
			List<String> headers,
			List<String> lines,
			char delimiter,
			TimeBucket timeBucket
	) {
		List<Map<String, String>> values = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line == null || line.isBlank()) continue;
			List<String> cells = parseDelimitedLine(line, delimiter);
			values.add(rowValues(headers, cells));
		}
		if (values.isEmpty()) return List.of();
		long stepSeconds = Math.max(1L, joinTolerance(timeBucket).getSeconds() / 2L);
		Instant start = Instant.now().minusSeconds(Math.max(0L, (long) (values.size() - 1) * stepSeconds));
		List<DatasetRow> rows = new ArrayList<>(values.size());
		for (int i = 0; i < values.size(); i++) {
			rows.add(new DatasetRow(start.plusSeconds((long) i * stepSeconds), values.get(i)));
		}
		return rows;
	}

	private static Map<String, String> rowValues(List<String> headers, List<String> cells) {
		Map<String, String> values = new LinkedHashMap<>();
		for (int i = 0; i < headers.size(); i++) {
			String header = trimToNull(headers.get(i));
			if (header == null) continue;
			String normalized = normalizeKey(header);
			if (normalized == null) continue;
			String value = i < cells.size() ? trimToEmpty(cells.get(i)) : "";
			values.put(normalized, value);
		}
		return values;
	}

	/**
	 * Join criteria:
	 * - Build the time axis from uploaded dataset instants + ingested metric instants.
	 * - For each instant, each required column takes the latest value <= instant
	 * if it is not older than 2 time buckets (as-of join with tolerance).
	 * - Keep only complete rows (all required columns present).
	 */
	private static List<JoinedRow> joinCompleteRows(
			NavigableSet<Instant> candidateInstants,
			Map<String, NavigableMap<Instant, String>> samplesByColumn,
			List<String> orderedColumns,
			Duration tolerance
	) {
		if (candidateInstants.isEmpty()) return List.of();
		LinkedHashMap<Instant, JoinedRow> byInstant = new LinkedHashMap<>();
		for (Instant instant : candidateInstants) {
			if (instant == null) continue;
			Map<String, String> row = new LinkedHashMap<>();
			boolean complete = true;
			for (String column : orderedColumns) {
				String normalized = normalizeKey(column);
				String value = latestValue(samplesByColumn.get(normalized), instant, tolerance);
				if (value == null) {
					complete = false;
					break;
				}
				row.put(normalized, value);
			}
			if (complete) {
				byInstant.put(instant, new JoinedRow(instant, row));
			}
		}
		return List.copyOf(byInstant.values());
	}

	private static List<JoinedRow> fallbackToUploadedRows(ParsedDataset dataset, List<String> orderedColumns) {
		List<JoinedRow> rows = new ArrayList<>();
		for (DatasetRow row : dataset.rows()) {
			if (row == null || row.instant() == null || row.values() == null) continue;
			Map<String, String> values = new LinkedHashMap<>();
			boolean complete = true;
			for (String column : orderedColumns) {
				String normalized = normalizeKey(column);
				String value = normalized == null ? null : trimToNull(row.values().get(normalized));
				if (value == null) {
					complete = false;
					break;
				}
				values.put(normalized, value);
			}
			if (complete) {
				rows.add(new JoinedRow(row.instant(), values));
			}
		}
		return List.copyOf(rows);
	}

	private void collectUploadedSamples(
			ParsedDataset dataset,
			Map<String, NavigableMap<Instant, String>> samplesByColumn,
			NavigableSet<Instant> candidateInstants
	) {
		for (DatasetRow row : dataset.rows()) {
			if (row == null || row.instant() == null || row.values() == null) continue;
			candidateInstants.add(row.instant());
			row.values().forEach((column, rawValue) -> {
				String normalized = normalizeKey(column);
				String value = trimToNull(rawValue);
				if (normalized == null || value == null || "instant".equals(normalized)) return;
				samplesByColumn.computeIfAbsent(normalized, ignored -> new TreeMap<>()).put(row.instant(), value);
			});
		}
	}

	private void collectIngestedSamples(
			String twinId,
			String twinVersion,
			DigitalSubject subject,
			Set<String> requiredColumns,
			Map<String, NavigableMap<Instant, String>> samplesByColumn,
			NavigableSet<Instant> candidateInstants
	) {
		if (subject == null || subject.variables() == null || requiredColumns.isEmpty()) return;
		for (Variable variable : subject.variables()) {
			if (variable == null || variable.variableType() != VariableType.SENSOR) continue;
			String column = preferredColumnName(variable);
			String normalizedColumn = normalizeKey(column);
			if (normalizedColumn == null || !requiredColumns.contains(normalizedColumn)) continue;

			DatasetStorage.MetricSeries series = datasetStorage.readMetricSeries(
					twinId,
					twinVersion,
					subject.id(),
					subject.name(),
					variable.id(),
					variable.name(),
					DatasetStorage.MetricKind.SENSORS,
					Integer.MAX_VALUE
			);
			List<DatasetStorage.MetricSample> history = series == null || series.history() == null ? List.of() : series.history();
			for (DatasetStorage.MetricSample sample : history) {
				if (sample == null || sample.instant() == null || sample.value() == null || !Double.isFinite(sample.value())) {
					continue;
				}
				candidateInstants.add(sample.instant());
				samplesByColumn.computeIfAbsent(normalizedColumn, ignored -> new TreeMap<>())
						.put(sample.instant(), Double.toString(sample.value()));
			}
		}
	}

	private static String latestValue(NavigableMap<Instant, String> values, Instant instant, Duration tolerance) {
		if (values == null || values.isEmpty() || instant == null) return null;
		Map.Entry<Instant, String> entry = values.floorEntry(instant);
		if (entry == null) return null;
		if (tolerance != null) {
			Duration age = Duration.between(entry.getKey(), instant);
			if (age.compareTo(tolerance) > 0) return null;
		}
		return trimToNull(entry.getValue());
	}

	private static Path writePreparedCsv(Path uploadedDatasetPath, List<JoinedRow> rows, List<String> orderedColumns) {
		Path parent = uploadedDatasetPath == null ? null : uploadedDatasetPath.getParent();
		if (parent == null) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Invalid uploaded dataset path");
		}
		Path outputDir = parent.resolve("training").normalize();
		Path outputPath = outputDir.resolve("prepared_" + UUID.randomUUID() + ".csv").normalize();
		try {
			Files.createDirectories(outputDir);
			List<String> header = new ArrayList<>();
			header.add("instant");
			for (String column : orderedColumns) {
				String clean = trimToNull(column);
				if (clean != null) header.add(clean);
			}
			StringBuilder csv = new StringBuilder();
			csv.append(toCsvLine(header)).append(System.lineSeparator());
			for (JoinedRow row : rows) {
				List<String> cells = new ArrayList<>();
				cells.add(row.instant() == null ? "" : row.instant().toString());
				for (String column : orderedColumns) {
					String normalized = normalizeKey(column);
					String value = normalized == null ? "" : trimToEmpty(row.values().get(normalized));
					cells.add(value);
				}
				csv.append(toCsvLine(cells)).append(System.lineSeparator());
			}
			Files.writeString(outputPath, csv.toString(), StandardCharsets.UTF_8);
			return outputPath.toAbsolutePath().normalize();
		} catch (IOException e) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Unable to write prepared training dataset");
		}
	}

	private static String toCsvLine(List<String> cells) {
		return cells.stream().map(TrainingDatasetPreparer::escapeCsv).reduce((a, b) -> a + "," + b).orElse("");
	}

	private static String escapeCsv(String value) {
		String safe = value == null ? "" : value;
		if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
			return "\"" + safe.replace("\"", "\"\"") + "\"";
		}
		return safe;
	}

	private static String preferredColumnName(Variable variable) {
		if (variable == null) return null;
		String byName = trimToNull(variable.name());
		if (byName != null) return byName;
		return trimToNull(variable.id());
	}

	private static Duration joinTolerance(TimeBucket bucket) {
		Duration base = switch (bucket == null ? TimeBucket.HOURS : bucket) {
			case YEARS -> Duration.ofDays(365);
			case MONTHS -> Duration.ofDays(30);
			case DAYS -> Duration.ofDays(1);
			case HOURS -> Duration.ofHours(1);
			case MINUTES -> Duration.ofMinutes(1);
			case SECONDS -> Duration.ofSeconds(1);
		};
		return base.multipliedBy(2);
	}

	private static int resolveInstantColumnIndex(List<String> headers) {
		for (int i = 0; i < headers.size(); i++) {
			String normalized = normalizeKey(headers.get(i));
			if ("instant".equals(normalized)) return i;
		}
		return -1;
	}

	private static char detectDelimiter(Path datasetPath, List<String> lines) {
		String fileName = datasetPath == null || datasetPath.getFileName() == null
				? ""
				: datasetPath.getFileName().toString().toLowerCase(Locale.ROOT);
		if (fileName.endsWith(".tsv")) return '\t';
		for (String line : lines) {
			if (line == null || line.isBlank()) continue;
			if (line.contains("\t")) return '\t';
			if (line.contains(";")) return ';';
			break;
		}
		return ',';
	}

	private static List<String> parseDelimitedLine(String line, char delimiter) {
		if (line == null) return List.of();
		List<String> values = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					current.append('"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
				continue;
			}
			if (c == delimiter && !inQuotes) {
				values.add(current.toString().trim());
				current.setLength(0);
				continue;
			}
			current.append(c);
		}
		values.add(current.toString().trim());
		return values;
	}

	private static Instant parseInstantLike(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) return null;
		String candidate = rawValue.trim();
		try {
			return Instant.parse(candidate);
		} catch (Exception ignored) {
		}
		try {
			return ZonedDateTime.parse(candidate).toInstant();
		} catch (Exception ignored) {
		}
		try {
			return LocalDateTime.parse(candidate).atZone(ZoneOffset.UTC).toInstant();
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String normalizeKey(String value) {
		String trimmed = trimToNull(value);
		return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
	}

	private static String trimToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String trimToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

	private record ParsedDataset(char delimiter, List<String> headers, List<DatasetRow> rows) {
		private ParsedDataset {
			headers = headers == null ? List.of() : List.copyOf(headers);
			rows = rows == null ? List.of() : List.copyOf(rows);
		}
	}

	private record DatasetRow(Instant instant, Map<String, String> values) {
		private DatasetRow {
			values = values == null ? Map.of() : Map.copyOf(values);
		}
	}

	private record JoinedRow(Instant instant, Map<String, String> values) {
		private JoinedRow {
			values = values == null ? Map.of() : Map.copyOf(values);
		}
	}
}
