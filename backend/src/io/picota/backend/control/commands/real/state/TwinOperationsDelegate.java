package io.picota.backend.control.commands.real.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.picota.backend.control.commands.UiCommandException;
import io.picota.backend.control.commands.UiCommandFixtures;
import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.control.ui.schemas.requests.ApplyModelPromptRequest;
import io.picota.backend.control.ui.schemas.requests.CreateTwinRequest;
import io.picota.backend.control.ui.schemas.requests.SaveModelRequest;
import io.picota.backend.persistence.DatasetStorage;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TwinOperationsDelegate {
	private final ConcurrentMap<String, ConcurrentMap<String, DigitalTwin>> twinsByUser;
	private final ConcurrentMap<String, TrainingJob> trainingJobs;
	private final ConcurrentMap<String, String> trainingJobOwnerById;
	private final ObjectMapper mapper;
	private final Random random;
	private final ModelProjectionDelegate modelProjectionDelegate;
	private final DatasetStatisticsDelegate datasetStatisticsDelegate;
	private final DatasetStorage datasetStorage;
	private final Runnable persistAction;

	public TwinOperationsDelegate(
			ConcurrentMap<String, ConcurrentMap<String, DigitalTwin>> twinsByUser,
			ConcurrentMap<String, TrainingJob> trainingJobs,
			ConcurrentMap<String, String> trainingJobOwnerById,
			ObjectMapper mapper,
			Random random,
			ModelProjectionDelegate modelProjectionDelegate,
			DatasetStatisticsDelegate datasetStatisticsDelegate,
			DatasetStorage datasetStorage,
			Runnable persistAction
	) {
		this.twinsByUser = twinsByUser;
		this.trainingJobs = trainingJobs;
		this.trainingJobOwnerById = trainingJobOwnerById;
		this.mapper = mapper;
		this.random = random;
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
				projection.datasets()
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
				nextDatasets
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
				projection.datasets()
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
		return UiCommandFixtures.telemetryForSubject(subject, historyPoints, random);
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
				updatedDatasets
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
				updatedDatasets
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
