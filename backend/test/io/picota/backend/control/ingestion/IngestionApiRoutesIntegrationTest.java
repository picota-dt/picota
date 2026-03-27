package io.picota.backend.control.ingestion;

import io.picota.backend.control.commands.TwinModelTemplate;
import io.picota.backend.control.commands.UiCommandSet;
import io.picota.backend.control.commands.UiCommandsFactory;
import io.picota.backend.control.commands.UiCommandsMode;
import io.picota.backend.control.training.ExternalTrainingClient;
import io.picota.backend.control.ui.BackendWebServer;
import io.picota.backend.model.*;
import io.picota.backend.persistence.ModelPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionApiRoutesIntegrationTest {
	private static final String USER_ID = "usr_ingestion_test";
	private static final String AUTH_TOKEN = "tok_ingestion_test";
	private static final String TWIN_INGESTION_TOKEN = "itok_twin_d97f41e0";
	private static final String TWIN_ID = "twin_d97f41e0";
	private static final String SUBJECT_ID = "CentralPark";
	private static final String SAMPLE_INSTANT = "2035-09-25T13:30:00Z";

	private BackendWebServer server;
	private Path tempRoot;
	private int port;

	@BeforeEach
	void setUp() throws IOException {
		tempRoot = Files.createTempDirectory("picota-ingestion-api-test");
		port = findAvailablePort();
		Path datasetsRoot = tempRoot.resolve("datasets");

		ModelPersistence persistence = new InMemoryModelPersistence(seedApplication());
		UiCommandSet commands = UiCommandsFactory.create(
				UiCommandsMode.REAL,
				persistence,
				TwinModelTemplate.defaultTemplate(),
				datasetsRoot,
				ExternalTrainingClient.disabled()
		);
		server = new BackendWebServer(
				new BackendWebServer.Config("127.0.0.1", port, tempRoot.resolve("workdir"), "/v1"),
				commands
		);
		server.start();
	}

	@AfterEach
	void tearDown() throws IOException {
		if (server != null) {
			server.stop();
		}
		deleteRecursively(tempRoot);
	}

	@Test
	void shouldIngestSensorMetricsForCentralParkTwin() throws Exception {
		String payload = """
				{
				  "metrics": [
				    {"variableName":"Temperature","instant":"2035-09-25T13:30:00Z","value":21.7},
				    {"variableName":"NoiseLevel","instant":"2035-09-25T13:30:00Z","value":68.2},
				    {"variableName":"Occupancy","instant":"2035-09-25T13:30:00Z","value":85.0}
				  ]
				}
				""";
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + port + "/ingestion/v1/twins/" + TWIN_ID + "/subjects/" + SUBJECT_ID + "/metrics/sensors"))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + TWIN_INGESTION_TOKEN)
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();

		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		assertEquals(202, response.statusCode(), response.body());

		Path sensorsDir = tempRoot.resolve("datasets")
				.resolve(TWIN_ID)
				.resolve("1.0.0")
				.resolve("centralpark")
				.resolve("sensors");
		assertTrue(Files.isDirectory(sensorsDir), "Sensors directory should exist: " + sensorsDir);

		assertMetricCsv(sensorsDir.resolve("temperature.csv"), SAMPLE_INSTANT + ",21.7");
		assertMetricCsv(sensorsDir.resolve("noiselevel.csv"), SAMPLE_INSTANT + ",68.2");
		assertMetricCsv(sensorsDir.resolve("occupancy.csv"), SAMPLE_INSTANT + ",85.0");

		String indexContent = normalized(Files.readString(sensorsDir.resolve("index.csv")));
		assertTrue(indexContent.startsWith("variable,instant,value\n"), "Index must include CSV header");
		assertTrue(indexContent.contains("temperature," + SAMPLE_INSTANT + ",21.7\n"));
		assertTrue(indexContent.contains("noiselevel," + SAMPLE_INSTANT + ",68.2\n"));
		assertTrue(indexContent.contains("occupancy," + SAMPLE_INSTANT + ",85.0\n"));
	}

	private static Application seedApplication() {
		UserAccount user = new UserAccount(
				USER_ID,
				"Ingestion Tester",
				"ingestion@test.picota",
				"google-sub-ingestion-test",
				"IT",
				1000,
				Instant.parse("2035-01-01T00:00:00Z")
		);
		UserSession session = new UserSession(
				AUTH_TOKEN,
				USER_ID,
				Instant.parse("2035-01-01T00:00:00Z")
		);

		DigitalSubject centralPark = new DigitalSubject(
				SUBJECT_ID,
				SUBJECT_ID,
				TimeBucket.HOURS,
				List.of(
						sensorVariable("Temperature"),
						sensorVariable("NoiseLevel"),
						sensorVariable("Occupancy")
				)
		);
		DigitalTwin twin = new DigitalTwin(
				TWIN_ID,
				"Central Park Twin",
				"",
				"1.0.0",
				null,
				null,
				null,
				"",
				0,
				"",
				List.of(centralPark),
				null,
				List.of(),
				TWIN_INGESTION_TOKEN
		);
		return new Application(
				List.of(user),
				List.of(session),
				List.of(new TwinAggregate(USER_ID, twin)),
				List.of()
		);
	}

	private static Variable sensorVariable(String name) {
		return new Variable(
				name,
				name,
				name + " sensor",
				null,
				VariableDataType.NUMERIC,
				VariableType.SENSOR,
				null,
				null
		);
	}

	private static int findAvailablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static void assertMetricCsv(Path file, String expectedDataLine) throws IOException {
		assertTrue(Files.isRegularFile(file), "Metric CSV must exist: " + file);
		String content = normalized(Files.readString(file));
		assertTrue(content.startsWith("instant,value\n"), "Metric CSV must include header");
		assertTrue(content.contains(expectedDataLine + "\n"), "Metric CSV must include sample line");
	}

	private static String normalized(String value) {
		return value.replace("\r\n", "\n");
	}

	private static void deleteRecursively(Path path) throws IOException {
		if (path == null || !Files.exists(path)) return;
		try (var walk = Files.walk(path)) {
			walk.sorted(Comparator.reverseOrder()).forEach(current -> {
				try {
					Files.deleteIfExists(current);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (RuntimeException e) {
			if (e.getCause() instanceof IOException io) throw io;
			throw e;
		}
	}

	private static final class InMemoryModelPersistence implements ModelPersistence {
		private Application model;

		private InMemoryModelPersistence(Application seed) {
			this.model = seed;
		}

		@Override
		public Optional<Application> loadModel() {
			return Optional.ofNullable(model);
		}

		@Override
		public void saveModel(Application model) {
			this.model = model;
		}

		@Override
		public void close() {
		}
	}
}
