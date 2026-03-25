package io.picota.backend.control.ui.schemas;

import java.util.List;

public record DigitalTwin(
		String id,
		String name,
		String description,
		String version,
		String image,
		TwinType type,
		TwinStatus status,
		String updatedAt,
		Integer creditsUsed,
		String model,
		List<DigitalSubject> subjects,
		InferenceEngine inferenceEngine,
		List<SubjectDataset> datasets,
		String ingestionToken
) {
	public DigitalTwin {
		subjects = subjects == null ? List.of() : List.copyOf(subjects);
		datasets = datasets == null ? List.of() : List.copyOf(datasets);
	}
}
