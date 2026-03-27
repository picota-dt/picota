package io.picota.backend.control.ui.viewmodel;

import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.model.UserAccount;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ModelViewMapper {
	private ModelViewMapper() {
	}

	public static User toViewUser(UserAccount account) {
		if (account == null) return null;
		return new User(
				account.id(),
				account.name(),
				account.email(),
				account.avatarInitials(),
				account.credits(),
				account.joinedAt() == null ? null : account.joinedAt().toString()
		);
	}

	public static UserAccount toDomainUserAccount(User user, String googleSubject) {
		if (user == null) return null;
		return new UserAccount(
				user.id(),
				user.name(),
				user.email(),
				googleSubject == null ? "" : googleSubject,
				user.avatarInitials(),
				user.credits() == null ? 0 : user.credits(),
				parseInstant(user.joinedAt())
		);
	}

	public static DigitalTwin toViewTwin(io.picota.backend.model.DigitalTwin model) {
		if (model == null) return null;
		return new DigitalTwin(
				model.id(),
				model.name(),
				model.description(),
				model.version(),
				model.image(),
				toViewTwinType(model.type()),
				toViewTwinStatus(model.status()),
				model.updatedAt(),
				model.creditsUsed(),
				model.model(),
				mapList(model.subjects(), ModelViewMapper::toViewSubject),
				toViewInferenceEngine(model.inferenceEngine()),
				mapList(model.datasets(), ModelViewMapper::toViewDataset),
				model.ingestionToken()
		);
	}

	public static io.picota.backend.model.DigitalTwin toDomainTwin(DigitalTwin view) {
		if (view == null) return null;
		return new io.picota.backend.model.DigitalTwin(
				view.id(),
				view.name(),
				view.description(),
				view.version(),
				view.image(),
				toDomainTwinType(view.type()),
				toDomainTwinStatus(view.status()),
				view.updatedAt(),
				view.creditsUsed(),
				view.model(),
				mapList(view.subjects(), ModelViewMapper::toDomainSubject),
				toDomainInferenceEngine(view.inferenceEngine()),
				mapList(view.datasets(), ModelViewMapper::toDomainDataset),
				view.ingestionToken()
		);
	}

	public static TrainingJob toViewTrainingJob(io.picota.backend.model.TrainingJob model) {
		if (model == null) return null;
		return new TrainingJob(
				model.jobId(),
				model.twinId(),
				toViewTrainingJobStatus(model.status()),
				model.progress(),
				model.currentPhase(),
				model.createdAt(),
				model.startedAt(),
				model.completedAt(),
				model.errorMessage(),
				toViewInferenceEngine(model.result())
		);
	}

	public static io.picota.backend.model.TrainingJob toDomainTrainingJob(TrainingJob view) {
		if (view == null) return null;
		return new io.picota.backend.model.TrainingJob(
				view.jobId(),
				view.twinId(),
				toDomainTrainingJobStatus(view.status()),
				view.progress(),
				view.currentPhase(),
				view.createdAt(),
				view.startedAt(),
				view.completedAt(),
				view.errorMessage(),
				toDomainInferenceEngine(view.result())
		);
	}

	private static DigitalSubject toViewSubject(io.picota.backend.model.DigitalSubject model) {
		if (model == null) return null;
		return new DigitalSubject(
				model.id(),
				model.name(),
				toViewTimeBucket(model.timeBucket()),
				mapList(model.variables(), ModelViewMapper::toViewVariable)
		);
	}

	private static io.picota.backend.model.DigitalSubject toDomainSubject(DigitalSubject view) {
		if (view == null) return null;
		return new io.picota.backend.model.DigitalSubject(
				view.id(),
				view.name(),
				toDomainTimeBucket(view.timeBucket()),
				mapList(view.variables(), ModelViewMapper::toDomainVariable)
		);
	}

	private static Variable toViewVariable(io.picota.backend.model.Variable model) {
		if (model == null) return null;
		return new Variable(
				model.id(),
				model.name(),
				model.description(),
				model.unit(),
				toViewVariableDataType(model.dataType()),
				toViewVariableType(model.variableType()),
				model.timeHorizon(),
				model.lookback()
		);
	}

	private static io.picota.backend.model.Variable toDomainVariable(Variable view) {
		if (view == null) return null;
		return new io.picota.backend.model.Variable(
				view.id(),
				view.name(),
				view.description(),
				view.unit(),
				toDomainVariableDataType(view.dataType()),
				toDomainVariableType(view.variableType()),
				view.timeHorizon(),
				view.lookback()
		);
	}

	private static SubjectDataset toViewDataset(io.picota.backend.model.SubjectDataset model) {
		if (model == null) return null;
		Map<String, VariableStat> stats = new LinkedHashMap<>();
		if (model.stats() != null) {
			model.stats().forEach((key, value) -> stats.put(key, toViewVariableStat(value)));
		}
		return new SubjectDataset(
				model.subjectId(),
				model.fileName(),
				model.uploadedRecords(),
				model.realtimeRecords(),
				model.uploadedAt(),
				stats
		);
	}

	private static io.picota.backend.model.SubjectDataset toDomainDataset(SubjectDataset view) {
		if (view == null) return null;
		Map<String, io.picota.backend.model.VariableStat> stats = new LinkedHashMap<>();
		if (view.stats() != null) {
			view.stats().forEach((key, value) -> stats.put(key, toDomainVariableStat(value)));
		}
		return new io.picota.backend.model.SubjectDataset(
				view.subjectId(),
				view.fileName(),
				view.uploadedRecords(),
				view.realtimeRecords(),
				view.uploadedAt(),
				stats
		);
	}

	private static VariableStat toViewVariableStat(io.picota.backend.model.VariableStat model) {
		if (model == null) return null;
		return new VariableStat(model.count(), model.mean(), model.std(), model.min(), model.max(), model.median());
	}

	private static io.picota.backend.model.VariableStat toDomainVariableStat(VariableStat view) {
		if (view == null) return null;
		return new io.picota.backend.model.VariableStat(view.count(), view.mean(), view.std(), view.min(), view.max(), view.median());
	}

	private static InferenceEngine toViewInferenceEngine(io.picota.backend.model.InferenceEngine model) {
		if (model == null) return null;
		return new InferenceEngine(
				model.trained(),
				toViewTrainingAlgorithm(model.algorithm()),
				model.trainedAt(),
				model.launchedAt(),
				model.trainingDurationSeconds(),
				model.epochs(),
				model.learningRate(),
				model.windowSize(),
				model.batchSize(),
				mapList(model.inferredVariables(), ModelViewMapper::toViewInferredVariableResult),
				toViewRetrainingConfig(model.retrainingConfig())
		);
	}

	private static io.picota.backend.model.InferenceEngine toDomainInferenceEngine(InferenceEngine view) {
		if (view == null) return null;
		return new io.picota.backend.model.InferenceEngine(
				view.trained(),
				toDomainTrainingAlgorithm(view.algorithm()),
				view.trainedAt(),
				view.launchedAt(),
				view.trainingDurationSeconds(),
				view.epochs(),
				view.learningRate(),
				view.windowSize(),
				view.batchSize(),
				mapList(view.inferredVariables(), ModelViewMapper::toDomainInferredVariableResult),
				toDomainRetrainingConfig(view.retrainingConfig())
		);
	}

	private static InferredVariableResult toViewInferredVariableResult(io.picota.backend.model.InferredVariableResult model) {
		if (model == null) return null;
		return new InferredVariableResult(
				model.name(),
				model.mae(),
				model.r2(),
				model.validationSampleCount(),
				model.validationDurationSeconds(),
				toViewVariableDataType(model.dataType()),
				model.accuracy(),
				model.macroF1(),
				model.violations(),
				model.constraintViolations()
		);
	}

	private static io.picota.backend.model.InferredVariableResult toDomainInferredVariableResult(InferredVariableResult view) {
		if (view == null) return null;
		return new io.picota.backend.model.InferredVariableResult(
				view.name(),
				view.mae(),
				view.r2(),
				view.validationSampleCount(),
				view.validationDurationSeconds(),
				toDomainVariableDataType(view.dataType()),
				view.accuracy(),
				view.macroF1(),
				view.violations(),
				view.constraintViolations()
		);
	}

	private static RetrainingConfig toViewRetrainingConfig(io.picota.backend.model.RetrainingConfig model) {
		if (model == null) return null;
		return new RetrainingConfig(
				model.enabled(),
				toViewRetrainingSchedule(model.schedule()),
				model.minNewRecords(),
				model.timeOfDay()
		);
	}

	private static io.picota.backend.model.RetrainingConfig toDomainRetrainingConfig(RetrainingConfig view) {
		if (view == null) return null;
		return new io.picota.backend.model.RetrainingConfig(
				view.enabled(),
				toDomainRetrainingSchedule(view.schedule()),
				view.minNewRecords(),
				view.timeOfDay()
		);
	}

	private static TwinType toViewTwinType(io.picota.backend.model.TwinType model) {
		return model == null ? null : TwinType.valueOf(model.name());
	}

	private static io.picota.backend.model.TwinType toDomainTwinType(TwinType view) {
		return view == null ? null : io.picota.backend.model.TwinType.valueOf(view.name());
	}

	private static TwinStatus toViewTwinStatus(io.picota.backend.model.TwinStatus model) {
		return model == null ? null : TwinStatus.valueOf(model.name());
	}

	private static io.picota.backend.model.TwinStatus toDomainTwinStatus(TwinStatus view) {
		return view == null ? null : io.picota.backend.model.TwinStatus.valueOf(view.name());
	}

	private static VariableType toViewVariableType(io.picota.backend.model.VariableType model) {
		return model == null ? null : VariableType.valueOf(model.name());
	}

	private static io.picota.backend.model.VariableType toDomainVariableType(VariableType view) {
		return view == null ? null : io.picota.backend.model.VariableType.valueOf(view.name());
	}

	private static VariableDataType toViewVariableDataType(io.picota.backend.model.VariableDataType model) {
		return model == null ? null : VariableDataType.valueOf(model.name());
	}

	private static io.picota.backend.model.VariableDataType toDomainVariableDataType(VariableDataType view) {
		return view == null ? null : io.picota.backend.model.VariableDataType.valueOf(view.name());
	}

	private static TimeBucket toViewTimeBucket(io.picota.backend.model.TimeBucket model) {
		return model == null ? null : TimeBucket.valueOf(model.name());
	}

	private static io.picota.backend.model.TimeBucket toDomainTimeBucket(TimeBucket view) {
		return view == null ? null : io.picota.backend.model.TimeBucket.valueOf(view.name());
	}

	private static TrainingAlgorithm toViewTrainingAlgorithm(io.picota.backend.model.TrainingAlgorithm model) {
		return model == null ? null : TrainingAlgorithm.valueOf(model.name());
	}

	private static io.picota.backend.model.TrainingAlgorithm toDomainTrainingAlgorithm(TrainingAlgorithm view) {
		return view == null ? null : io.picota.backend.model.TrainingAlgorithm.valueOf(view.name());
	}

	private static RetrainingSchedule toViewRetrainingSchedule(io.picota.backend.model.RetrainingSchedule model) {
		return model == null ? null : RetrainingSchedule.valueOf(model.name());
	}

	private static io.picota.backend.model.RetrainingSchedule toDomainRetrainingSchedule(RetrainingSchedule view) {
		return view == null ? null : io.picota.backend.model.RetrainingSchedule.valueOf(view.name());
	}

	private static TrainingJobStatus toViewTrainingJobStatus(io.picota.backend.model.TrainingJobStatus model) {
		return model == null ? null : TrainingJobStatus.valueOf(model.name());
	}

	private static io.picota.backend.model.TrainingJobStatus toDomainTrainingJobStatus(TrainingJobStatus view) {
		return view == null ? null : io.picota.backend.model.TrainingJobStatus.valueOf(view.name());
	}

	private static <S, T> List<T> mapList(List<S> source, Function<S, T> mapper) {
		if (source == null || source.isEmpty()) return List.of();
		return source.stream().map(mapper).toList();
	}

	private static Instant parseInstant(String value) {
		if (value == null || value.isBlank()) return Instant.now();
		try {
			return Instant.parse(value);
		} catch (Exception ignored) {
			return Instant.now();
		}
	}
}
