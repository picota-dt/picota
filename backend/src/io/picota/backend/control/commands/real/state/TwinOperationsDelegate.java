package io.picota.backend.control.commands.real.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.picota.backend.control.commands.UiCommandException;
import io.picota.backend.control.commands.UiCommandFixtures;
import io.picota.backend.control.ingestion.IngestMetricRequest;
import io.picota.backend.control.ingestion.IngestMetricsRequest;
import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.control.ui.schemas.requests.ApplyModelPromptRequest;
import io.picota.backend.control.ui.schemas.requests.CreateTwinRequest;
import io.picota.backend.control.ui.schemas.requests.SaveModelRequest;
import io.picota.backend.persistence.DatasetStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TwinOperationsDelegate {
	private final ConcurrentMap<String, ConcurrentMap<String, DigitalTwin>> twinsByUser;
	private final ConcurrentMap<String, TrainingJob> trainingJobs;
	private final ConcurrentMap<String, String> trainingJobOwnerById;
	private final ObjectMapper mapper;
	private final ModelProjectionDelegate modelProjectionDelegate;
	private final DatasetStatisticsDelegate datasetStatisticsDelegate;
	private final DatasetStorage datasetStorage;
	private final Runnable persistAction;

	public TwinOperationsDelegate(
			ConcurrentMap<String, ConcurrentMap<String, DigitalTwin>> twinsByUser,
			ConcurrentMap<String, TrainingJob> trainingJobs,
			ConcurrentMap<String, String> trainingJobOwnerById,
			ObjectMapper mapper,
			ModelProjectionDelegate modelProjectionDelegate,
			DatasetStatisticsDelegate datasetStatisticsDelegate,
			DatasetStorage datasetStorage,
			Runnable persistAction
	) {
		this.twinsByUser = twinsByUser;
		this.trainingJobs = trainingJobs;
		this.trainingJobOwnerById = trainingJobOwnerById;
		this.mapper = mapper;
		this.modelProjectionDelegate = modelProjectionDelegate;
		this.datasetStatisticsDelegate = datasetStatisticsDelegate;
		this.datasetStorage = datasetStorage == null ? DatasetStorage.noOp() : datasetStorage;
		this.persistAction = persistAction;
	}

	public List<DigitalTwin> listTwins(String userId, String status, String type, String q, String sort, String order) {
		List<DigitalTwin> twins = new ArrayList<>(twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).values());

		if (status != null && !status.isBlank()) {
			TwinStatus filterStatus = TwinStatus.fromWireValue(status);
			twins = twins.stream().filter(t -> t.status() == filterStatus).toList();
		}
		if (type != null && !type.isBlank()) {
			TwinType filterType = TwinType.fromWireValue(type);
			twins = twins.stream().filter(t -> t.type() == filterType).toList();
		}
		if (q != null && !q.isBlank()) {
			String query = q.toLowerCase(Locale.ROOT);
			twins = twins.stream().filter(t ->
					t.name().toLowerCase(Locale.ROOT).contains(query) ||
							t.description().toLowerCase(Locale.ROOT).contains(query)
			).toList();
		}

		Comparator<DigitalTwin> comparator = switch (sort == null ? "updatedAt" : sort) {
			case "name" -> Comparator.comparing(DigitalTwin::name, String.CASE_INSENSITIVE_ORDER);
			case "creditsUsed" -> Comparator.comparingInt(DigitalTwin::creditsUsed);
			default -> Comparator.comparing(DigitalTwin::updatedAt, String.CASE_INSENSITIVE_ORDER);
		};
		boolean desc = !"asc".equalsIgnoreCase(order);
		if (desc) comparator = comparator.reversed();
		return twins.stream().sorted(comparator).map(UiCommandFixtures::copyTwin).toList();
	}

	public DigitalTwin createTwin(String userId, CreateTwinRequest request) {
		validate(request != null && request.name() != null && !request.name().isBlank(), 422, "VALIDATION_ERROR", "Twin name is required");
		validate(request.type() != null, 422, "VALIDATION_ERROR", "Twin type is required");
		String twinName = request.name().trim();
		String initialModel = modelProjectionDelegate.initialTwinModel(twinName);
		ModelProjectionDelegate.ModelProjection projection =
				modelProjectionDelegate.projectTwinStateFromCommittedModel(initialModel, List.of(), List.of());

		DigitalTwin twin = new DigitalTwin(
				"twin_" + shortId(),
				twinName,
				request.description() == null || request.description().isBlank() ? "No description provided." : request.description().trim(),
				"0.1.0",
				"https://images.unsplash.com/photo-1647427060118-4911c9821b82?auto=format&fit=crop&w=1080&q=80",
				request.type(),
				TwinStatus.DRAFT,
				"Just now",
				0,
				initialModel,
				projection.subjects(),
				null,
				projection.datasets(),
				newTwinIngestionToken()
		);
		twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(twin.id(), twin);
		persistAction.run();
		return UiCommandFixtures.copyTwin(twin);
	}

	public DigitalTwin updateTwin(String userId, DigitalTwin current, Map<String, Object> updates) {
		Map<String, Object> safeUpdates = updates == null ? Map.of() : updates;
		String nextName = stringOrDefault(safeUpdates.get("name"), current.name());
		String nextDescription = stringOrDefault(safeUpdates.get("description"), current.description());
		String nextVersion = stringOrDefault(safeUpdates.get("version"), current.version());
		String nextImage = stringOrDefault(safeUpdates.get("image"), current.image());
		TwinType nextType = enumOrDefault(safeUpdates.get("type"), TwinType.class, current.type());
		TwinStatus nextStatus = enumOrDefault(safeUpdates.get("status"), TwinStatus.class, current.status());
		String nextModel = stringOrDefault(safeUpdates.get("model"), current.model());
		List<DigitalSubject> nextSubjects = listOrDefault(safeUpdates.get("subjects"), new TypeReference<List<DigitalSubject>>() {
		}, current.subjects());
		InferenceEngine nextEngine = objectOrDefault(safeUpdates.get("inferenceEngine"), InferenceEngine.class, current.inferenceEngine());
		List<SubjectDataset> nextDatasets = listOrDefault(safeUpdates.get("datasets"), new TypeReference<List<SubjectDataset>>() {
		}, current.datasets());

		if (current.status() != TwinStatus.ACTIVE && nextStatus == TwinStatus.ACTIVE) {
			validate(
					modelProjectionDelegate.hasDefinedModel(nextModel, nextName, current.name()),
					422,
					"PRECONDITION_FAILED",
					"Twin model must be defined before activation"
			);
		}
		if (safeUpdates.containsKey("model")) {
			ModelProjectionDelegate.ModelProjection projection =
					modelProjectionDelegate.projectTwinStateFromCommittedModel(nextModel, nextSubjects, nextDatasets);
			nextSubjects = projection.subjects();
			nextDatasets = projection.datasets();
		}

		DigitalTwin updated = new DigitalTwin(
				current.id(),
				nextName,
				nextDescription,
				nextVersion,
				nextImage,
				nextType,
				nextStatus,
				stringOrDefault(safeUpdates.get("updatedAt"), "Just now"),
				intOrDefault(safeUpdates.get("creditsUsed"), current.creditsUsed()),
				nextModel,
				nextSubjects,
				nextEngine,
				nextDatasets,
				current.ingestionToken()
		);
		removeOrphanDatasetFiles(current, updated);
		twinsByUser.get(userId).put(updated.id(), updated);
		persistAction.run();
		return UiCommandFixtures.copyTwin(updated);
	}

	public void deleteTwin(String userId, String twinId) {
		ConcurrentMap<String, DigitalTwin> twins = twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
		DigitalTwin removed = twins.remove(twinId);
		if (removed == null) throw new UiCommandException(404, "TWIN_NOT_FOUND", "No twin found with id " + twinId);
		datasetStorage.deleteTwinDatasets(twinId);
		trainingJobs.entrySet().removeIf(e -> twinId.equals(e.getValue().twinId()));
		trainingJobOwnerById.entrySet().removeIf(e -> !trainingJobs.containsKey(e.getKey()));
		persistAction.run();
	}

	public ModelContent getModel(DigitalTwin twin) {
		return new ModelContent(twin.model(), twin.version());
	}

	public SaveModelResponse saveModel(String userId, String twinId, DigitalTwin twin, SaveModelRequest request) {
		validate(request != null && request.content() != null, 422, "VALIDATION_ERROR", "Model content is required");
		String newVersion = bumpVersion(twin.version(), request.versionBump() == null ? VersionBump.PATCH : request.versionBump());
		ModelProjectionDelegate.ModelProjection projection =
				modelProjectionDelegate.projectTwinStateFromCommittedModel(request.content(), twin.subjects(), twin.datasets());
		DigitalTwin updated = new DigitalTwin(
				twin.id(),
				twin.name(),
				twin.description(),
				newVersion,
				twin.image(),
				twin.type(),
				twin.status(),
				"Just now",
				twin.creditsUsed(),
				request.content(),
				projection.subjects(),
				twin.inferenceEngine(),
				projection.datasets(),
				twin.ingestionToken()
		);
		removeOrphanDatasetFiles(twin, updated);
		twinsByUser.get(userId).put(twinId, updated);
		persistAction.run();
		return new SaveModelResponse(newVersion, Instant.now());
	}

	public ApplyModelPromptResponse applyModelPrompt(ApplyModelPromptRequest request) {
		String prompt = request == null || request.prompt() == null ? "" : request.prompt().trim();
		String base = request == null || request.currentContent() == null ? "" : request.currentContent();
		String updated = base + "\n# AI suggestion: " + (prompt.isBlank() ? "No prompt provided" : prompt);
		return new ApplyModelPromptResponse(updated);
	}

	public List<DigitalSubject> listSubjects(DigitalTwin twin) {
		return twin.subjects().stream().map(UiCommandFixtures::copySubject).toList();
	}

	public DigitalSubject getSubject(DigitalTwin twin, String subjectId) {
		return twin.subjects().stream()
				.filter(s -> s.id().equals(subjectId))
				.findFirst()
				.map(UiCommandFixtures::copySubject)
				.orElseThrow(() -> new UiCommandException(404, "SUBJECT_NOT_FOUND", "No subject found with id " + subjectId));
	}

	public List<VariableTelemetry> getSubjectTelemetry(DigitalTwin twin, String subjectId, int historyPoints) {
		DigitalSubject subject = getSubject(twin, subjectId);
		int safeHistoryPoints = Math.max(1, Math.min(historyPoints, 5_000));
		List<VariableTelemetry> telemetry = new ArrayList<>();
		for (Variable variable : subject.variables()) {
			if (variable == null) continue;
			boolean inferredVariable = variable.variableType() == VariableType.INFERRED;
			DatasetStorage.MetricKind primaryKind = variable.variableType() == VariableType.INFERRED
					? DatasetStorage.MetricKind.INFERRED
					: DatasetStorage.MetricKind.SENSORS;
			telemetry.add(toTelemetry(
					twin,
					subject,
					variable,
					safeHistoryPoints,
					primaryKind,
					inferredVariable ? inferredTelemetryId(variable) : variable.id(),
					variable.name()
			));
			if (inferredVariable && !hasSensorCounterpart(subject, variable)) {
				telemetry.add(toTelemetry(
						twin,
						subject,
						variable,
						safeHistoryPoints,
						DatasetStorage.MetricKind.SENSORS,
						sensorActualTelemetryId(variable),
						sensorActualTelemetryName(variable)
				));
			}
		}
		return List.copyOf(telemetry);
	}

	private static boolean hasSensorCounterpart(DigitalSubject subject, Variable inferredVariable) {
		if (subject == null || inferredVariable == null || subject.variables() == null) return false;
		String inferredId = trimToNull(inferredVariable.id());
		String inferredName = trimToNull(inferredVariable.name());
		return subject.variables().stream()
				.filter(variable -> variable != null && variable.variableType() == VariableType.SENSOR)
				.anyMatch(variable ->
						(inferredId != null && equalsIgnoreCase(variable.id(), inferredId)) ||
								(inferredName != null && equalsIgnoreCase(variable.name(), inferredName))
				);
	}

	public void ingestSubjectSensorMetrics(DigitalTwin twin, String subjectId, IngestMetricsRequest request) {
		ingestSubjectMetrics(twin, subjectId, request, VariableType.SENSOR, DatasetStorage.MetricKind.SENSORS);
	}

	public List<SubjectDataset> listDatasets(DigitalTwin twin) {
		return twin.datasets().stream().map(UiCommandFixtures::copyDataset).toList();
	}

	public SubjectDataset getDataset(DigitalTwin twin, String subjectId) {
		return twin.datasets().stream()
				.filter(d -> d.subjectId().equals(subjectId))
				.findFirst()
				.map(UiCommandFixtures::copyDataset)
				.orElseThrow(() -> new UiCommandException(404, "DATASET_NOT_FOUND", "No dataset found for subject " + subjectId));
	}

	public SubjectDataset uploadDataset(String userId, DigitalTwin twin, String subjectId, String fileName, byte[] content) {
		DigitalSubject subject = twin.subjects().stream()
				.filter(s -> s.id().equals(subjectId))
				.findFirst()
				.orElseThrow(() -> new UiCommandException(404, "SUBJECT_NOT_FOUND", "No subject found with id " + subjectId));

		DatasetStatisticsDelegate.CsvStats stats = datasetStatisticsDelegate.computeCsvStats(subject, content);
		datasetStorage.storeDataset(twin.id(), twin.version(), subjectId, subject.name(), fileName, content);
		SubjectDataset dataset = new SubjectDataset(
				subjectId,
				fileName,
				stats.rowCount(),
				0,
				Instant.now(),
				stats.variableStats()
		);
		List<SubjectDataset> updatedDatasets = new ArrayList<>(twin.datasets());
		updatedDatasets.removeIf(d -> d.subjectId().equals(subjectId));
		updatedDatasets.add(dataset);
		DigitalTwin updatedTwin = new DigitalTwin(
				twin.id(),
				twin.name(),
				twin.description(),
				twin.version(),
				twin.image(),
				twin.type(),
				twin.status(),
				"Just now",
				twin.creditsUsed(),
				twin.model(),
				twin.subjects(),
				twin.inferenceEngine(),
				updatedDatasets,
				twin.ingestionToken()
		);
		twinsByUser.get(userId).put(twin.id(), updatedTwin);
		persistAction.run();
		return UiCommandFixtures.copyDataset(dataset);
	}

	public void deleteDataset(String userId, DigitalTwin twin, String subjectId) {
		List<SubjectDataset> updatedDatasets = new ArrayList<>(twin.datasets());
		boolean removed = updatedDatasets.removeIf(d -> d.subjectId().equals(subjectId));
		if (!removed)
			throw new UiCommandException(404, "DATASET_NOT_FOUND", "No dataset found for subject " + subjectId);
		String subjectName = subjectNameById(twin.subjects(), subjectId);
		datasetStorage.deleteDataset(twin.id(), subjectId, subjectName);
		DigitalTwin updatedTwin = new DigitalTwin(
				twin.id(),
				twin.name(),
				twin.description(),
				twin.version(),
				twin.image(),
				twin.type(),
				twin.status(),
				"Just now",
				twin.creditsUsed(),
				twin.model(),
				twin.subjects(),
				twin.inferenceEngine(),
				updatedDatasets,
				twin.ingestionToken()
		);
		twinsByUser.get(userId).put(twin.id(), updatedTwin);
		persistAction.run();
	}

	public void deleteTwinDatasets(String twinId) {
		datasetStorage.deleteTwinDatasets(twinId);
	}

	private void removeOrphanDatasetFiles(DigitalTwin before, DigitalTwin after) {
		List<String> beforeSubjects = before.datasets().stream()
				.map(SubjectDataset::subjectId)
				.filter(id -> id != null && !id.isBlank())
				.toList();
		List<String> afterSubjects = after.datasets().stream()
				.map(SubjectDataset::subjectId)
				.filter(id -> id != null && !id.isBlank())
				.toList();
		for (String subjectId : beforeSubjects) {
			if (!afterSubjects.contains(subjectId)) {
				datasetStorage.deleteDataset(before.id(), subjectId, subjectNameById(before.subjects(), subjectId));
			}
		}
	}

	private String subjectNameById(List<DigitalSubject> subjects, String subjectId) {
		if (subjects == null || subjectId == null || subjectId.isBlank()) return null;
		return subjects.stream()
				.filter(subject -> subject != null && subjectId.equals(subject.id()))
				.map(DigitalSubject::name)
				.filter(name -> name != null && !name.isBlank())
				.findFirst()
				.orElse(null);
	}

	private VariableTelemetry toTelemetry(
			DigitalTwin twin,
			DigitalSubject subject,
			Variable variable,
			int historyPoints,
			DatasetStorage.MetricKind metricKind,
			String telemetryVariableId,
			String telemetryVariableName
	) {
		DatasetStorage.MetricSeries series = datasetStorage.readMetricSeries(
				twin.id(),
				twin.version(),
				subject.id(),
				subject.name(),
				variable.id(),
				variable.name(),
				metricKind,
				historyPoints
		);
		List<TelemetryPoint> history = series.history().stream()
				.filter(sample -> sample != null && sample.instant() != null && sample.value() != null && Double.isFinite(sample.value()))
				.map(sample -> new TelemetryPoint(sample.instant(), sample.value()))
				.toList();
		if (metricKind == DatasetStorage.MetricKind.SENSORS) {
			history = mergeSensorHistory(
					fallbackHistoryFromDataset(twin, subject, variable, historyPoints),
					history,
					historyPoints
			);
		}
		Double current = series.latest() != null && series.latest().value() != null && Double.isFinite(series.latest().value())
				? series.latest().value()
				: history.isEmpty() ? null : history.get(history.size() - 1).value();
		return new VariableTelemetry(telemetryVariableId, telemetryVariableName, variable.unit(), current, history);
	}

	private static List<TelemetryPoint> mergeSensorHistory(
			List<TelemetryPoint> datasetHistory,
			List<TelemetryPoint> ingestedHistory,
			int historyPoints
	) {
		int safeHistoryPoints = Math.max(1, historyPoints);
		NavigableMap<Instant, Double> mergedByInstant = new TreeMap<>();
		if (datasetHistory != null) {
			for (TelemetryPoint point : datasetHistory) {
				if (point == null || point.time() == null || point.value() == null || !Double.isFinite(point.value()))
					continue;
				mergedByInstant.put(point.time(), point.value());
			}
		}
		if (ingestedHistory != null) {
			for (TelemetryPoint point : ingestedHistory) {
				if (point == null || point.time() == null || point.value() == null || !Double.isFinite(point.value()))
					continue;
				mergedByInstant.put(point.time(), point.value());
			}
		}
		if (mergedByInstant.isEmpty()) return List.of();
		List<TelemetryPoint> merged = mergedByInstant.entrySet().stream()
				.map(entry -> new TelemetryPoint(entry.getKey(), entry.getValue()))
				.toList();
		if (merged.size() <= safeHistoryPoints) return merged;
		return List.copyOf(merged.subList(merged.size() - safeHistoryPoints, merged.size()));
	}

	private static String sensorActualTelemetryId(Variable variable) {
		String base = trimToNull(variable == null ? null : variable.id());
		if (base == null) base = trimToNull(variable == null ? null : variable.name());
		if (base == null) base = "variable";
		return base + "__sensor_actual";
	}

	private static String sensorActualTelemetryName(Variable variable) {
		String base = trimToNull(variable == null ? null : variable.name());
		if (base == null) base = trimToNull(variable == null ? null : variable.id());
		if (base == null) base = "variable";
		return base + " (actual)";
	}

	private static String inferredTelemetryId(Variable variable) {
		String base = trimToNull(variable == null ? null : variable.id());
		if (base == null) base = trimToNull(variable == null ? null : variable.name());
		if (base == null) base = "variable";
		return base + "__inferred";
	}

	private List<TelemetryPoint> fallbackHistoryFromDataset(DigitalTwin twin, DigitalSubject subject, Variable variable, int historyPoints) {
		Optional<Path> datasetPath = datasetStorage.resolveDatasetPath(twin.id(), subject.id(), subject.name());
		if (datasetPath.isEmpty()) return List.of();
		try {
			List<String> lines = Files.readAllLines(datasetPath.get(), StandardCharsets.UTF_8);
			if (lines.size() < 2) return List.of();
			String[] headers = splitCsvLine(lines.get(0));
			int valueColumn = resolveValueColumnIndex(headers, variable);
			if (valueColumn < 0) return List.of();
			int instantColumn = resolveInstantColumnIndex(headers);
			Map<String, Double> categoricalValues = new LinkedHashMap<>();
			List<TelemetryPoint> history = new ArrayList<>();
			int totalDataRows = lines.size() - 1;
			for (int i = 1; i < lines.size(); i++) {
				String[] cells = splitCsvLine(lines.get(i));
				if (valueColumn >= cells.length) continue;
				Double value = parseTelemetryValue(variable, cells[valueColumn], categoricalValues);
				if (value == null || !Double.isFinite(value)) continue;
				Instant instant = parseInstantOrDefault(
						instantColumn >= 0 && instantColumn < cells.length ? cleanCsvCell(cells[instantColumn]) : null,
						Instant.now().minusSeconds((long) (totalDataRows - i) * 3L)
				);
				history.add(new TelemetryPoint(instant, value));
			}
			if (history.isEmpty()) return List.of();
			int safeHistoryPoints = Math.max(1, historyPoints);
			if (history.size() <= safeHistoryPoints) return List.copyOf(history);
			return List.copyOf(history.subList(history.size() - safeHistoryPoints, history.size()));
		} catch (IOException ignored) {
			return List.of();
		}
	}

	private static int resolveValueColumnIndex(String[] headers, Variable variable) {
		if (headers == null || headers.length == 0 || variable == null) return -1;
		String byName = trimToNull(variable.name());
		String byId = trimToNull(variable.id());
		for (int i = 0; i < headers.length; i++) {
			String header = trimToNull(cleanCsvCell(headers[i]));
			if (header == null) continue;
			if (byName != null && header.equalsIgnoreCase(byName)) return i;
			if (byId != null && header.equalsIgnoreCase(byId)) return i;
		}
		return -1;
	}

	private static int resolveInstantColumnIndex(String[] headers) {
		if (headers == null || headers.length == 0) return -1;
		for (int i = 0; i < headers.length; i++) {
			String header = trimToNull(cleanCsvCell(headers[i]));
			if (header != null && "instant".equalsIgnoreCase(header)) return i;
		}
		return -1;
	}

	private static Double parseTelemetryValue(Variable variable, String rawCell, Map<String, Double> categoricalValues) {
		String cleaned = cleanCsvCell(rawCell);
		if (cleaned.isEmpty()) return null;
		try {
			double numeric = Double.parseDouble(cleaned);
			return Double.isFinite(numeric) ? numeric : null;
		} catch (NumberFormatException ignored) {
		}
		if (variable != null && variable.dataType() == VariableDataType.CATEGORICAL) {
			return categoricalValues.computeIfAbsent(cleaned, key -> (double) categoricalValues.size());
		}
		return null;
	}

	private static Instant parseInstantOrDefault(String rawInstant, Instant fallback) {
		String cleaned = trimToNull(rawInstant);
		if (cleaned == null) return fallback;
		try {
			return Instant.parse(cleaned);
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static String[] splitCsvLine(String line) {
		if (line == null) return new String[0];
		return line.split(",", -1);
	}

	private static String cleanCsvCell(String value) {
		if (value == null) return "";
		String trimmed = value.trim();
		if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
			trimmed = trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
		}
		return trimmed;
	}

	private void ingestSubjectMetrics(
			DigitalTwin twin,
			String subjectId,
			IngestMetricsRequest request,
			VariableType expectedType,
			DatasetStorage.MetricKind metricKind
	) {
		DigitalSubject subject = getSubject(twin, subjectId);
		validate(request != null && request.metrics() != null && !request.metrics().isEmpty(),
				422,
				"VALIDATION_ERROR",
				"At least one metric sample is required");
		for (IngestMetricRequest metric : request.metrics()) {
			validate(metric != null, 422, "VALIDATION_ERROR", "Metric sample payload cannot be null");
			Variable variable = resolveMetricVariable(subject, metric, expectedType);
			validate(metric.value() != null && Double.isFinite(metric.value()),
					422,
					"VALIDATION_ERROR",
					"Metric value must be a finite number");
			Instant instant = metric.instant() == null ? Instant.now() : metric.instant();
			datasetStorage.appendMetric(
					twin.id(),
					twin.version(),
					subject.id(),
					subject.name(),
					variable.id(),
					variable.name(),
					metricKind,
					instant,
					metric.value()
			);
		}
	}

	private Variable resolveMetricVariable(DigitalSubject subject, IngestMetricRequest metric, VariableType expectedType) {
		String requestedId = trimToNull(metric.variableId());
		String requestedName = trimToNull(metric.variableName());
		validate(requestedId != null || requestedName != null,
				422,
				"VALIDATION_ERROR",
				"Each metric sample must include variableId or variableName");
		return subject.variables().stream()
				.filter(variable -> variable != null && variable.variableType() == expectedType)
				.filter(variable -> matchesVariable(variable, requestedId, requestedName))
				.findFirst()
				.orElseThrow(() -> new UiCommandException(
						404,
						"VARIABLE_NOT_FOUND",
						"Variable not found in subject " + subject.id() + " for type " + expectedType.toWireValue()
				));
	}

	private static boolean matchesVariable(Variable variable, String requestedId, String requestedName) {
		boolean idMatch = requestedId != null && equalsIgnoreCase(variable.id(), requestedId);
		boolean nameMatch = requestedName != null && equalsIgnoreCase(variable.name(), requestedName);
		return idMatch || nameMatch;
	}

	private static boolean equalsIgnoreCase(String left, String right) {
		return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
	}

	private static String trimToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String bumpVersion(String version, VersionBump bump) {
		String[] parts = (version == null ? "0.1.0" : version).split("\\.");
		int major = parts.length > 0 ? parseInt(parts[0], 0) : 0;
		int minor = parts.length > 1 ? parseInt(parts[1], 1) : 1;
		int patch = parts.length > 2 ? parseInt(parts[2], 0) : 0;
		return switch (bump) {
			case MAJOR -> (major + 1) + ".0.0";
			case MINOR -> major + "." + (minor + 1) + ".0";
			case PATCH -> major + "." + minor + "." + (patch + 1);
		};
	}

	private static String shortId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	private static String newTwinIngestionToken() {
		return "itok_" + UUID.randomUUID().toString().replace("-", "");
	}

	private static void validate(boolean condition, int status, String code, String message) {
		if (!condition) throw new UiCommandException(status, code, message);
	}

	private String stringOrDefault(Object raw, String fallback) {
		if (raw == null) return fallback;
		String value = String.valueOf(raw);
		return value.isBlank() ? fallback : value;
	}

	private int intOrDefault(Object raw, int fallback) {
		if (raw == null) return fallback;
		if (raw instanceof Number number) return number.intValue();
		try {
			return Integer.parseInt(String.valueOf(raw));
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private <T> T enumOrDefault(Object raw, Class<T> type, T fallback) {
		if (raw == null) return fallback;
		return mapper.convertValue(raw, type);
	}

	private <T> T objectOrDefault(Object raw, Class<T> type, T fallback) {
		if (raw == null) return fallback;
		return mapper.convertValue(raw, type);
	}

	private <T> List<T> listOrDefault(Object raw, TypeReference<List<T>> typeRef, List<T> fallback) {
		if (raw == null) return fallback;
		List<T> converted = mapper.convertValue(raw, typeRef);
		return converted == null ? fallback : converted;
	}

	private static int parseInt(String value, int fallback) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
