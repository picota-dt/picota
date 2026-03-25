package io.picota.backend.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DatasetStorage {
	void storeDataset(String twinId, String twinVersion, String subjectId, String subjectName, String fileName, byte[] content);

	void deleteDataset(String twinId, String subjectId, String subjectName);

	void deleteTwinDatasets(String twinId);

	Optional<Path> resolveDatasetPath(String twinId, String subjectId, String subjectName);

	default void appendMetric(
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
	}

	default MetricSeries readMetricSeries(
			String twinId,
			String twinVersion,
			String subjectId,
			String subjectName,
			String variableId,
			String variableName,
			MetricKind kind,
			int historyPoints
	) {
		return MetricSeries.empty();
	}

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

	enum MetricKind {
		SENSORS("sensors"),
		INFERRED("inferred");

		private final String folderName;

		MetricKind(String folderName) {
			this.folderName = folderName;
		}

		public String folderName() {
			return folderName;
		}
	}

	record MetricSample(Instant instant, Double value) {
	}

	record MetricSeries(MetricSample latest, List<MetricSample> history) {
		public MetricSeries {
			history = history == null ? List.of() : List.copyOf(history);
		}

		public static MetricSeries empty() {
			return new MetricSeries(null, List.of());
		}
	}
}
