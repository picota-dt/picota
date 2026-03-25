package io.picota.backend.persistence;

import io.picota.backend.control.commands.UiCommandException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public class FilesystemDatasetStorage implements DatasetStorage {
	private final Path rootDir;

	public FilesystemDatasetStorage(Path rootDir) {
		if (rootDir == null) throw new IllegalArgumentException("Dataset root directory is required");
		this.rootDir = rootDir.toAbsolutePath().normalize();
		ensureDirectory(this.rootDir);
	}

	@Override
	public void storeDataset(String twinId, String twinVersion, String subjectId, String subjectName, String fileName, byte[] content) {
		String safeTwinId = safeId(twinId, "twin");
		String safeVersion = safeVersion(twinVersion);
		String safeSubjectId = safeId(subjectId, "subject");
		String safeSubjectDir = safeSubjectDirName(subjectName, safeSubjectId);
		String safeFileName = safeFileName(fileName);
		Path targetDir = rootDir.resolve(safeTwinId).resolve(safeVersion).resolve(safeSubjectDir).normalize();
		Path targetFile = targetDir.resolve(safeFileName).normalize();
		if (!targetFile.startsWith(rootDir)) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Invalid dataset storage path");
		}
		try {
			deleteSubjectInTwinRoot(safeTwinId, safeSubjectId, safeSubjectDir);
			Files.createDirectories(targetDir);
			Files.write(targetFile, content == null ? new byte[0] : content);
		} catch (IOException e) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Unable to persist dataset file");
		}
	}

	@Override
	public void deleteDataset(String twinId, String subjectId, String subjectName) {
		String safeTwinId = safeId(twinId, "twin");
		String safeSubjectId = safeId(subjectId, "subject");
		String safeSubjectDir = safeSubjectDirName(subjectName, safeSubjectId);
		try {
			deleteSubjectInTwinRoot(safeTwinId, safeSubjectId, safeSubjectDir);
		} catch (IOException e) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Unable to delete dataset file");
		}
	}

	@Override
	public void deleteTwinDatasets(String twinId) {
		String safeTwinId = safeId(twinId, "twin");
		Path twinDir = rootDir.resolve(safeTwinId).normalize();
		if (!twinDir.startsWith(rootDir)) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Invalid dataset storage path");
		}
		try {
			deleteRecursively(twinDir);
		} catch (IOException e) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Unable to delete twin datasets directory");
		}
	}

	@Override
	public Optional<Path> resolveDatasetPath(String twinId, String subjectId, String subjectName) {
		String safeTwinId = safeId(twinId, "twin");
		String safeSubjectId = safeId(subjectId, "subject");
		String safeSubjectDir = safeSubjectDirName(subjectName, safeSubjectId);
		Path twinRoot = rootDir.resolve(safeTwinId).normalize();
		if (!twinRoot.startsWith(rootDir)) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Invalid dataset storage path");
		}
		if (!Files.exists(twinRoot) || !Files.isDirectory(twinRoot)) return Optional.empty();
		try {
			Path latest = null;
			long latestModifiedAt = Long.MIN_VALUE;
			try (Stream<Path> versions = Files.list(twinRoot)) {
				for (Path versionDir : versions.filter(Files::isDirectory).toList()) {
					CandidateResult byName = latestDatasetFile(versionDir.resolve(safeSubjectDir).normalize());
					if (byName.path != null && byName.modifiedAt > latestModifiedAt) {
						latest = byName.path;
						latestModifiedAt = byName.modifiedAt;
					}
					if (!safeSubjectId.equals(safeSubjectDir)) {
						CandidateResult byLegacyId = latestDatasetFile(versionDir.resolve(safeSubjectId).normalize());
						if (byLegacyId.path != null && byLegacyId.modifiedAt > latestModifiedAt) {
							latest = byLegacyId.path;
							latestModifiedAt = byLegacyId.modifiedAt;
						}
					}
				}
			}
			return Optional.ofNullable(latest);
		} catch (IOException e) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Unable to resolve dataset path");
		}
	}

	@Override
	public synchronized void appendMetric(
			String twinId,
			String twinVersion,
			String subjectId,
			String subjectName,
			String variableId,
			String variableName,
			MetricKind kind,
			Instant instant,
			Double value
	) {
		if (kind == null) throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Metric kind is required");
		Instant safeInstant = instant == null ? Instant.now() : instant;
		double safeValue = value == null ? Double.NaN : value;
		if (!Double.isFinite(safeValue)) {
			throw new UiCommandException(422, "VALIDATION_ERROR", "Metric value must be a finite number");
		}

		Path metricsDir = subjectDir(twinId, twinVersion, subjectId, subjectName).resolve(kind.folderName()).normalize();
		Path metricFile = metricsDir.resolve(safeMetricKey(variableId, variableName) + ".csv").normalize();
		Path indexFile = metricsDir.resolve("index.csv").normalize();
		if (!metricsDir.startsWith(rootDir) || !metricFile.startsWith(rootDir) || !indexFile.startsWith(rootDir)) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Invalid metric storage path");
		}
		try {
			Files.createDirectories(metricsDir);
			ensureCsvHeader(metricFile, "instant,value");
			appendCsvLine(metricFile, safeInstant + "," + safeValue);
			Map<String, MetricSample> latestByMetric = readIndex(indexFile);
			latestByMetric.put(safeMetricKey(variableId, variableName), new MetricSample(safeInstant, safeValue));
			writeIndex(indexFile, latestByMetric);
		} catch (IOException e) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Unable to persist metric sample");
		}
	}

	@Override
	public synchronized MetricSeries readMetricSeries(
			String twinId,
			String twinVersion,
			String subjectId,
			String subjectName,
			String variableId,
			String variableName,
			MetricKind kind,
			int historyPoints
	) {
		if (kind == null) return MetricSeries.empty();
		int safeHistoryPoints = Math.max(1, historyPoints);
		Path metricsDir = subjectDir(twinId, twinVersion, subjectId, subjectName).resolve(kind.folderName()).normalize();
		if (!metricsDir.startsWith(rootDir)) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Invalid metric storage path");
		}
		String metricKey = safeMetricKey(variableId, variableName);
		Path metricFile = metricsDir.resolve(metricKey + ".csv").normalize();
		Path indexFile = metricsDir.resolve("index.csv").normalize();
		if (!metricFile.startsWith(rootDir) || !indexFile.startsWith(rootDir)) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Invalid metric storage path");
		}
		try {
			List<MetricSample> history = readMetricFile(metricFile, safeHistoryPoints);
			MetricSample latest = history.isEmpty() ? readIndex(indexFile).get(metricKey) : history.get(history.size() - 1);
			if (latest != null && history.isEmpty()) {
				history = List.of(latest);
			}
			return new MetricSeries(latest, history);
		} catch (IOException e) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Unable to read metric samples");
		}
	}

	private void deleteSubjectInTwinRoot(String safeTwinId, String safeSubjectId, String safeSubjectDir) throws IOException {
		Path twinRoot = rootDir.resolve(safeTwinId).normalize();
		if (!Files.exists(twinRoot) || !Files.isDirectory(twinRoot)) return;
		try (Stream<Path> versions = Files.list(twinRoot)) {
			for (Path versionDir : versions.filter(Files::isDirectory).toList()) {
				deleteSubjectDir(versionDir, safeSubjectDir);
				if (!safeSubjectId.equals(safeSubjectDir)) {
					// Backward compatibility with previous directory layout by subject id.
					deleteSubjectDir(versionDir, safeSubjectId);
				}
			}
		}
	}

	private void deleteSubjectDir(Path versionDir, String folderName) throws IOException {
		Path subjectDir = versionDir.resolve(folderName).normalize();
		if (!subjectDir.startsWith(rootDir)) return;
		deleteRecursively(subjectDir);
	}

	private CandidateResult latestDatasetFile(Path subjectDir) throws IOException {
		if (!subjectDir.startsWith(rootDir)) return CandidateResult.empty();
		if (!Files.exists(subjectDir) || !Files.isDirectory(subjectDir)) return CandidateResult.empty();
		Path latest = null;
		long latestModifiedAt = Long.MIN_VALUE;
		try (Stream<Path> files = Files.list(subjectDir)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				long modifiedAt = Files.getLastModifiedTime(file).toMillis();
				if (modifiedAt > latestModifiedAt) {
					latest = file.toAbsolutePath().normalize();
					latestModifiedAt = modifiedAt;
				}
			}
		}
		if (latest == null) return CandidateResult.empty();
		return new CandidateResult(latest, latestModifiedAt);
	}

	private static void deleteRecursively(Path path) throws IOException {
		if (path == null || !Files.exists(path)) return;
		try (Stream<Path> walk = Files.walk(path)) {
			walk.sorted(Comparator.reverseOrder()).forEach(current -> {
				try {
					Files.deleteIfExists(current);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (RuntimeException e) {
			if (e.getCause() instanceof IOException ioException) {
				throw ioException;
			}
			throw e;
		}
	}

	private static void ensureDirectory(Path dir) {
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to create dataset root directory: " + dir, e);
		}
	}

	private static String safeId(String raw, String fallback) {
		if (raw == null || raw.isBlank()) return fallback;
		String sanitized = raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
		return sanitized.isBlank() ? fallback : sanitized;
	}

	private static String safeVersion(String raw) {
		if (raw == null || raw.isBlank()) return "0.0.0";
		String sanitized = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		return sanitized.isBlank() ? "0.0.0" : sanitized;
	}

	private static String safeFileName(String raw) {
		if (raw == null || raw.isBlank()) return "dataset.csv";
		String onlyName = Path.of(raw).getFileName().toString();
		String sanitized = onlyName.replaceAll("[^a-zA-Z0-9._-]", "_");
		if (sanitized.isBlank()) return "dataset.csv";
		return sanitized;
	}

	private static String safeSubjectDirName(String subjectName, String safeSubjectId) {
		if (subjectName == null || subjectName.isBlank()) return safeSubjectId;
		String sanitized = subjectName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		if (sanitized.isBlank()) return safeSubjectId;
		return sanitized;
	}

	private Path subjectDir(String twinId, String twinVersion, String subjectId, String subjectName) {
		String safeTwinId = safeId(twinId, "twin");
		String safeVersion = safeVersion(twinVersion);
		String safeSubjectId = safeId(subjectId, "subject");
		String safeSubjectDir = safeSubjectDirName(subjectName, safeSubjectId);
		Path dir = rootDir.resolve(safeTwinId).resolve(safeVersion).resolve(safeSubjectDir).normalize();
		if (!dir.startsWith(rootDir)) {
			throw new UiCommandException(500, "DATASET_STORAGE_ERROR", "Invalid metric storage path");
		}
		return dir;
	}

	private static String safeMetricKey(String variableId, String variableName) {
		String preferred = variableId == null || variableId.isBlank() ? variableName : variableId;
		if (preferred == null || preferred.isBlank()) preferred = "variable";
		String sanitized = preferred.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		return sanitized.isBlank() ? "variable" : sanitized;
	}

	private static void ensureCsvHeader(Path file, String header) throws IOException {
		if (!Files.exists(file) || Files.size(file) == 0) {
			Files.writeString(file, header + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}
	}

	private static void appendCsvLine(Path file, String line) throws IOException {
		Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	private static List<MetricSample> readMetricFile(Path file, int historyPoints) throws IOException {
		if (!Files.exists(file) || !Files.isRegularFile(file)) return List.of();
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		if (lines.size() <= 1) return List.of();
		List<MetricSample> all = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			MetricSample parsed = parseMetricLine(lines.get(i));
			if (parsed != null) all.add(parsed);
		}
		if (all.isEmpty()) return List.of();
		if (all.size() <= historyPoints) return List.copyOf(all);
		return List.copyOf(all.subList(all.size() - historyPoints, all.size()));
	}

	private static Map<String, MetricSample> readIndex(Path indexFile) throws IOException {
		if (!Files.exists(indexFile) || !Files.isRegularFile(indexFile)) return new LinkedHashMap<>();
		List<String> lines = Files.readAllLines(indexFile, StandardCharsets.UTF_8);
		if (lines.size() <= 1) return new LinkedHashMap<>();
		Map<String, MetricSample> result = new LinkedHashMap<>();
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line == null || line.isBlank()) continue;
			String[] parts = line.split(",", 3);
			if (parts.length < 3) continue;
			MetricSample sample = parseMetricLine(parts[1] + "," + parts[2]);
			if (sample == null) continue;
			String metricKey = safeMetricKey(parts[0], parts[0]);
			result.put(metricKey, sample);
		}
		return result;
	}

	private static void writeIndex(Path indexFile, Map<String, MetricSample> latestByMetric) throws IOException {
		Files.createDirectories(indexFile.getParent());
		StringBuilder csv = new StringBuilder("variable,instant,value").append(System.lineSeparator());
		latestByMetric.forEach((metricKey, sample) -> {
			if (sample == null || sample.instant() == null || sample.value() == null || !Double.isFinite(sample.value()))
				return;
			csv.append(metricKey).append(",").append(sample.instant()).append(",").append(sample.value()).append(System.lineSeparator());
		});
		Files.writeString(indexFile, csv.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private static MetricSample parseMetricLine(String line) {
		if (line == null || line.isBlank()) return null;
		String[] parts = line.split(",", 2);
		if (parts.length < 2) return null;
		String rawInstant = parts[0].trim();
		String rawValue = parts[1].trim();
		if (rawInstant.isEmpty() || rawValue.isEmpty()) return null;
		try {
			Instant instant = Instant.parse(rawInstant);
			double value = Double.parseDouble(rawValue);
			if (!Double.isFinite(value)) return null;
			return new MetricSample(instant, value);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private record CandidateResult(Path path, long modifiedAt) {
		private static CandidateResult empty() {
			return new CandidateResult(null, Long.MIN_VALUE);
		}
	}
}
