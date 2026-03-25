package io.picota.backend.control.training;

import io.picota.backend.control.ui.schemas.DigitalSubject;
import io.picota.backend.control.ui.schemas.TimeBucket;
import io.picota.backend.control.ui.schemas.Variable;
import io.picota.backend.control.ui.schemas.VariableDataType;
import io.picota.backend.control.ui.schemas.VariableType;
import io.picota.backend.persistence.DatasetStorage;
import io.picota.backend.persistence.FilesystemDatasetStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingDatasetPreparerTest {
	private Path tempDir;

	@AfterEach
	void tearDown() throws IOException {
		if (tempDir == null || !Files.exists(tempDir)) return;
		try (var walk = Files.walk(tempDir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (RuntimeException e) {
			if (e.getCause() instanceof IOException io) throw io;
			throw e;
		}
	}

	@Test
	void shouldJoinUploadedDatasetWithIngestedMetrics() throws Exception {
		tempDir = Files.createTempDirectory("training-dataset-preparer");
		DatasetStorage storage = new FilesystemDatasetStorage(tempDir.resolve("datasets"));
		TrainingDatasetPreparer preparer = new TrainingDatasetPreparer(storage);

		String twinId = "twin_training";
		String twinVersion = "1.0.0";
		String subjectId = "CentralPark";
		String subjectName = "CentralPark";
		String uploadedCsv = """
				instant,Temperature,NoiseLevel,Occupancy
				2026-01-01T00:00:00Z,20,60,70
				2026-01-01T01:00:00Z,21,61,71
				2026-01-01T02:00:00Z,22,62,72
				""";
		storage.storeDataset(
				twinId,
				twinVersion,
				subjectId,
				subjectName,
				"dataset.csv",
				uploadedCsv.getBytes(StandardCharsets.UTF_8)
		);
		storage.appendMetric(
				twinId,
				twinVersion,
				subjectId,
				subjectName,
				"Temperature",
				"Temperature",
				DatasetStorage.MetricKind.SENSORS,
				Instant.parse("2026-01-01T03:00:00Z"),
				23.0
		);
		storage.appendMetric(
				twinId,
				twinVersion,
				subjectId,
				subjectName,
				"NoiseLevel",
				"NoiseLevel",
				DatasetStorage.MetricKind.SENSORS,
				Instant.parse("2026-01-01T03:10:00Z"),
				63.0
		);
		storage.appendMetric(
				twinId,
				twinVersion,
				subjectId,
				subjectName,
				"Occupancy",
				"Occupancy",
				DatasetStorage.MetricKind.SENSORS,
				Instant.parse("2026-01-01T03:20:00Z"),
				73.0
		);

		DigitalSubject subject = new DigitalSubject(
				subjectId,
				subjectName,
				TimeBucket.HOURS,
				List.of(
						sensor("Temperature"),
						sensor("NoiseLevel"),
						sensor("Occupancy")
				)
		);
		Path uploadedPath = storage.resolveDatasetPath(twinId, subjectId, subjectName).orElseThrow();
		Path preparedPath = preparer.prepareSubjectTrainingDataset(
				twinId,
				twinVersion,
				subject,
				"Occupancy",
				List.of("Temperature", "NoiseLevel"),
				uploadedPath,
				TimeBucket.HOURS
		);

		List<String> lines = Files.readAllLines(preparedPath, StandardCharsets.UTF_8);
		assertTrue(lines.size() >= 5, "Prepared CSV should contain joined rows");
		assertTrue(lines.get(0).equals("instant,Temperature,NoiseLevel,Occupancy"), "Prepared CSV header mismatch");
		assertTrue(
				lines.stream().anyMatch(line -> line.equals("2026-01-01T03:20:00Z,23.0,63.0,73.0")),
				"Prepared CSV should contain a complete row built from ingested metrics"
		);
	}

	private static Variable sensor(String name) {
		return new Variable(
				name,
				name,
				name,
				null,
				VariableDataType.NUMERIC,
				VariableType.SENSOR
		);
	}
}
