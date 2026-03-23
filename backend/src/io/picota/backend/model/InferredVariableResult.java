package io.picota.backend.model;

public record InferredVariableResult(
		String name,
		Double accuracy,
		Double mae,
		Double violations
) {
}
