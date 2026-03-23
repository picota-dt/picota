package io.picota.backend.control.ui;

public record InferredVariableResult(
		String name,
		Double accuracy,
		Double mae,
		Double violations
) {
}
