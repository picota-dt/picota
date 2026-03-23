package io.picota.backend.control.ui.schemas;

public record InferredVariableResult(
		String name,
		Double accuracy,
		Double mae,
		Double violations
) {
}
