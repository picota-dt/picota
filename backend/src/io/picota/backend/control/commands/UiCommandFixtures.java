package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.*;

import java.time.Instant;
import java.util.*;

public final class UiCommandFixtures {
	public static final String DEFAULT_EMAIL = "alex.laurent@acme.io";
	public static final String DEFAULT_PASSWORD = "password123";

	private UiCommandFixtures() {
	}

	public static User demoUser() {
		return new User(
				"usr_demo_001",
				"Alex Laurent",
				DEFAULT_EMAIL,
				"Engineer",
				"Acme Industries",
				"AL",
				1_248,
				"January 2024"
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
										new Variable("v1", "pressure_in", "bar", 3.2, VariableType.SENSOR),
										new Variable("v2", "pressure_out", "bar", 5.8, VariableType.SENSOR),
										new Variable("v3", "flow_rate", "m³/h", 24.5, VariableType.INFERRED)
								)
						),
						new DigitalSubject(
								"motor",
								"Electric Motor",
								List.of(
										new Variable("v4", "temperature", "°C", 62.1, VariableType.SENSOR),
										new Variable("v5", "rpm", "rpm", 1480.0, VariableType.SENSOR),
										new Variable("v6", "vibration", "mm/s", 3.7, VariableType.INFERRED)
								)
						)
				),
				new InferenceEngine(
						true,
						TrainingAlgorithm.LSTM,
						Instant.parse("2024-11-10T14:32:00Z"),
						200,
						0.001,
						60,
						32,
						List.of(
								new InferredVariableResult("flow_rate", 94.2, 0.38, 1.2),
								new InferredVariableResult("vibration", 91.7, 0.21, 2.8)
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
				)
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
				List.of()
		));
		return twins;
	}

	public static User copyUser(User user) {
		return new User(
				user.id(),
				user.name(),
				user.email(),
				user.role(),
				user.organization(),
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
				twin.datasets() == null ? List.of() : twin.datasets().stream().map(UiCommandFixtures::copyDataset).toList()
		);
	}

	public static InferenceEngine copyInferenceEngine(InferenceEngine engine) {
		return new InferenceEngine(
				engine.trained(),
				engine.algorithm(),
				engine.trainedAt(),
				engine.epochs(),
				engine.learningRate(),
				engine.windowSize(),
				engine.batchSize(),
				engine.inferredVariables() == null ? List.of() : engine.inferredVariables().stream()
						.map(v -> new InferredVariableResult(v.name(), v.accuracy(), v.mae(), v.violations()))
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
				subject.variables() == null ? List.of() : subject.variables().stream().map(UiCommandFixtures::copyVariable).toList()
		);
	}

	public static Variable copyVariable(Variable variable) {
		return new Variable(variable.id(), variable.name(), variable.unit(), variable.value(), variable.variableType());
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
			double base = variable.value() == null ? 0.0 : variable.value();
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
}
