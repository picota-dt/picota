package io.picota.backend.control.commands.real.state;

import io.intino.magritte.builder.StashBuilder;
import io.intino.magritte.framework.stores.ResourcesStore;
import io.intino.magritte.io.model.Stash;
import io.picota.backend.control.commands.TwinModelTemplate;
import io.picota.backend.control.ui.schemas.*;
import io.quassar.monentia.picota.PicotaGraph;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ModelProjectionDelegate {
	private final TwinModelTemplate twinModelTemplate;

	public ModelProjectionDelegate(TwinModelTemplate twinModelTemplate) {
		this.twinModelTemplate = twinModelTemplate == null ? TwinModelTemplate.defaultTemplate() : twinModelTemplate;
	}

	public String initialTwinModel(String twinName) {
		return twinModelTemplate.render(twinName);
	}

	public boolean hasDefinedModel(String model, String nextTwinName, String currentTwinName) {
		if (model == null || model.isBlank()) return false;
		if (twinModelTemplate.matchesRendered(model, nextTwinName)) return false;
		return currentTwinName == null
				|| currentTwinName.equals(nextTwinName)
				|| !twinModelTemplate.matchesRendered(model, currentTwinName);
	}

	public ModelProjection projectTwinStateFromCommittedModel(
			String modelText,
			List<DigitalSubject> currentSubjects,
			List<SubjectDataset> currentDatasets
	) {
		List<DigitalSubject> parsedSubjects = parseSubjectsFromCommittedModel(modelText, currentSubjects);
		List<SubjectDataset> projectedDatasets = retainDatasetsForSubjects(currentDatasets, parsedSubjects);
		return new ModelProjection(parsedSubjects, projectedDatasets);
	}

	private List<DigitalSubject> parseSubjectsFromCommittedModel(String modelText, List<DigitalSubject> fallbackSubjects) {
		List<DigitalSubject> safeFallback = fallbackSubjects == null ? List.of() : List.copyOf(fallbackSubjects);
		if (modelText == null || modelText.isBlank()) return safeFallback;
		try {
			Path modelFile = Files.createTempFile("_model", ".tara");
			Files.writeString(modelFile, modelText);
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			Stash[] stashes = (new StashBuilder(List.of(modelFile.toFile()), "io.quassar.monentia:picota:1.2.3", "parser", new PrintStream(stream))).build();
			PicotaGraph graph = PicotaGraph.load(new ResourcesStore(), stashes);
			List<io.quassar.monentia.picota.Variable> globalVariables =
					graph.reality() == null || graph.reality().variableList() == null
							? List.of()
							: graph.reality().variableList();
			List<DigitalSubject> parsedSubjects = new ArrayList<>();
			for (io.quassar.monentia.picota.DigitalTwin.DigitalSubject parsedSubject : graph.digitalTwin().digitalSubjectList()) {
				if (parsedSubject == null || parsedSubject.subject() == null) continue;
				String subjectId = resolveSubjectName(parsedSubject);
				if (subjectId == null || subjectId.isBlank()) continue;
				TimeBucket timeBucket = resolveTimeBucket(parsedSubject);
				Map<String, List<InferenceModelSettings>> inferredByVariableName = inferenceSettingsByVariableName(parsedSubject);
				List<Variable> variables = mergeVariables(globalVariables, parsedSubject.subject().variableList(), inferredByVariableName);
				parsedSubjects.add(new DigitalSubject(subjectId, subjectId, timeBucket, variables));
			}
			if (parsedSubjects.isEmpty()) return safeFallback;
			return List.copyOf(parsedSubjects);
		} catch (IOException | RuntimeException ex) {
			return safeFallback;
		}
	}

	private Map<String, List<InferenceModelSettings>> inferenceSettingsByVariableName(
			io.quassar.monentia.picota.DigitalTwin.DigitalSubject parsedSubject
	) {
		if (parsedSubject.inferenceModelList() == null || parsedSubject.inferenceModelList().isEmpty()) return Map.of();
		Map<String, List<InferenceModelSettings>> settings = new LinkedHashMap<>();
		for (io.quassar.monentia.picota.DigitalTwin.DigitalSubject.InferenceModel inferenceModel : parsedSubject.inferenceModelList()) {
			if (inferenceModel == null || inferenceModel.variable() == null) continue;
			String variableName = blankToNull(inferenceModel.variable().name$());
			if (variableName == null) continue;
			Integer timeHorizon = inferenceModel.timeHorizon() <= 0 ? null : inferenceModel.timeHorizon();
			Integer lookback = resolveLookback(inferenceModel.lookback());
			String key = normalizeVariableKey(variableName);
			settings.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new InferenceModelSettings(timeHorizon, lookback));
		}
		settings.replaceAll((key, value) -> List.copyOf(value));
		return settings;
	}

	private Integer resolveLookback(io.quassar.monentia.picota.DigitalTwin.DigitalSubject.InferenceModel.Lookback lookback) {
		if (lookback == null) return null;
		try {
			if (lookback.isWindow() && lookback.asWindow() != null && lookback.asWindow().size() > 0) {
				return lookback.asWindow().size();
			}
			if (lookback.isDistance() && lookback.asDistance() != null && lookback.asDistance().max() > 0) {
				return lookback.asDistance().max();
			}
		} catch (RuntimeException ignored) {
			return null;
		}
		return null;
	}

	private TimeBucket resolveTimeBucket(io.quassar.monentia.picota.DigitalTwin.DigitalSubject parsedSubject) {
		io.quassar.monentia.picota.DigitalTwin.DigitalSubject.Resolution resolution = parsedSubject.resolution();
		if (resolution == null || resolution.scale() == null) return null;
		try {
			return TimeBucket.fromDslScaleName(resolution.scale().name());
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private List<Variable> mergeVariables(
			List<io.quassar.monentia.picota.Variable> globalVariables,
			List<io.quassar.monentia.picota.Variable> subjectVariables,
			Map<String, List<InferenceModelSettings>> inferredByVariableName
	) {
		Map<String, io.quassar.monentia.picota.Variable> byName = new LinkedHashMap<>();
		appendVariables(byName, globalVariables);
		appendVariables(byName, subjectVariables);
		List<Variable> projected = new ArrayList<>();
		Set<String> usedVariableIds = new HashSet<>();
		for (io.quassar.monentia.picota.Variable variable : byName.values()) {
			if (variable == null) continue;
			Variable sensor = toSensorVariable(variable);
			projected.add(sensor);
			usedVariableIds.add(normalizeVariableKey(sensor.id()));
			List<InferenceModelSettings> inferredSettings = inferredByVariableName.get(normalizeVariableKey(variable.name$()));
			if (inferredSettings == null || inferredSettings.isEmpty()) continue;
			for (InferenceModelSettings settings : inferredSettings) {
				Variable inferred = toInferredVariable(variable, settings, usedVariableIds);
				projected.add(inferred);
				usedVariableIds.add(normalizeVariableKey(inferred.id()));
			}
		}
		return List.copyOf(projected);
	}

	private void appendVariables(Map<String, io.quassar.monentia.picota.Variable> byName, List<io.quassar.monentia.picota.Variable> variables) {
		if (variables == null) return;
		for (io.quassar.monentia.picota.Variable variable : variables) {
			if (variable == null || variable.name$() == null || variable.name$().isBlank()) continue;
			byName.put(variable.name$(), variable);
		}
	}

	private Variable toSensorVariable(io.quassar.monentia.picota.Variable variable) {
		return toVariable(variable, VariableType.SENSOR, null, variable.name$());
	}

	private Variable toInferredVariable(
			io.quassar.monentia.picota.Variable variable,
			InferenceModelSettings inferredSettings,
			Set<String> usedIds
	) {
		String baseName = blankToNull(variable.name$());
		String baseId = inferredVariableId(baseName, inferredSettings == null ? null : inferredSettings.timeHorizon());
		String uniqueId = disambiguateId(baseId, usedIds);
		return toVariable(variable, VariableType.INFERRED, inferredSettings, uniqueId);
	}

	private Variable toVariable(
			io.quassar.monentia.picota.Variable variable,
			VariableType variableType,
			InferenceModelSettings inferredSettings,
			String variableId
	) {
		String name = variable.name$();
		String description = firstNonBlank(
				variable.label(),
				name
		);
		String unit = variable.isNumeric() ? blankToNull(variable.asNumeric().unit()) : null;
		VariableDataType dataType = variable.isNumeric() ? VariableDataType.NUMERIC : VariableDataType.CATEGORICAL;
		return new Variable(
				variableId,
				name,
				description,
				unit,
				dataType,
				variableType,
				inferredSettings == null ? null : inferredSettings.timeHorizon(),
				inferredSettings == null ? null : inferredSettings.lookback()
		);
	}

	private String inferredVariableId(String baseName, Integer timeHorizon) {
		String root = baseName == null ? "inferred" : baseName.trim();
		if (root.isEmpty()) root = "inferred";
		if (timeHorizon == null || timeHorizon <= 0) return root + "__inferred";
		return root + "__t_plus_" + timeHorizon;
	}

	private String disambiguateId(String candidate, Set<String> usedIds) {
		String base = blankToNull(candidate);
		if (base == null) base = "inferred";
		String normalized = normalizeVariableKey(base);
		if (usedIds == null || !usedIds.contains(normalized)) return base;
		int suffix = 2;
		String next = base + "__" + suffix;
		while (usedIds.contains(normalizeVariableKey(next))) {
			suffix++;
			next = base + "__" + suffix;
		}
		return next;
	}

	private String normalizeVariableKey(String value) {
		String candidate = blankToNull(value);
		return candidate == null ? "" : candidate.trim().toLowerCase(Locale.ROOT);
	}

	private String resolveSubjectName(io.quassar.monentia.picota.DigitalTwin.DigitalSubject parsedSubject) {
		return firstNonBlank(
				parsedSubject.subject().label(),
				parsedSubject.subject().name$(),
				parsedSubject.name$(),
				parsedSubject.core$() == null ? null : parsedSubject.core$().name()
		);
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			String candidate = blankToNull(value);
			if (candidate != null) return candidate;
		}
		return null;
	}

	private String blankToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String subjectIdFromName(String subjectName, Set<String> usedIds) {
		String base = subjectName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (base.isBlank()) base = "subject";
		String candidate = "subject_" + base;
		int suffix = 2;
		while (usedIds.contains(candidate)) {
			candidate = "subject_" + base + "_" + suffix++;
		}
		usedIds.add(candidate);
		return candidate;
	}

	private List<SubjectDataset> retainDatasetsForSubjects(List<SubjectDataset> datasets, List<DigitalSubject> subjects) {
		List<SubjectDataset> safeDatasets = datasets == null ? List.of() : List.copyOf(datasets);
		if (safeDatasets.isEmpty()) return safeDatasets;
		Set<String> validSubjectIds = subjects == null
				? Set.of()
				: subjects.stream()
				.filter(Objects::nonNull)
				.map(DigitalSubject::id)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (validSubjectIds.isEmpty()) return List.of();
		return safeDatasets.stream()
				.filter(Objects::nonNull)
				.filter(dataset -> dataset.subjectId() != null && validSubjectIds.contains(dataset.subjectId()))
				.toList();
	}

	public record ModelProjection(List<DigitalSubject> subjects, List<SubjectDataset> datasets) {
		public ModelProjection {
			subjects = subjects == null ? List.of() : List.copyOf(subjects);
			datasets = datasets == null ? List.of() : List.copyOf(datasets);
		}
	}

	private record InferenceModelSettings(Integer timeHorizon, Integer lookback) {
	}
}
