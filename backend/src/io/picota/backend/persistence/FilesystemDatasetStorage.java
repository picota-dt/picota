package io.picota.backend.persistence;

import io.picota.backend.control.commands.UiCommandException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
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

	private record CandidateResult(Path path, long modifiedAt) {
		private static CandidateResult empty() {
			return new CandidateResult(null, Long.MIN_VALUE);
		}
	}
}
