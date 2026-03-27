package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.*;

import java.time.Instant;
import java.util.*;

public final class UiCommandFixtures {
	public static final String DEFAULT_EMAIL = "alex.laurent@acme.io";
	public static final String DEFAULT_GOOGLE_SUBJECT = "google_demo_subject";

	private UiCommandFixtures() {
	}

	public static User demoUser() {
		return new User(
				"usr_demo_001",
				"Alex Laurent",
				DEFAULT_EMAIL,
				"AL",
				1_248,
				"2024-01-15T00:00:00Z"
		);
	}

	public static List<DigitalTwin> demoTwins() {
		List<DigitalTwin> twins = new ArrayList<>();
		twins.add(new DigitalTwin(
				"twin_8f4a1b2c",
				"Pump Station Alpha",
				"Digital replica of the main water pumping station.",
				"1.0.0",
				"https://images.unsplash.com/photo-1649829725145-d93731589a6e?auto=format&fit=crop&w=1080&q=80",
				TwinType.INFRASTRUCTURE,
				TwinStatus.ACTIVE,
				"2 hours ago",
				142,
				"# Pump Station Alpha\nsubjects:\n  - id: pump_main\n    name: Main Pump\n",
				List.of(
						new DigitalSubject(
								"pump_main",
								"Main Pump",
								List.of(
										new Variable("v1", "pressure_in", "Pump inlet pressure", "bar", VariableDataType.NUMERIC, VariableType.SENSOR),
										new Variable("v2", "pressure_out", "Pump outlet pressure", "bar", VariableDataType.NUMERIC, VariableType.SENSOR),
										new Variable("v3", "flow_rate", "Estimated flow rate", "m³/h", VariableDataType.NUMERIC, VariableType.INFERRED)
								)
						),
						new DigitalSubject(
								"motor",
								"Electric Motor",
								List.of(
										new Variable("v4", "temperature", "Motor temperature", "°C", VariableDataType.NUMERIC, VariableType.SENSOR),
										new Variable("v5", "rpm", "Motor speed", "rpm", VariableDataType.NUMERIC, VariableType.SENSOR),
										new Variable("v6", "vibration", "Estimated vibration level", "mm/s", VariableDataType.NUMERIC, VariableType.INFERRED)
								)
						)
				),
				new InferenceEngine(
						true,
						TrainingAlgorithm.KAN,
						Instant.parse("2024-11-10T14:32:00Z"),
						Instant.parse("2024-11-10T14:00:00Z"),
						1920.0,
						200,
						0.001,
						60,
						32,
						List.of(
								new InferredVariableResult("flow_rate", 0.38, 0.942, 320, 1.8, VariableDataType.NUMERIC, null, null, 1.2, Map.of()),
								new InferredVariableResult("vibration", 0.21, 0.917, 320, 1.8, VariableDataType.NUMERIC, null, null, 2.8, Map.of())
						),
						new RetrainingConfig(true, RetrainingSchedule.WEEKLY, 500, "02:00")
				),
				List.of(
						new SubjectDataset(
								"pump_main",
								"pump_main_data.csv",
								1500,
								120,
								Instant.parse("2024-11-09T08:00:00Z"),
								Map.of(
										"pressure_in", new VariableStat(1500, 3.2, 0.1, 3.0, 3.5, 3.2),
										"pressure_out", new VariableStat(1500, 5.8, 0.2, 5.5, 6.0, 5.8),
										"flow_rate", new VariableStat(1500, 24.5, 1.0, 23.0, 26.0, 24.5)
								)
						)
				),
				"itok_demo_8f4a1b2c"
		));

		twins.add(new DigitalTwin(
				"twin_2d3e4f5g",
				"HQ Building — HVAC",
				"Digital twin of the headquarters HVAC system.",
				"0.9.0",
				"https://images.unsplash.com/photo-1721244654392-9c912a6eb236?auto=format&fit=crop&w=1080&q=80",
				TwinType.BUILDING,
				TwinStatus.DRAFT,
				"1 week ago",
				0,
				"# HQ Building HVAC\nsubjects: []\n",
				List.of(),
				null,
				List.of(),
				"itok_demo_2d3e4f5g"
		));
		return twins;
	}

	public static User copyUser(User user) {
		return new User(
				user.id(),
				user.name(),
				user.email(),
				user.avatarInitials(),
				user.credits(),
				user.joinedAt()
		);
	}

