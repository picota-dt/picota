package io.picota.backend.model;

public record VariableStat(
		Integer count,
		Double mean,
		Double std,
		Double min,
		Double max,
		Double median
) {
}
