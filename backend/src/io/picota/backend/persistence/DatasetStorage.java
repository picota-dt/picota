package io.picota.backend.persistence;

import java.nio.file.Path;
import java.util.Optional;

public interface DatasetStorage {
	void storeDataset(String twinId, String twinVersion, String subjectId, String subjectName, String fileName, byte[] content);

	void deleteDataset(String twinId, String subjectId, String subjectName);

	void deleteTwinDatasets(String twinId);

	Optional<Path> resolveDatasetPath(String twinId, String subjectId, String subjectName);

	static DatasetStorage noOp() {
		return new DatasetStorage() {
			@Override
			public void storeDataset(String twinId, String twinVersion, String subjectId, String subjectName, String fileName, byte[] content) {
			}

			@Override
			public void deleteDataset(String twinId, String subjectId, String subjectName) {
			}

			@Override
			public void deleteTwinDatasets(String twinId) {
			}

			@Override
			public Optional<Path> resolveDatasetPath(String twinId, String subjectId, String subjectName) {
				return Optional.empty();
			}
		};
	}
}