	public static DigitalTwin copyTwin(DigitalTwin twin) {
		return new DigitalTwin(
				twin.id(),
				twin.name(),
				twin.description(),
				twin.version(),
				twin.image(),
				twin.type(),
				twin.status(),
				twin.updatedAt(),
				twin.creditsUsed(),
				twin.model(),
				twin.subjects() == null ? List.of() : twin.subjects().stream().map(UiCommandFixtures::copySubject).toList(),
				twin.inferenceEngine() == null ? null : copyInferenceEngine(twin.inferenceEngine()),
				twin.datasets() == null ? List.of() : twin.datasets().stream().map(UiCommandFixtures::copyDataset).toList(),
				twin.ingestionToken()
		);
	}

	public static InferenceEngine copyInferenceEngine(InferenceEngine engine) {
		return new InferenceEngine(
				engine.trained(),
				engine.algorithm(),
				engine.trainedAt(),
				engine.launchedAt(),
				engine.trainingDurationSeconds(),
				engine.epochs(),
				engine.learningRate(),
				engine.windowSize(),
				engine.batchSize(),
				engine.inferredVariables() == null ? List.of() : engine.inferredVariables().stream()
						.map(v -> new InferredVariableResult(
								v.name(),
								v.mae(),
								v.r2(),
								v.validationSampleCount(),
								v.validationDurationSeconds(),
								v.dataType(),
								v.accuracy(),
								v.macroF1(),
								v.violations(),
								v.constraintViolations()
						))
						.toList(),
				engine.retrainingConfig() == null
						? null
						: new RetrainingConfig(
						engine.retrainingConfig().enabled(),
						engine.retrainingConfig().schedule(),
						engine.retrainingConfig().minNewRecords(),
						engine.retrainingConfig().timeOfDay()
				)
		);
	}

	public static DigitalSubject copySubject(DigitalSubject subject) {
		return new DigitalSubject(
				subject.id(),
				subject.name(),
				subject.timeBucket(),
				subject.variables() == null ? List.of() : subject.variables().stream().map(UiCommandFixtures::copyVariable).toList()
		);
	}

	public static Variable copyVariable(Variable variable) {
		return new Variable(
				variable.id(),
				variable.name(),
				variable.description(),
				variable.unit(),
				variable.dataType(),
				variable.variableType(),
				variable.timeHorizon(),
				variable.lookback()
		);
	}

	public static SubjectDataset copyDataset(SubjectDataset dataset) {
		Map<String, VariableStat> copiedStats = new LinkedHashMap<>();
		if (dataset.stats() != null) {
			dataset.stats().forEach((k, v) -> copiedStats.put(k, new VariableStat(v.count(), v.mean(), v.std(), v.min(), v.max(), v.median())));
		}
		return new SubjectDataset(
				dataset.subjectId(),
				dataset.fileName(),
				dataset.uploadedRecords(),
				dataset.realtimeRecords(),
				dataset.uploadedAt(),
				copiedStats
		);
	}

	public static List<VariableTelemetry> telemetryForSubject(DigitalSubject subject, int points, Random random) {
		List<VariableTelemetry> telemetry = new ArrayList<>();
		int safePoints = Math.max(1, Math.min(points, 100));
		for (Variable variable : subject.variables()) {
			List<TelemetryPoint> history = new ArrayList<>();
			double base = telemetryBase(variable);
			for (int i = safePoints - 1; i >= 0; i--) {
				double jitter = base + (base * 0.03 * (random.nextDouble() - 0.5));
				history.add(new TelemetryPoint(Instant.now().minusSeconds(i * 3L), round3(jitter)));
			}
			double current = history.get(history.size() - 1).value();
			telemetry.add(new VariableTelemetry(variable.id(), variable.name(), variable.unit(), current, history));
		}
		return telemetry;
	}

	private static double round3(double value) {
		return Math.round(value * 1000.0) / 1000.0;
	}

	private static double telemetryBase(Variable variable) {
		int hash = Math.abs(Objects.hash(variable.id(), variable.name(), variable.unit(), variable.variableType()));
		double base = 5.0 + (hash % 9_500) / 100.0;
		return variable.dataType() == VariableDataType.CATEGORICAL ? Math.rint(base % 2.0) : base;
	}
}
